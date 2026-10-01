package org.bookreader.mobile.locator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.bookreader.mobile.model.isManagedRelativePath

sealed interface ContentLocator {
    val contentRevision: String
    val version: Int get() = 1
}

@Serializable
enum class TextAffinity {
    @SerialName("leading") LEADING,
    @SerialName("trailing") TRAILING,
}

@Serializable
data class TxtLocatorPayload(
    val utf16Offset: Long,
    val affinity: TextAffinity = TextAffinity.LEADING,
    val contextHash: String? = null,
) {
    init { require(utf16Offset >= 0) }
}

data class TxtLocator(
    override val contentRevision: String,
    val payload: TxtLocatorPayload,
) : ContentLocator {
    init { require(contentRevision.isNotBlank()) }
}

/** Readium public Locator JSON is retained intact; shared has no Readium dependency. */
@Serializable
data class EpubLocatorPayload(
    val sdk: String = "readium-kotlin",
    val schemaVersion: Int = 1,
    val locator: JsonObject,
) {
    init {
        require(sdk.isNotBlank())
        require(schemaVersion > 0)
        require(locator.isNotEmpty())
    }
}

data class EpubLocator(
    override val contentRevision: String,
    val payload: EpubLocatorPayload,
) : ContentLocator {
    init { require(contentRevision.isNotBlank()) }
}

@Serializable
data class ComicLocatorPayload(
    val entryKey: String,
    val sortVersion: Int = 1,
    val imageIndexHint: Int? = null,
    val normalizedX: Double = 0.0,
    val normalizedY: Double = 0.0,
) {
    init {
        require(isManagedRelativePath(entryKey))
        require(sortVersion > 0)
        require(imageIndexHint == null || imageIndexHint >= 0)
        require(normalizedX.isFinite() && normalizedX in 0.0..1.0)
        require(normalizedY.isFinite() && normalizedY in 0.0..1.0)
    }
}

data class ComicLocator(
    override val contentRevision: String,
    val payload: ComicLocatorPayload,
) : ContentLocator {
    init { require(contentRevision.isNotBlank()) }
}
