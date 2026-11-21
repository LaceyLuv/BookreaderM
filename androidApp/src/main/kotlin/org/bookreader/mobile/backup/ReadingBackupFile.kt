package org.bookreader.mobile.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.encodeToStream

/**
 * BRMBKUP + LF, then big-endian version and payload byte length, SHA-256, and UTF-8 JSON.
 * The v1 envelope contains records only and accepts neither attachments nor trailing bytes.
 */
internal object ReadingBackupFile {
    private val magic = "BRMBKUP\n".encodeToByteArray()
    private const val DIGEST_BYTES = 32
    private const val CHUNK_BYTES = 64 * 1024
    private const val HEADER_BYTES = 8 + 4 + 4 + DIGEST_BYTES
    const val MAX_FILE_BYTES = HEADER_BYTES + BackupRules.MAX_PAYLOAD_BYTES

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(snapshot: ReadingBackup, checkpoint: () -> Unit = {}): EncodedReadingBackup {
        validate(snapshot)
        val buffer = ByteArrayOutputStream()
        val bounded = object : OutputStream() {
            private fun checkWrite(count: Int) {
                checkpoint()
                if (count > BackupRules.MAX_PAYLOAD_BYTES - buffer.size()) throw BackupFailure(BackupProblem.LIMIT)
            }

            override fun write(value: Int) {
                checkWrite(1)
                buffer.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                checkWrite(length)
                buffer.write(bytes, offset, length)
            }
        }
        BackupRules.json.encodeToStream(ReadingBackup.serializer(), snapshot, bounded)
        val payload = buffer.toByteArray()
        val header = ByteArrayOutputStream(HEADER_BYTES)
        DataOutputStream(header).use {
            it.write(magic)
            it.writeInt(BackupRules.VERSION)
            it.writeInt(payload.size)
            it.write(digest(payload))
        }
        return EncodedReadingBackup(header.toByteArray(), payload)
    }

    fun decode(input: InputStream, checkpoint: () -> Unit = {}): ReadingBackup = parse {
        val stream = DataInputStream(input)
        checkpoint()
        val key = ByteArray(magic.size)
        stream.readFully(key)
        if (!key.contentEquals(magic)) throw BackupFailure(BackupProblem.FORMAT)
        if (stream.readInt() != BackupRules.VERSION) throw BackupFailure(BackupProblem.VERSION)
        val size = stream.readInt()
        if (size <= 0) throw BackupFailure(BackupProblem.FORMAT)
        if (size > BackupRules.MAX_PAYLOAD_BYTES) throw BackupFailure(BackupProblem.LIMIT)
        val expected = ByteArray(DIGEST_BYTES)
        stream.readFully(expected)
        val payload = ByteArray(size)
        var offset = 0
        while (offset < size) {
            checkpoint()
            val count = minOf(CHUNK_BYTES, size - offset)
            stream.readFully(payload, offset, count)
            offset += count
        }
        if (stream.read() != -1 || !MessageDigest.isEqual(expected, digest(payload))) {
            throw BackupFailure(BackupProblem.INTEGRITY)
        }
        checkpoint()
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString()
        BackupRules.boundedJson(text, BackupRules.MAX_PAYLOAD_BYTES)
        BackupRules.json.decodeFromString<ReadingBackup>(text).also(::validate)
    }

    private fun validate(snapshot: ReadingBackup) = parse { BackupRules.validate(snapshot) }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private inline fun <T> parse(action: () -> T): T = try {
        action()
    } catch (_: EOFException) {
        throw BackupFailure(BackupProblem.INTEGRITY)
    } catch (_: CharacterCodingException) {
        throw BackupFailure(BackupProblem.FORMAT)
    } catch (_: SerializationException) {
        throw BackupFailure(BackupProblem.FORMAT)
    } catch (_: IllegalArgumentException) {
        throw BackupFailure(BackupProblem.FORMAT)
    }

    class EncodedReadingBackup(private val header: ByteArray, private val payload: ByteArray) {
        fun writeTo(output: OutputStream, checkpoint: () -> Unit) {
            checkpoint()
            output.write(header)
            for (offset in payload.indices step CHUNK_BYTES) {
                checkpoint()
                output.write(payload, offset, minOf(CHUNK_BYTES, payload.size - offset))
            }
            output.flush()
        }
    }
}
