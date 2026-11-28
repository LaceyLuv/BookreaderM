package org.bookreader.mobile.backup

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReadingBackupFileTest {
    private val snapshot = ReadingBackup(42L, emptyList(), listOf("나의 서재 😀"), BackupSettings(1, "{}"), null)

    @Test fun portableEnvelopeRoundTripsUnicodeAndDoesNotDependOnFilename() {
        val bytes = envelope()
        assertEquals(snapshot, ReadingBackupFile.decode(bytes.inputStream()))
        assertTrue(bytes.copyOfRange(0, 8).contentEquals("BRMBKUP\n".toByteArray()))
        assertTrue(bytes.size < ReadingBackupFile.MAX_FILE_BYTES)
    }

    @Test fun modifiedTruncatedAndAppendedDataFailIntegrityBeforeRestore() {
        val original = envelope()
        val modified = original.copyOf().also { it[it.lastIndex] = ' '.code.toByte() }
        for (input in listOf(modified, original.copyOf(original.size - 1), original + byteArrayOf(0))) {
            rejects(BackupProblem.INTEGRITY, input)
        }
    }

    @Test fun futureVersionAndHugeDeclaredSizeRejectBeforePayloadAllocation() {
        val bytes = envelope()
        rejects(BackupProblem.VERSION, bytes.copyOf().also { it[11] = 2 })
        rejects(BackupProblem.LIMIT, bytes.copyOfRange(0, 16).also { it[12] = 0x7f.toByte() })
    }

    @Test fun untrustedPayloadWithValidChecksumStillMustBeStrictAndBounded() {
        rejects(BackupProblem.FORMAT, framed(byteArrayOf(0xc3.toByte(), 0x28)))
        rejects(BackupProblem.LIMIT, framed(("{\"unknown\":" + "[".repeat(50) + "]".repeat(50) + "}").toByteArray()))
        rejects(BackupProblem.LIMIT, framed(("[" + "0,".repeat(BackupRules.MAX_RECORDS) + "0]").toByteArray()))
        rejects(BackupProblem.FORMAT, framed(BackupRules.json.encodeToString(ReadingBackup.serializer(),
            snapshot.copy(shelves = listOf("same", "SAME"))).toByteArray()))
        val futureSettings = snapshot.copy(readerSettings = BackupSettings(999, "{}"))
        rejects(BackupProblem.FORMAT, framed(BackupRules.json.encodeToString(ReadingBackup.serializer(), futureSettings).toByteArray()))
    }

    private fun envelope(): ByteArray = ByteArrayOutputStream().also { ReadingBackupFile.encode(snapshot).writeTo(it) {} }.toByteArray()

    private fun framed(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        DataOutputStream(this).use {
            it.write("BRMBKUP\n".toByteArray())
            it.writeInt(BackupRules.VERSION)
            it.writeInt(bytes.size)
            it.write(MessageDigest.getInstance("SHA-256").digest(bytes))
            it.write(bytes)
        }
    }.toByteArray()

    @Test fun v2BundleRoundTripsBooksAndFontsAndKeepsV1Compatible() {
        val v1Bytes = envelope()
        val decodedV1 = ReadingBackupFile.decodeBundle(v1Bytes.inputStream(), { error("v1 must not stage") })
        assertTrue(decodedV1 is ReadingBackupFile.DecodedBackup.V1)
        assertEquals(snapshot, (decodedV1 as ReadingBackupFile.DecodedBackup.V1).snapshot)

        val bookBytes = "Hello Phase 4-2 v2 streaming backup!\n".toByteArray()
        val fontBytes = ByteArray(64) { (it + 1).toByte() }
        val (bundle, raw) = v2Envelope(bookBytes, fontBytes)
        val stageDir = java.nio.file.Files.createTempDirectory("v2-roundtrip-").toFile()
        try {
            val decoded = ReadingBackupFile.decodeBundle(raw.inputStream(), { stageDir })
            assertTrue(decoded is ReadingBackupFile.DecodedBackup.V2)
            val v2 = decoded as ReadingBackupFile.DecodedBackup.V2
            assertEquals(bundle, v2.bundle)
            assertEquals(2, v2.staged.size)
            assertArrayEquals(bookBytes, java.io.File(stageDir, v2.staged[0].stagingName).readBytes())
            assertArrayEquals(fontBytes, java.io.File(stageDir, v2.staged[1].stagingName).readBytes())
        } finally {
            stageDir.deleteRecursively()
        }
    }

    @Test fun v2BundleRejectsFutureVersionTraversalSymlinkTamperingSizeMismatchAndTrailingBytes() {
        val bookBytes = "Book payload for security verification\n".toByteArray()
        val fontBytes = ByteArray(48) { (it * 5 and 0xff).toByte() }
        val (_, validRaw) = v2Envelope(bookBytes, fontBytes)

        fun rejectsV2(expected: BackupProblem, input: ByteArray) {
            val stageDir = java.nio.file.Files.createTempDirectory("v2-reject-").toFile()
            try {
                val failure = assertThrows(BackupFailure::class.java) {
                    ReadingBackupFile.decodeBundle(input.inputStream(), { stageDir })
                }
                assertEquals(expected, failure.problem)
            } finally {
                stageDir.deleteRecursively()
            }
        }

        // Future version 3 rejected before manifest allocation
        rejectsV2(BackupProblem.VERSION, validRaw.copyOf().also { it[11] = 3 })

        // Trailing garbage after final asset rejected
        rejectsV2(BackupProblem.INTEGRITY, validRaw + byteArrayOf(0x42))

        // Truncated asset payload rejected
        rejectsV2(BackupProblem.INTEGRITY, validRaw.copyOf(validRaw.size - 1))

        // Corrupted asset byte fails per-asset SHA-256
        val corruptedAsset = validRaw.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xff).toByte() }
        rejectsV2(BackupProblem.INTEGRITY, corruptedAsset)

        // Non-regular file mode (e.g., symlink flag 0x01) in first asset frame rejected
        val frameMagic = "BRMAST\n".toByteArray()
        val frameOffset = indexOfSubarray(validRaw, frameMagic)
        assertTrue(frameOffset > 0)
        val symlinkFrame = validRaw.copyOf().also { it[frameOffset + frameMagic.size] = 0x01 }
        rejectsV2(BackupProblem.FORMAT, symlinkFrame)

        // Path traversal injected into frame path rejected
        val pathStart = frameOffset + frameMagic.size + 1 + 4
        val traversalFrame = validRaw.copyOf().also {
            it[pathStart] = '.'.code.toByte()
            it[pathStart + 1] = '.'.code.toByte()
            it[pathStart + 2] = '/'.code.toByte()
        }
        rejectsV2(BackupProblem.FORMAT, traversalFrame)

        // Declared size tampered in asset frame header rejected
        val pathLen = java.nio.ByteBuffer.wrap(validRaw, frameOffset + frameMagic.size + 1, 4).int
        val sizeOffset = pathStart + pathLen
        val sizeMismatch = validRaw.copyOf().also { it[sizeOffset + 7] = (it[sizeOffset + 7].toInt() xor 0x01).toByte() }
        rejectsV2(BackupProblem.INTEGRITY, sizeMismatch)
    }

    private fun v2Envelope(bookBytes: ByteArray, fontBytes: ByteArray): Pair<BackupBundleV2, ByteArray> {
        val bookHash = sha256Hex(bookBytes)
        val fontHash = sha256Hex(fontBytes)
        val revision = org.bookreader.mobile.importing.txtContentRevision(bookHash, "utf-8", 1)
        val bookRecord = BackupBook(
            sha256 = bookHash, byteSize = bookBytes.size.toLong(), format = "TXT", title = "V2 Book",
            displayName = "v2.txt", author = null, revision = revision, encoding = "utf-8",
            normalizationVersion = 1, addedAt = 1L, lastReadAt = null, details = null, preferences = null,
            shelves = emptyList(), progress = emptyList(), bookmarks = emptyList(),
            txtHighlights = emptyList(), epubHighlights = emptyList(), tocEdits = emptyList(),
        )
        val records = snapshot.copy(books = listOf(bookRecord))
        val bookEntry = BackupAssetEntry(BackupAssetKind.BOOK, "books/$bookHash.bin", bookHash, bookBytes.size.toLong(), "TXT", null)
        val fontEntry = BackupAssetEntry(BackupAssetKind.FONT, "fonts/$fontHash.bin", fontHash, fontBytes.size.toLong(), null, "V2Font.ttf")
        val bundle = BackupBundleV2(records, listOf(bookEntry, fontEntry))
        val raw = ByteArrayOutputStream().also { out ->
            ReadingBackupFile.writeBundleV2(
                bundle = bundle,
                sources = listOf(
                    ReadingBackupFile.AssetSource(bookEntry) { bookBytes.inputStream() },
                    ReadingBackupFile.AssetSource(fontEntry) { fontBytes.inputStream() },
                ),
                output = out,
            )
        }.toByteArray()
        return bundle to raw
    }

    private fun indexOfSubarray(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun rejects(problem: BackupProblem, bytes: ByteArray) {
        val failure = assertThrows(BackupFailure::class.java) { ReadingBackupFile.decode(bytes.inputStream()) }
        assertEquals(problem, failure.problem)
    }
}
