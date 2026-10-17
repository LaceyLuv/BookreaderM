package org.bookreader.mobile.epub

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** M01 probe: preserve the complete public SDK JSON, never a page-number approximation. */
object EpubSdkProbe {
    fun roundTrip(serialized: String): String {
        require(serialized.length <= 64 * 1024)
        return requireNotNull(Locator.fromJSON(JSONObject(serialized))).toJSON().toString()
    }
}
