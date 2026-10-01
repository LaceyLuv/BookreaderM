package org.bookreader.mobile.locator

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
private data class LocatorEnvelope(
    val type: String,
    val version: Int,
    val contentRevision: String,
    val payload: JsonObject,
)

sealed interface LocatorDecodeResult {
    data class Success(val locator: ContentLocator) : LocatorDecodeResult
    data class Unsupported(val type: String, val version: Int) : LocatorDecodeResult
    data object Malformed : LocatorDecodeResult
}

/** Decoding never repairs or rewrites an unsupported/corrupt persisted record. */
class LocatorCodec {
    private val json = Json { encodeDefaults = true }

    fun encode(locator: ContentLocator): String {
        val (type, payload) = when (locator) {
            is TxtLocator -> "txt" to json.encodeToJsonElement(locator.payload)
            is EpubLocator -> "epub" to json.encodeToJsonElement(locator.payload)
            is ComicLocator -> "comic" to json.encodeToJsonElement(locator.payload)
        }
        return json.encodeToString(
            LocatorEnvelope(type, locator.version, locator.contentRevision, payload as JsonObject),
        )
    }

    fun decode(serialized: String): LocatorDecodeResult = try {
        val envelope = json.decodeFromString<LocatorEnvelope>(serialized)
        if (envelope.contentRevision.isBlank()) {
            LocatorDecodeResult.Malformed
        } else if (envelope.version != 1 || envelope.type !in setOf("txt", "epub", "comic")) {
            LocatorDecodeResult.Unsupported(envelope.type, envelope.version)
        } else {
            val locator = when (envelope.type) {
                "txt" -> TxtLocator(envelope.contentRevision, json.decodeFromJsonElement(envelope.payload))
                "epub" -> EpubLocator(envelope.contentRevision, json.decodeFromJsonElement(envelope.payload))
                else -> ComicLocator(envelope.contentRevision, json.decodeFromJsonElement(envelope.payload))
            }
            LocatorDecodeResult.Success(locator)
        }
    } catch (_: SerializationException) {
        LocatorDecodeResult.Malformed
    } catch (_: IllegalArgumentException) {
        LocatorDecodeResult.Malformed
    }
}
