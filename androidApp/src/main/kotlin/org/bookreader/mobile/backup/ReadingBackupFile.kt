package org.bookreader.mobile.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
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
 * Portable .brmbackup framing:
 * - v1: BRMBKUP\n + version(1) + payloadSize + payloadSha256 + UTF-8 ReadingBackup JSON + EOF.
 * - v2: BRMBKUP\n + version(2) + manifestSize + manifestSha256 + UTF-8 BackupBundleV2 JSON
 *       + sequential regular-file asset frames (BRMAST\n + mode(0) + path + size + sha256 + bytes) + EOF.
 */
internal object ReadingBackupFile {
    private val magic = "BRMBKUP\n".encodeToByteArray()
    private val assetMagic = "BRMAST\n".encodeToByteArray()
    private const val DIGEST_BYTES = 32
    private const val CHUNK_BYTES = 64 * 1024
    private const val HEADER_BYTES = 8 + 4 + 4 + DIGEST_BYTES
    private const val MAX_PATH_BYTES = 128
    const val MAX_FILE_BYTES = HEADER_BYTES + BackupRules.MAX_PAYLOAD_BYTES

    data class AssetSource(val entry: BackupAssetEntry, val open: () -> InputStream)

    sealed interface DecodedBackup {
        data class V1(val snapshot: ReadingBackup) : DecodedBackup
        data class V2(val bundle: BackupBundleV2, val staged: List<StagedBackupAsset>) : DecodedBackup
    }

    fun encode(snapshot: ReadingBackup, checkpoint: () -> Unit = {}): EncodedReadingBackup {
        validate(snapshot)
        val payload = encodeBoundedJson(ReadingBackup.serializer(), snapshot, checkpoint)
        return EncodedReadingBackup(buildHeader(BackupRules.VERSION, payload), payload)
    }

    /** Strict v1 records-only decoder. Rejects non-v1 versions and any trailing bytes. */
    fun decode(input: InputStream, checkpoint: () -> Unit = {}): ReadingBackup = parse {
        val stream = DataInputStream(input)
        checkpoint()
        val version = readMagicAndVersion(stream)
        if (version != BackupRules.VERSION) throw BackupFailure(BackupProblem.VERSION)
        readV1Payload(stream, checkpoint)
    }

    /** Streams a v2 manifest followed by verified raw asset frames without buffering files in memory. */
    fun writeBundleV2(
        bundle: BackupBundleV2,
        sources: List<AssetSource>,
        output: OutputStream,
        checkpoint: () -> Unit = {},
    ) {
        parse { BackupAssetRules.validateBundle(bundle) }
        if (sources.size != bundle.assets.size || sources.zip(bundle.assets).any { (src, entry) -> src.entry != entry }) {
            throw BackupFailure(BackupProblem.FORMAT)
        }
        val manifest = encodeBoundedJson(BackupBundleV2.serializer(), bundle, checkpoint)
        val header = buildHeader(BackupAssetRules.VERSION_V2, manifest)
        checkpoint()
        output.write(header)
        for (offset in manifest.indices step CHUNK_BYTES) {
            checkpoint()
            output.write(manifest, offset, minOf(CHUNK_BYTES, manifest.size - offset))
        }
        val buffer = ByteArray(CHUNK_BYTES)
        for (source in sources) {
            checkpoint()
            val entry = source.entry
            val pathBytes = entry.path.encodeToByteArray()
            val expectedDigest = hexToBytes(entry.sha256)
            val frameHeader = ByteArrayOutputStream(assetMagic.size + 1 + 4 + pathBytes.size + 8 + DIGEST_BYTES)
            DataOutputStream(frameHeader).use { out ->
                out.write(assetMagic)
                out.writeByte(BackupAssetRules.REGULAR_FILE_MODE.toInt())
                out.writeInt(pathBytes.size)
                out.write(pathBytes)
                out.writeLong(entry.byteSize)
                out.write(expectedDigest)
            }
            output.write(frameHeader.toByteArray())
            val runningDigest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            source.open().use { input ->
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    copied += count
                    if (copied > entry.byteSize) throw BackupFailure(BackupProblem.INTEGRITY)
                    runningDigest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            if (copied != entry.byteSize || !MessageDigest.isEqual(runningDigest.digest(), expectedDigest)) {
                throw BackupFailure(BackupProblem.INTEGRITY)
            }
        }
        output.flush()
    }

    /**
     * Decodes either a v1 records-only backup or a v2 streaming asset bundle.
     * For v2, each asset frame is streamed directly into stageDir, verified against its declared
     * canonical path, regular-file mode, exact size, SHA-256, and content validator before returning.
     */
    fun decodeBundle(
        input: InputStream,
        stageDirProvider: () -> File,
        reserveCheck: (Long) -> Unit = {},
        validateStaged: (StagedBackupAsset, File, ReadingBackup) -> Unit = { _, _, _ -> },
        checkpoint: () -> Unit = {},
    ): DecodedBackup = parse {
        val stream = DataInputStream(input)
        checkpoint()
        when (readMagicAndVersion(stream)) {
            BackupRules.VERSION -> DecodedBackup.V1(readV1Payload(stream, checkpoint))
            BackupAssetRules.VERSION_V2 -> {
                val manifestBytes = readLengthPrefixedVerifiedJsonBytes(stream, checkpoint)
                val text = decodeStrictUtf8(manifestBytes)
                BackupRules.boundedJson(text, BackupRules.MAX_PAYLOAD_BYTES)
                val bundle = BackupRules.json.decodeFromString<BackupBundleV2>(text)
                BackupAssetRules.validateBundle(bundle)
                val staged = ArrayList<StagedBackupAsset>(bundle.assets.size)
                if (bundle.assets.isNotEmpty()) {
                    val stageDir = stageDirProvider()
                    val buffer = ByteArray(CHUNK_BYTES)
                    for ((index, expected) in bundle.assets.withIndex()) {
                        checkpoint()
                        val frameKey = ByteArray(assetMagic.size)
                        stream.readFully(frameKey)
                        if (!frameKey.contentEquals(assetMagic)) throw BackupFailure(BackupProblem.FORMAT)
                        val mode = stream.readByte()
                        if (mode != BackupAssetRules.REGULAR_FILE_MODE) throw BackupFailure(BackupProblem.FORMAT)
                        val pathLen = stream.readInt()
                        if (pathLen <= 0 || pathLen > MAX_PATH_BYTES) throw BackupFailure(BackupProblem.FORMAT)
                        val pathBytes = ByteArray(pathLen)
                        stream.readFully(pathBytes)
                        val framePath = decodeStrictUtf8(pathBytes)
                        BackupAssetRules.validateCanonicalPath(framePath, expected.kind, expected.sha256)
                        if (framePath != expected.path) throw BackupFailure(BackupProblem.FORMAT)
                        val declaredSize = stream.readLong()
                        if (declaredSize < 0 || declaredSize > BackupAssetRules.MAX_BUNDLE_BYTES) {
                            throw BackupFailure(BackupProblem.LIMIT)
                        }
                        if (declaredSize != expected.byteSize) throw BackupFailure(BackupProblem.INTEGRITY)
                        val frameDigest = ByteArray(DIGEST_BYTES)
                        stream.readFully(frameDigest)
                        val expectedDigest = hexToBytes(expected.sha256)
                        if (!MessageDigest.isEqual(frameDigest, expectedDigest)) {
                            throw BackupFailure(BackupProblem.INTEGRITY)
                        }
                        val stagingName = "asset-%04d.bin".format(java.util.Locale.ROOT, index)
                        val target = File(stageDir, stagingName)
                        val actualDigest = MessageDigest.getInstance("SHA-256")
                        var remaining = declaredSize
                        FileOutputStream(target).use { out ->
                            while (remaining > 0L) {
                                checkpoint()
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                stream.readFully(buffer, 0, toRead)
                                reserveCheck(toRead.toLong())
                                actualDigest.update(buffer, 0, toRead)
                                out.write(buffer, 0, toRead)
                                remaining -= toRead
                            }
                            out.fd.sync()
                        }
                        if (!MessageDigest.isEqual(actualDigest.digest(), expectedDigest)) {
                            throw BackupFailure(BackupProblem.INTEGRITY)
                        }
                        val asset = StagedBackupAsset(
                            kind = expected.kind,
                            stagingName = stagingName,
                            sha256 = expected.sha256,
                            byteSize = expected.byteSize,
                            format = expected.format,
                            label = expected.label,
                        )
                        validateStaged(asset, target, bundle.records)
                        staged.add(asset)
                    }
                }
                if (stream.read() != -1) throw BackupFailure(BackupProblem.INTEGRITY)
                DecodedBackup.V2(bundle, staged)
            }
            else -> throw BackupFailure(BackupProblem.VERSION)
        }
    }

    private fun readMagicAndVersion(stream: DataInputStream): Int {
        val key = ByteArray(magic.size)
        stream.readFully(key)
        if (!key.contentEquals(magic)) throw BackupFailure(BackupProblem.FORMAT)
        return stream.readInt()
    }

    private fun readV1Payload(stream: DataInputStream, checkpoint: () -> Unit): ReadingBackup {
        val payload = readLengthPrefixedVerifiedJsonBytes(stream, checkpoint)
        if (stream.read() != -1) throw BackupFailure(BackupProblem.INTEGRITY)
        checkpoint()
        val text = decodeStrictUtf8(payload)
        BackupRules.boundedJson(text, BackupRules.MAX_PAYLOAD_BYTES)
        return BackupRules.json.decodeFromString<ReadingBackup>(text).also(::validate)
    }

    private fun readLengthPrefixedVerifiedJsonBytes(stream: DataInputStream, checkpoint: () -> Unit): ByteArray {
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
        if (!MessageDigest.isEqual(expected, digest(payload))) {
            throw BackupFailure(BackupProblem.INTEGRITY)
        }
        return payload
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> encodeBoundedJson(
        serializer: kotlinx.serialization.SerializationStrategy<T>,
        value: T,
        checkpoint: () -> Unit,
    ): ByteArray {
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
        BackupRules.json.encodeToStream(serializer, value, bounded)
        return buffer.toByteArray()
    }

    private fun buildHeader(version: Int, payload: ByteArray): ByteArray {
        val header = ByteArrayOutputStream(HEADER_BYTES)
        DataOutputStream(header).use {
            it.write(magic)
            it.writeInt(version)
            it.writeInt(payload.size)
            it.write(digest(payload))
        }
        return header.toByteArray()
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.length != DIGEST_BYTES * 2) throw BackupFailure(BackupProblem.FORMAT)
        return ByteArray(DIGEST_BYTES) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun validate(snapshot: ReadingBackup) = parse { BackupRules.validate(snapshot) }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private inline fun <T> parse(action: () -> T): T = try {
        action()
    } catch (failure: BackupFailure) {
        throw failure
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
