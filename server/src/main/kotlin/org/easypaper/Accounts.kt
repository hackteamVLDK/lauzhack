package org.easypaper

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.account.Account
import org.easypaper.account.AccountException
import org.easypaper.account.AccountException.Kind
import org.easypaper.account.AccountSession
import org.easypaper.account.EmailChallenge
import org.easypaper.account.InvitedPerson
import org.easypaper.model.EasyPaperJson
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.Properties

private const val CODE_TTL = 10 * 60_000L
private const val INVITE_TTL = 14 * 24 * 60 * 60_000L
private const val CLINIC_HEADER = "X-EasyPaper-Clinic-Token"
private const val CODE_SUBJECT = "Your Easy Paper code"

fun interface MailSender {
    suspend fun send(to: String, code: String)
}

private fun codeText(code: String) = "Your code: $code. It is valid for 10 minutes. If you did not ask for it, ignore this email."

class ResendMail(private val http: HttpClient, private val apiKey: String, private val from: String) : MailSender {
    override suspend fun send(to: String, code: String) {
        val body = JsonObject(mapOf(
            "from" to JsonPrimitive(from),
            "to" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(to))),
            "subject" to JsonPrimitive(CODE_SUBJECT),
            "text" to JsonPrimitive(codeText(code)),
        ))
        val response = http.post("https://api.resend.com/emails") {
            expectSuccess = false
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (response.status.value !in 200..299) fail(Kind.MailUnavailable)
    }
}

/** Port 465 uses implicit TLS; other ports require STARTTLS before authentication. Never enables mail debug logs. */
class SmtpMail(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
    private val from: String,
) : MailSender {
    override suspend fun send(to: String, code: String) = withContext(Dispatchers.IO) {
        val properties = Properties().apply {
            setProperty("mail.smtp.host", host)
            setProperty("mail.smtp.port", port.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
            if (port == 465) setProperty("mail.smtp.ssl.enable", "true") else {
                setProperty("mail.smtp.starttls.enable", "true")
                setProperty("mail.smtp.starttls.required", "true")
            }
        }
        val session = Session.getInstance(properties)
        val sender = from // inside MimeMessage.apply, 'from' would be the message's own From header
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(sender, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(to, true))
            setSubject(CODE_SUBJECT, "UTF-8")
            setText(codeText(code), "UTF-8")
        }
        session.getTransport("smtp").use { transport ->
            transport.connect(host, port, user, password)
            transport.sendMessage(message, message.allRecipients)
        }
    }
}

/** Only claims returned by Google's HTTPS tokeninfo endpoint are accepted. Validation also runs for test verifiers. */
data class GoogleIdentity(
    val audience: String,
    val issuer: String,
    val emailVerified: Boolean,
    val expiresAtSeconds: Long,
    val email: String,
    val name: String? = null,
)

fun interface GoogleVerifier {
    suspend fun verify(idToken: String): GoogleIdentity
}

class GoogleTokenInfo(private val http: HttpClient) : GoogleVerifier {
    override suspend fun verify(idToken: String): GoogleIdentity {
        val response = http.post("https://oauth2.googleapis.com/tokeninfo") {
            expectSuccess = false
            parameter("id_token", idToken)
        }
        if (response.status.value in 400..499) fail(Kind.Unauthorized)
        if (response.status != HttpStatusCode.OK) fail(Kind.Network)
        val json = EasyPaperJson.parseToJsonElement(response.bodyAsText()).jsonObject
        fun field(key: String) = json[key]?.jsonPrimitive?.contentOrNull ?: fail(Kind.Unauthorized)
        return GoogleIdentity(
            audience = field("aud"), issuer = field("iss"), emailVerified = field("email_verified") == "true",
            expiresAtSeconds = field("exp").toLongOrNull() ?: fail(Kind.Unauthorized),
            email = field("email"), name = json["name"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

fun interface GoogleCodeExchange {
    suspend fun exchange(code: String, codeVerifier: String, redirectUri: String): String
}

/** Credentials stay on the server. Upstream bodies and exceptions are never exposed to callers. */
class GoogleTokenExchange(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
) : GoogleCodeExchange {
    override suspend fun exchange(code: String, codeVerifier: String, redirectUri: String): String {
        try {
            val response = http.submitForm("https://oauth2.googleapis.com/token", parameters {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("code", code)
                append("code_verifier", codeVerifier)
                append("redirect_uri", redirectUri)
            }) { expectSuccess = false }
            if (response.status.value in 400..499) fail(Kind.Unauthorized)
            if (response.status != HttpStatusCode.OK) fail(Kind.Network)
            return EasyPaperJson.parseToJsonElement(response.bodyAsText()).jsonObject["id_token"]
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it.length <= 12_000 }
                ?: fail(Kind.Network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountException) {
            fail(e.kind)
        } catch (_: Exception) {
            fail(Kind.Network)
        }
    }
}

@Serializable
private data class StoredInvite(val name: String, val phone: String?, val email: String, val expiresAt: Long)

@Serializable
private data class AccountState(
    val accounts: Map<String, Account> = emptyMap(),
    // SHA-256(session token) -> account id; raw session and invitation tokens never reach disk.
    val sessions: Map<String, String> = emptyMap(),
    val invites: Map<String, StoredInvite> = emptyMap(),
)

/** One instance per process/data directory. The mutex serializes updates and atomic file replacement together. */
class Accounts(
    private val file: Path? = null,
    private val mail: MailSender? = null,
    private val googleVerifier: GoogleVerifier? = null,
    private val googleClientIds: Set<String> = emptySet(),
    private val clinicToken: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val googleDesktopClientId: String? = null,
    private val googleCodeExchange: GoogleCodeExchange? = null,
) {
    private val allowedGoogleClientIds = googleClientIds + listOfNotNull(googleDesktopClientId?.takeIf { it.isNotBlank() })
    private data class Challenge(
        val email: String, val digest: String, val expiresAt: Long, val inviteHash: String?, var attempts: Int = 0,
    )
    private val mutex = Mutex()
    private val random = SecureRandom()
    private val challenges = mutableMapOf<String, Challenge>()
    private val windows = mutableMapOf<String, ArrayDeque<Long>>()
    private var state = try {
        if (file != null && Files.exists(file)) EasyPaperJson.decodeFromString<AccountState>(Files.readString(file)) else AccountState()
    } catch (_: Exception) {
        // Refuse to overwrite bad data, and keep serialization errors containing personal data out of startup logs.
        throw IllegalStateException("Cannot read account database")
    }

    /** All account routes have a bounded per-IP minute window; sending additionally allows five mails / ten minutes. */
    suspend fun limit(ip: String) = mutex.withLock { rate("requests:$ip", 60, 60_000L) }

    suspend fun startEmail(email: String, ip: String): EmailChallenge = sendChallenge(normalizeEmail(email), null, ip)

    private suspend fun sendChallenge(email: String, inviteHash: String?, ip: String): EmailChallenge {
        mutex.withLock {
            rate("mail:$ip", 5, CODE_TTL)
            pruneChallenges()
            if (challenges.size >= 10_000) fail(Kind.TooMany)
        }
        val sender = mail ?: fail(Kind.MailUnavailable)
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val id = secret()
        val challenge = Challenge(email, hash("$id:$code"), now() + CODE_TTL, inviteHash)
        // Do not hold the state lock over a network request. Unsent codes can never be verified.
        try {
            sender.send(email, code)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(Kind.MailUnavailable)
        }
        return mutex.withLock {
            if (inviteHash != null) liveInvite(inviteHash)
            if (now() >= challenge.expiresAt) fail(Kind.Expired)
            if (challenges.size >= 10_000) fail(Kind.TooMany)
            challenges[id] = challenge
            EmailChallenge(id, challenge.expiresAt)
        }
    }

    suspend fun verifyEmail(challengeId: String, code: String): AccountSession = mutex.withLock {
        val challenge = challenges[challengeId] ?: fail(Kind.NotFound)
        if (now() >= challenge.expiresAt) fail(Kind.Expired)
        if (challenge.attempts >= 5) fail(Kind.TooMany)
        challenge.attempts++
        if (!Regex("[0-9]{6}").matches(code) || !equalHash(challenge.digest, hash("$challengeId:$code"))) {
            fail(if (challenge.attempts >= 5) Kind.TooMany else Kind.BadCode)
        }
        val invite = challenge.inviteHash?.let { liveInvite(it) }
        val existing = state.accounts.values.find { it.email == challenge.email }
        val account = Account(
            id = existing?.id ?: secret(), email = challenge.email,
            name = invite?.name ?: existing?.name, phone = invite?.phone ?: existing?.phone,
            provider = if (invite != null) "invite" else existing?.provider ?: "email",
        )
        val token = secret()
        commit(state.copy(
            accounts = state.accounts + (account.id to account),
            sessions = state.sessions + (hash(token) to account.id),
            invites = if (challenge.inviteHash == null) state.invites else state.invites - challenge.inviteHash,
        ))
        challenges.remove(challengeId)
        if (challenge.inviteHash != null) challenges.entries.removeAll { it.value.inviteHash == challenge.inviteHash }
        AccountSession(token, account)
    }

    suspend fun google(idToken: String): AccountSession = googleSession(idToken)

    suspend fun googleCode(code: String, codeVerifier: String, redirectUri: String): AccountSession {
        val redirect = Regex("http://(?:127\\.0\\.0\\.1|localhost):([0-9]{1,5})").matchEntire(redirectUri)
        require(redirect != null && redirect.groupValues[1].toInt() in 1..65535)
        require(code.isNotBlank() && code.length <= 8_192 && Regex("[A-Za-z0-9._~-]{43,128}").matches(codeVerifier))
        val clientId = googleDesktopClientId?.takeIf { it.isNotBlank() } ?: fail(Kind.MailUnavailable)
        val exchange = googleCodeExchange ?: fail(Kind.MailUnavailable)
        val idToken = try {
            exchange.exchange(code, codeVerifier, redirectUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountException) {
            fail(e.kind)
        } catch (_: Exception) {
            fail(Kind.Network)
        }
        return googleSession(idToken, requiredAudience = clientId)
    }

    private suspend fun googleSession(idToken: String, requiredAudience: String? = null): AccountSession {
        if (idToken.isBlank() || idToken.length > 12_000 || allowedGoogleClientIds.isEmpty()) fail(Kind.Unauthorized)
        val verifier = googleVerifier ?: fail(Kind.Unauthorized)
        val identity = try {
            verifier.verify(idToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountException) {
            throw e
        } catch (_: Exception) {
            fail(Kind.Network)
        }
        if (identity.audience !in allowedGoogleClientIds || (requiredAudience != null && identity.audience != requiredAudience) ||
            identity.issuer !in setOf("accounts.google.com", "https://accounts.google.com") ||
            !identity.emailVerified || identity.expiresAtSeconds <= now() / 1000) fail(Kind.Unauthorized)
        val email = try { normalizeEmail(identity.email) } catch (_: IllegalArgumentException) { fail(Kind.Unauthorized) }
        return mutex.withLock {
            // A slow tokeninfo request must not allow an already expired token to create a session.
            if (identity.expiresAtSeconds <= now() / 1000) fail(Kind.Unauthorized)
            val existing = state.accounts.values.find { it.email == email }
            val account = Account(existing?.id ?: secret(), email, existing?.name ?: identity.name?.take(200), existing?.phone, "google")
            val token = secret()
            commit(state.copy(accounts = state.accounts + (account.id to account), sessions = state.sessions + (hash(token) to account.id)))
            AccountSession(token, account)
        }
    }

    fun authorizeClinic(supplied: String?) {
        if (clinicToken.isNullOrBlank() || supplied == null || !equalHash(hash(clinicToken), hash(supplied))) fail(Kind.Unauthorized)
    }

    suspend fun createInvite(name: String, phone: String?, email: String): String {
        val normalizedEmail = normalizeEmail(email)
        require(name.isNotBlank() && name.length <= 200 && (phone == null || phone.length <= 64))
        return mutex.withLock {
            val token = secret()
            val invites = state.invites.filterValues { it.expiresAt > now() }
            if (invites.size >= 10_000) fail(Kind.TooMany)
            commit(state.copy(invites = invites + (hash(token) to StoredInvite(name.trim(), phone?.trim()?.takeIf { it.isNotEmpty() }, normalizedEmail, now() + INVITE_TTL))))
            token
        }
    }

    suspend fun invitedPerson(token: String): InvitedPerson = mutex.withLock {
        val invite = liveInvite(hash(token))
        InvitedPerson(invite.name, invite.phone?.let(::maskPhone), maskEmail(invite.email))
    }

    suspend fun confirmInvite(token: String, ip: String): EmailChallenge {
        val inviteHash = hash(token)
        val email = mutex.withLock { liveInvite(inviteHash).email }
        return sendChallenge(email, inviteHash, ip)
    }

    suspend fun me(token: String?): Account = mutex.withLock { authenticated(token) }

    suspend fun signOut(token: String?) = mutex.withLock {
        authenticated(token)
        commit(state.copy(sessions = state.sessions - hash(token!!)))
    }

    private fun authenticated(token: String?): Account {
        val id = token?.let { state.sessions[hash(it)] } ?: fail(Kind.Unauthorized)
        return state.accounts[id] ?: fail(Kind.Unauthorized)
    }

    private fun liveInvite(inviteHash: String): StoredInvite {
        val invite = state.invites[inviteHash] ?: fail(Kind.NotFound)
        if (now() >= invite.expiresAt) fail(Kind.Expired)
        return invite
    }

    private fun pruneChallenges() {
        // Keep expired ids briefly so a late verification can distinguish Expired from NotFound.
        challenges.entries.removeAll { now() - it.value.expiresAt >= CODE_TTL }
    }

    private fun rate(key: String, max: Int, duration: Long) {
        val time = now()
        windows.values.forEach { times -> while (times.isNotEmpty() && time - times.first() >= CODE_TTL) times.removeFirst() }
        windows.entries.removeAll { it.value.isEmpty() }
        if (key !in windows && windows.size >= 100_000) fail(Kind.TooMany)
        val times = windows.getOrPut(key) { ArrayDeque() }
        if (times.count { time - it < duration } >= max) fail(Kind.TooMany)
        times.addLast(time)
    }

    private suspend fun commit(updated: AccountState) {
        if (file != null) withContext(Dispatchers.IO) {
            val target = file.toAbsolutePath()
            Files.createDirectories(target.parent)
            val posix = target.fileSystem.supportedFileAttributeViews().contains("posix")
            val temporary = if (posix) Files.createTempFile(target.parent, ".accounts-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                else Files.createTempFile(target.parent, ".accounts-", ".tmp")
            try {
                Files.writeString(temporary, EasyPaperJson.encodeToString(updated))
                FileChannel.open(temporary, WRITE).use { it.force(true) }
                // Same-directory atomic rename. If unsupported, fail rather than risk a partial database.
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
                state = updated
            } finally {
                Files.deleteIfExists(temporary)
            }
        } else state = updated
    }

    private fun secret() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    companion object {
        fun fromEnv(application: Application): Accounts {
            val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 15_000 } }
            application.monitor.subscribe(ApplicationStopped) { http.close() }
            fun env(name: String) = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            val from = env("EASYPAPER_MAIL_FROM")
            val desktopClientId = env("EASYPAPER_GOOGLE_DESKTOP_CLIENT_ID")
            val desktopClientSecret = env("EASYPAPER_GOOGLE_DESKTOP_CLIENT_SECRET")
            val mail = when (env("EASYPAPER_MAIL")?.lowercase(Locale.ROOT)) {
                "resend" -> env("RESEND_API_KEY")?.let { key -> from?.let { ResendMail(http, key, it) } }
                "smtp" -> {
                    val host = env("SMTP_HOST")
                    val port = env("SMTP_PORT")?.toIntOrNull() ?: 587
                    val user = env("SMTP_USER")
                    val password = System.getenv("SMTP_PASSWORD")?.takeIf { it.isNotEmpty() }
                    if (host != null && port in 1..65535 && user != null && password != null && from != null) SmtpMail(host, port, user, password, from) else null
                }
                else -> null
            }
            return Accounts(
                file = Path.of(env("EASYPAPER_DATA_DIR") ?: "/var/lib/easypaper", "accounts.json"), mail = mail,
                googleVerifier = GoogleTokenInfo(http),
                googleClientIds = env("EASYPAPER_GOOGLE_CLIENT_IDS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
                clinicToken = env("EASYPAPER_CLINIC_TOKEN"),
                googleDesktopClientId = desktopClientId,
                googleCodeExchange = if (desktopClientId != null && desktopClientSecret != null)
                    GoogleTokenExchange(http, desktopClientId, desktopClientSecret) else null,
            )
        }
    }
}

private fun normalizeEmail(value: String): String {
    val email = value.trim().lowercase(Locale.ROOT)
    require(email.length <= 254 && Regex("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+").matches(email))
    return email
}

private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
private fun equalHash(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))
private fun fail(kind: Kind): Nothing = throw AccountException(kind, "Account request failed: $kind")
private fun maskEmail(email: String) = "${email.substringBefore('@').take(1)}***@${email.substringAfter('@')}"
private fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    val countryLength = if (digits.startsWith("1") || digits.startsWith("7")) 1 else if (digits.length >= 11) digits.length - 9 else 0
    val prefix = if (phone.startsWith('+') && countryLength in 1..3) "+${digits.take(countryLength)} " else ""
    return "$prefix*** ** ${digits.takeLast(2)}"
}

fun Route.accountRoutes(accounts: Accounts) {
    post("/account/email/start") { call.accountResponse(accounts) { accounts.startEmail(bodyField("email"), accountIp()) } }
    post("/account/email/verify") {
        call.accountResponse(accounts) {
            val body = accountBody()
            accounts.verifyEmail(body.string("challengeId"), body.string("code"))
        }
    }
    post("/account/google") { call.accountResponse(accounts) { accounts.google(bodyField("idToken")) } }
    post("/account/google/code") {
        call.accountResponse(accounts) {
            val body = accountBody()
            accounts.googleCode(body.string("code"), body.string("codeVerifier"), body.string("redirectUri"))
        }
    }
    get("/account/me") { call.accountResponse(accounts) { accounts.me(bearer()) } }
    post("/account/signout") { call.accountResponse<Unit>(accounts) { accounts.signOut(bearer()) } }
    post("/invites") {
        call.accountResponse(accounts) {
            accounts.authorizeClinic(request.headers[CLINIC_HEADER])
            val body = accountBody()
            mapOf("inviteToken" to accounts.createInvite(body.string("name"), body["phone"]?.jsonPrimitive?.contentOrNull, body.string("email")))
        }
    }
    get("/invites/{inviteToken}") { call.accountResponse(accounts) { accounts.invitedPerson(parameters["inviteToken"].orEmpty()) } }
    post("/invites/{inviteToken}/confirm") { call.accountResponse(accounts) { accounts.confirmInvite(parameters["inviteToken"].orEmpty(), accountIp()) } }
}

internal fun accountClientIp(remote: String, forwardedFor: String?): String =
    if (remote == "127.0.0.1" || remote == "::1") forwardedFor?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() } ?: remote else remote

private fun RoutingCall.accountIp() = accountClientIp(request.local.remoteAddress, request.headers["X-Forwarded-For"])
private fun RoutingCall.bearer(): String? = request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.takeIf { it.isNotBlank() }
private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing field")
private suspend fun RoutingCall.bodyField(key: String) = accountBody().string(key)
private suspend fun RoutingCall.accountBody(): JsonObject {
    val bytes = receiveChannel().readRemaining(16_385).readByteArray()
    require(bytes.size <= 16_384)
    return EasyPaperJson.parseToJsonElement(bytes.decodeToString()).jsonObject
}

/** Sanitized status-only errors: no addresses, tokens, codes or upstream failure bodies in logs/responses. */
private suspend inline fun <reified T> RoutingCall.accountResponse(accounts: Accounts, action: RoutingCall.() -> T) {
    response.header(HttpHeaders.CacheControl, "no-store")
    try {
        accounts.limit(accountIp())
        val result = action()
        if (T::class == Unit::class) respond(HttpStatusCode.NoContent)
        else respondText(EasyPaperJson.encodeToString(result), ContentType.Application.Json, HttpStatusCode.OK)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AccountException) {
        val status = when (e.kind) {
            Kind.BadCode -> HttpStatusCode.BadRequest
            Kind.Expired -> HttpStatusCode.Gone
            Kind.TooMany -> HttpStatusCode.TooManyRequests
            Kind.MailUnavailable -> HttpStatusCode.ServiceUnavailable
            Kind.Unauthorized -> HttpStatusCode.Unauthorized
            Kind.NotFound -> HttpStatusCode.NotFound
            Kind.Network -> HttpStatusCode.BadGateway
        }
        respond(status)
    } catch (_: SerializationException) {
        respond(HttpStatusCode.BadRequest)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest)
    } catch (_: IOException) {
        respond(HttpStatusCode.InternalServerError)
    }
}
