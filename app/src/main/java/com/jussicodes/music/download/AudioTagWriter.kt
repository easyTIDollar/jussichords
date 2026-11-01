package com.jussicodes.music.download

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * 负责将歌曲元数据、内嵌歌词（USLT / VorbisComment）和专辑封面（APIC / PICTURE）
 * 直接写入音频文件本身（MP3 / FLAC），无需引入庞大的外部第三方库。
 */
object AudioTagWriter {

    private const val TAG = "AudioTagWriter"

    fun embedMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        lyrics: String? = null,
        coverBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists() || file.length() < 128) return false

        return runCatching {
            val header = ByteArray(8)
            FileInputStream(file).use { it.read(header) }

            val isFlac = header[0] == 'f'.code.toByte() &&
                    header[1] == 'L'.code.toByte() &&
                    header[2] == 'a'.code.toByte() &&
                    header[3] == 'C'.code.toByte()

            if (isFlac) {
                embedFlac(file, title, artist, album, lyrics, coverBytes)
            } else {
                embedMp3(file, title, artist, album, lyrics, coverBytes)
            }
        }.onFailure {
            Log.e(TAG, "Failed to embed metadata: ${it.message}", it)
        }.getOrDefault(false)
    }

    // =========================================================================
    // MP3 (ID3v2.3) 实现
    // =========================================================================

    private fun embedMp3(
        file: File,
        title: String,
        artist: String,
        album: String,
        lyrics: String?,
        coverBytes: ByteArray?
    ): Boolean {
        val totalLength = file.length()
        val fis = FileInputStream(file)
        var audioStartOffset = 0L

        // 检查原文件开头是否已有 ID3v2 标签，有则跳过旧标签直接定位到纯音频帧起始位置
        val header = ByteArray(10)
        val read = fis.read(header)
        if (read == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val tagSize = (header[6].toInt() and 0x7F shl 21) or
                    (header[7].toInt() and 0x7F shl 14) or
                    (header[8].toInt() and 0x7F shl 7) or
                    (header[9].toInt() and 0x7F)
            audioStartOffset = 10L + tagSize
        }
        fis.close()

        val framesStream = ByteArrayOutputStream()

        // TIT2 (Title)
        writeTextFrame(framesStream, "TIT2", title)
        // TPE1 (Artist)
        writeTextFrame(framesStream, "TPE1", artist)
        // TALB (Album)
        writeTextFrame(framesStream, "TALB", album)

        // USLT (Lyrics 内嵌歌词)
        if (!lyrics.isNullOrBlank()) {
            writeLyricsFrame(framesStream, lyrics)
        }

        // APIC (Cover 专辑封面)
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            writeApicFrame(framesStream, coverBytes)
        }

        val framesData = framesStream.toByteArray()
        val tagSize = framesData.size

        val id3Header = ByteArray(10).apply {
            this[0] = 'I'.code.toByte()
            this[1] = 'D'.code.toByte()
            this[2] = '3'.code.toByte()
            this[3] = 3 // ID3v2.3
            this[4] = 0 // Revision
            this[5] = 0 // Flags
            // Synchsafe integer (7 bits per byte)
            this[6] = ((tagSize shr 21) and 0x7F).toByte()
            this[7] = ((tagSize shr 14) and 0x7F).toByte()
            this[8] = ((tagSize shr 7) and 0x7F).toByte()
            this[9] = (tagSize and 0x7F).toByte()
        }

        val tempOut = File(file.parentFile, "${file.name}.tagged")
        FileOutputStream(tempOut).use { fos ->
            fos.write(id3Header)
            fos.write(framesData)

            // 拷贝原音频帧流
            FileInputStream(file).use { audioIn ->
                if (audioStartOffset > 0) {
                    audioIn.skip(audioStartOffset)
                }
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (audioIn.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
            }
        }

        // 替换文件
        return if (tempOut.length() > 0) {
            file.delete()
            tempOut.renameTo(file)
        } else {
            tempOut.delete()
            false
        }
    }

    private fun writeTextFrame(stream: ByteArrayOutputStream, frameId: String, text: String) {
        if (text.isBlank()) return
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        // 编码标识: 0x03 代表 UTF-8 (ID3v2.4) / 0x01 UTF-16。很多现代播放器兼容 0x03 或使用 UTF-16LE 带 BOM
        val payload = ByteArrayOutputStream()
        payload.write(0x01) // UTF-16 with BOM
        payload.write(0xFF) // BOM LE
        payload.write(0xFE)
        val utf16Bytes = text.toByteArray(StandardCharsets.UTF_16LE)
        payload.write(utf16Bytes)

        writeFrame(stream, frameId, payload.toByteArray())
    }

    private fun writeLyricsFrame(stream: ByteArrayOutputStream, lyrics: String) {
        val payload = ByteArrayOutputStream()
        payload.write(0x01) // UTF-16 with BOM
        payload.write("eng".toByteArray(StandardCharsets.ISO_8859_1)) // Language
        // Content descriptor (空字符串以 0x00, 0x00 结尾)
        payload.write(0x00)
        payload.write(0x00)
        // 歌词正文带 BOM
        payload.write(0xFF)
        payload.write(0xFE)
        payload.write(lyrics.toByteArray(StandardCharsets.UTF_16LE))

        writeFrame(stream, "USLT", payload.toByteArray())
    }

    private fun writeApicFrame(stream: ByteArrayOutputStream, imageBytes: ByteArray) {
        val mimeType = if (imageBytes.size > 8 && imageBytes[0] == 0x89.toByte() && imageBytes[1] == 'P'.code.toByte()) {
            "image/png"
        } else {
            "image/jpeg"
        }

        val payload = ByteArrayOutputStream()
        payload.write(0x00) // ISO-8859-1 for mime/desc
        payload.write(mimeType.toByteArray(StandardCharsets.ISO_8859_1))
        payload.write(0x00) // null-terminated mime
        payload.write(0x03) // Picture type: 0x03 = Cover (front)
        payload.write(0x00) // null-terminated empty description
        payload.write(imageBytes)

        writeFrame(stream, "APIC", payload.toByteArray())
    }

    private fun writeFrame(stream: ByteArrayOutputStream, frameId: String, data: ByteArray) {
        stream.write(frameId.toByteArray(StandardCharsets.ISO_8859_1))
        // 4 bytes frame size (ID3v2.3: 32-bit int, big-endian)
        val size = data.size
        stream.write((size shr 24) and 0xFF)
        stream.write((size shr 16) and 0xFF)
        stream.write((size shr 8) and 0xFF)
        stream.write(size and 0xFF)
        // 2 bytes flags (0)
        stream.write(0)
        stream.write(0)
        // Frame data
        stream.write(data)
    }

    // =========================================================================
    // FLAC 实现
    // =========================================================================

    private fun embedFlac(
        file: File,
        title: String,
        artist: String,
        album: String,
        lyrics: String?,
        coverBytes: ByteArray?
    ): Boolean {
        // 对于 FLAC，Vorbis Comment 支持 TITLE, ARTIST, ALBUM, LYRICS 等键值对
        // 以及 PICTURE 块。构造标准 FLAC METADATA_BLOCK:
        val fis = FileInputStream(file)
        val header = ByteArray(4)
        if (fis.read(header) != 4 || String(header, StandardCharsets.ISO_8859_1) != "fLaC") {
            fis.close()
            return false
        }

        // 读取所有现有的 metadata blocks，保留 STREAMINFO（必须是第一个），跳过旧的 VORBIS_COMMENT 和 PICTURE
        var streamInfoBlock: ByteArray? = null
        val preservedBlocks = mutableListOf<ByteArray>()

        while (true) {
            val blockHeader = ByteArray(4)
            if (fis.read(blockHeader) != 4) break
            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            val blockData = ByteArray(length)
            fis.read(blockData)

            if (blockType == 0) { // STREAMINFO
                streamInfoBlock = blockData
            } else if (blockType != 4 && blockType != 6) { // 保留除 vorbis comment 和 picture 之外的其他块
                val raw = ByteArray(4 + length)
                System.arraycopy(blockHeader, 0, raw, 0, 4)
                raw[0] = (blockType and 0x7F).toByte() // 暂时去掉 isLast
                System.arraycopy(blockData, 0, raw, 4, length)
                preservedBlocks.add(raw)
            }

            if (isLast) break
        }

        if (streamInfoBlock == null) {
            fis.close()
            return false
        }

        // 构造新的 VORBIS_COMMENT
        val comments = mutableListOf<String>()
        if (title.isNotBlank()) comments.add("TITLE=$title")
        if (artist.isNotBlank()) comments.add("ARTIST=$artist")
        if (album.isNotBlank()) comments.add("ALBUM=$album")
        if (!lyrics.isNullOrBlank()) comments.add("LYRICS=$lyrics")

        val vcStream = ByteArrayOutputStream()
        val vendor = "Jussichords FLAC TagWriter".toByteArray(StandardCharsets.UTF_8)
        writeLittleEndian32(vcStream, vendor.size)
        vcStream.write(vendor)
        writeLittleEndian32(vcStream, comments.size)
        for (c in comments) {
            val cb = c.toByteArray(StandardCharsets.UTF_8)
            writeLittleEndian32(vcStream, cb.size)
            vcStream.write(cb)
        }
        val vcData = vcStream.toByteArray()

        // 构造新的 PICTURE block (type 6)
        var picBlockData: ByteArray? = null
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val mimeType = if (coverBytes.size > 8 && coverBytes[0] == 0x89.toByte() && coverBytes[1] == 'P'.code.toByte()) {
                "image/png"
            } else {
                "image/jpeg"
            }
            val picStream = ByteArrayOutputStream()
            writeBigEndian32(picStream, 3) // Picture type: Cover front
            val mimeBytes = mimeType.toByteArray(StandardCharsets.ISO_8859_1)
            writeBigEndian32(picStream, mimeBytes.size)
            picStream.write(mimeBytes)
            writeBigEndian32(picStream, 0) // Description length = 0
            writeBigEndian32(picStream, 0) // Width
            writeBigEndian32(picStream, 0) // Height
            writeBigEndian32(picStream, 24) // Color depth
            writeBigEndian32(picStream, 0) // Colors indexed
            writeBigEndian32(picStream, coverBytes.size)
            picStream.write(coverBytes)
            picBlockData = picStream.toByteArray()
        }

        // 组装所有 blocks
        val allBlocks = mutableListOf<Pair<Int, ByteArray>>()
        allBlocks.add(Pair(0, streamInfoBlock)) // STREAMINFO
        for (pb in preservedBlocks) {
            val type = pb[0].toInt() and 0x7F
            val data = pb.copyOfRange(4, pb.size)
            allBlocks.add(Pair(type, data))
        }
        allBlocks.add(Pair(4, vcData)) // VORBIS_COMMENT
        if (picBlockData != null) {
            allBlocks.add(Pair(6, picBlockData)) // PICTURE
        }

        val tempOut = File(file.parentFile, "${file.name}.flac_tagged")
        FileOutputStream(tempOut).use { fos ->
            fos.write("fLaC".toByteArray(StandardCharsets.ISO_8859_1))
            for (i in allBlocks.indices) {
                val isLast = (i == allBlocks.size - 1)
                val type = allBlocks[i].first
                val data = allBlocks[i].second
                val headerByte0 = (if (isLast) 0x80 else 0) or (type and 0x7F)
                fos.write(headerByte0)
                fos.write((data.size shr 16) and 0xFF)
                fos.write((data.size shr 8) and 0xFF)
                fos.write(data.size and 0xFF)
                fos.write(data)
            }

            // 拷贝音频流
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
            }
        }
        fis.close()

        return if (tempOut.length() > 0) {
            file.delete()
            tempOut.renameTo(file)
        } else {
            tempOut.delete()
            false
        }
    }

    private fun writeLittleEndian32(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }

    private fun writeBigEndian32(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value shr 24) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }
}
