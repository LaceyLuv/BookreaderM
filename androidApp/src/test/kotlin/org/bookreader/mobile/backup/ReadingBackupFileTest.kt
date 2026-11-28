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

    private fun rejects(problem: BackupProblem, bytes: ByteArray) {
        val failure = assertThrows(BackupFailure::class.java) { ReadingBackupFile.decode(bytes.inputStream()) }
        assertEquals(problem, failure.problem)
    }
}
