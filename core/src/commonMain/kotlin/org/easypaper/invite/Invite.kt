package org.easypaper.invite

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.model.CareRequest
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.SCHEMA_VERSION
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Invite = the doctor's [CareRequest] packed into one string the patient opens from a link or a QR code.
 *
 * Code:  `epi1:<base64url(JSON)>` — "epi" + format version; unrelated to the report QR (`ep1:`).
 * Link:  `<base>#epi1:...` — the data sits in the URL fragment, so a web server never receives it.
 */
const val INVITE_FORMAT_VERSION = 1

/** Default link base; the web build can pass its own https address instead. */
const val DEFAULT_INVITE_BASE = "easypaper://invite"

/** Max QR payload in byte mode at error correction M (version 40). Longer invites go by link only. */
const val INVITE_QR_MAX_CHARS = 2331

/** Input longer than this is rejected before decoding, even when it arrives as a link. */
const val INVITE_MAX_CHARS = 64 * 1024

private const val PREFIX = "epi"
private val PREFIX_PATTERN = Regex("^epi(\\d{1,3}):")

@OptIn(ExperimentalEncodingApi::class)
private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** Same rules as [EasyPaperJson] but drops default and null fields to keep the QR small. */
private val compactJson = Json(from = EasyPaperJson) {
    encodeDefaults = false
    explicitNulls = false
}

sealed interface InviteResult {
    data class Ok(val request: CareRequest) : InviteResult
    data class Error(val reason: InviteError) : InviteResult
}

/** Why an invite did not open; the UI maps each reason to its own message. */
enum class InviteError {
    /** Not an Easy Paper invite at all (random text, a report QR, an empty string). */
    NOT_INVITE,
    /** Made by a newer app version: the patient needs to update the app. */
    NEWER_VERSION,
    /** Looks like an invite but is cut off or altered. */
    DAMAGED,
    /** Longer than [INVITE_MAX_CHARS]. */
    TOO_LARGE,
}

/** Packs a request into the invite code `epi1:...`. */
@OptIn(ExperimentalEncodingApi::class)
fun encodeInvite(request: CareRequest): String {
    val fields = compactJson.encodeToJsonElement(CareRequest.serializer(), request).jsonObject
    // schemaVersion always travels, even when it equals the default, so an older app can refuse a newer request.
    val json = JsonObject(fields + ("schemaVersion" to JsonPrimitive(request.schemaVersion)))
    return "$PREFIX$INVITE_FORMAT_VERSION:" + base64.encode(json.toString().encodeToByteArray())
}

/** Link the patient taps; [base] must not contain '#'. */
fun inviteLink(request: CareRequest, base: String = DEFAULT_INVITE_BASE): String {
    require('#' !in base) { "invite base must not contain '#'" }
    return "$base#${encodeInvite(request)}"
}

/** True when [content] (a code or a link) fits into one QR code. */
fun inviteFitsQr(content: String): Boolean = content.encodeToByteArray().size <= INVITE_QR_MAX_CHARS

/** Opens an invite from a raw code or from a link with the code in its fragment. Never throws. */
fun parseInvite(text: String): InviteResult {
    val input = text.trim()
    if (input.length > INVITE_MAX_CHARS) return InviteResult.Error(InviteError.TOO_LARGE)
    val code = input.substringAfter('#')
    val match = PREFIX_PATTERN.find(code) ?: return InviteResult.Error(InviteError.NOT_INVITE)
    val version = match.groupValues[1].toInt()
    return when {
        version == INVITE_FORMAT_VERSION -> decodeV1(code.substring(match.range.last + 1))
        version > INVITE_FORMAT_VERSION -> InviteResult.Error(InviteError.NEWER_VERSION)
        else -> InviteResult.Error(InviteError.NOT_INVITE)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeV1(payload: String): InviteResult {
    val damaged = InviteResult.Error(InviteError.DAMAGED)
    if (payload.isEmpty()) return damaged
    return try {
        val json = base64.decode(payload).decodeToString(throwOnInvalidSequence = true)
        val obj = compactJson.parseToJsonElement(json).jsonObject
        val schema = obj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return damaged
        if (schema > SCHEMA_VERSION) return InviteResult.Error(InviteError.NEWER_VERSION)
        InviteResult.Ok(compactJson.decodeFromJsonElement(CareRequest.serializer(), obj))
    } catch (e: IllegalArgumentException) {
        damaged // bad base64, broken JSON (SerializationException), missing fields or a failed CareRequest check
    } catch (e: CharacterCodingException) {
        damaged
    }
}
