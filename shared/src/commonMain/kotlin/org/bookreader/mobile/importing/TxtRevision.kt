package org.bookreader.mobile.importing

fun txtContentRevision(sourceSha256: String, encodingId: String, normalizationVersion: Int): String {
    require(sourceSha256.length == 64 && sourceSha256.all { it in "0123456789abcdef" })
    require(encodingId in setOf("utf-8", "utf-16le", "utf-16be", "cp949", "euc-kr"))
    require(normalizationVersion == 1)
    return sha256Utf8("bookreader-txt\n$sourceSha256\n$encodingId\n$normalizationVersion\n")
}

internal expect fun sha256Utf8(value: String): String
