package org.easypaper.account

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.easypaper.model.EasyPaperJson

@Serializable
data class Account(val id: String, val email: String, val name: String? = null, val phone: String? = null, val provider: String)

@Serializable
data class AccountSession(val token: String, val account: Account)

/** expiresAt is UTC epoch milliseconds. */
@Serializable
data class EmailChallenge(val challengeId: String, val expiresAt: Long)

@Serializable
data class InvitedPerson(val name: String, val phoneMasked: String?, val emailMasked: String)

class AccountException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { BadCode, Expired, TooMany, MailUnavailable, Unauthorized, NotFound, Network }
}

/** Works without ContentNegotiation. Caller cancellation is preserved; transport errors become Network. */
class AccountClient(private val baseUrl: String, private val http: HttpClient) {
    suspend fun startEmail(email: String): EmailChallenge = call(HttpMethod.Post, listOf("account", "email", "start"), fields("email" to email))

    suspend fun verifyEmail(challengeId: String, code: String): AccountSession =
        call(HttpMethod.Post, listOf("account", "email", "verify"), fields("challengeId" to challengeId, "code" to code))

    suspend fun google(idToken: String): AccountSession = call(HttpMethod.Post, listOf("account", "google"), fields("idToken" to idToken))

    suspend fun invitedPerson(inviteToken: String): InvitedPerson = call(HttpMethod.Get, listOf("invites", inviteToken))

    suspend fun confirmInvite(inviteToken: String): EmailChallenge = call(HttpMethod.Post, listOf("invites", inviteToken, "confirm"))

    suspend fun me(sessionToken: String): Account = call(HttpMethod.Get, listOf("account", "me"), sessionToken = sessionToken)

    suspend fun signOut(sessionToken: String) {
        call<Unit>(HttpMethod.Post, listOf("account", "signout"), sessionToken = sessionToken)
    }

    suspend fun createInvite(clinicToken: String, name: String, phone: String?, email: String): String =
        call<JsonObject>(HttpMethod.Post, listOf("invites"), fields("name" to name, "phone" to phone, "email" to email), clinicToken = clinicToken)
            .let { (it["inviteToken"] as? JsonPrimitive)?.content ?: throw AccountException(AccountException.Kind.Network, "Invalid account response") }

    private fun fields(vararg values: Pair<String, String?>) = JsonObject(values.associate { (key, value) -> key to JsonPrimitive(value) })

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: List<String>,
        body: JsonObject? = null,
        sessionToken: String? = null,
        clinicToken: String? = null,
    ): T {
        try {
            val response = http.request(baseUrl) {
                this.method = method
                expectSuccess = false
                url { appendPathSegments(path, encodeSlash = true) }
                sessionToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                clinicToken?.let { header("X-EasyPaper-Clinic-Token", it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
            if (response.status.value !in 200..299) {
                val kind = when (response.status) {
                    HttpStatusCode.BadRequest -> AccountException.Kind.BadCode
                    HttpStatusCode.Gone -> AccountException.Kind.Expired
                    HttpStatusCode.TooManyRequests -> AccountException.Kind.TooMany
                    HttpStatusCode.ServiceUnavailable -> AccountException.Kind.MailUnavailable
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> AccountException.Kind.Unauthorized
                    HttpStatusCode.NotFound -> AccountException.Kind.NotFound
                    else -> AccountException.Kind.Network
                }
                throw AccountException(kind, "Account request failed: ${response.status.value}")
            }
            if (T::class == Unit::class) return Unit as T
            return EasyPaperJson.decodeFromString<T>(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountException) {
            throw e
        } catch (_: Exception) {
            throw AccountException(AccountException.Kind.Network, "Account request unavailable")
        }
    }
}
