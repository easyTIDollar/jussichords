package com.jussicodes.music.lyric.client

import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

/**
 * QQ 音乐 QRC 歌词解密工具
 * Triple-DES 解密 + Zlib Inflate 解压
 */
internal object QqLyricDecoder {
    private enum class DESMode {
        DES_ENCRYPT,
        DES_DECRYPT
    }

    private fun bitNum(a: ByteArray, b: Int, c: Int): Long {
        val byteIndex = (b / 32) * 4 + 3 - (b % 32) / 8
        val bitPosition = 7 - (b % 8)
        val extractedBit = (a[byteIndex].toUInt() shr bitPosition) and 0x01u
        return (extractedBit shl c).toLong()
    }

    private fun bitNumIntR(a: Long, b: Int, c: Int): Long {
        val extractedBit = (a shr (31 - b)) and 0x00000001
        return extractedBit shl c
    }

    private fun bitNumIntL(a: Long, b: Int, c: Int): Long {
        val extractedBit = (a shl b) and 0x80000000
        return extractedBit shr c
    }

    private fun sBoxBit(a: Long): Long {
        val part1 = a and 0x20
        val part2 = (a and 0x1f) shr 1
        val part3 = (a and 0x01) shl 4
        return part1 or part2 or part3
    }

    private val sBox1 = intArrayOf(
        14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
        0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
        4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
        15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13
    )

    private val sBox2 = intArrayOf(
        15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
        3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
        0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
        13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9
    )

    private val sBox3 = intArrayOf(
        10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
        13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
        13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
        1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12
    )

    private val sBox4 = intArrayOf(
        7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
        13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
        10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
        3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14
    )

    private val sBox5 = intArrayOf(
        2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
        14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
        4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
        11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3
    )

    private val sBox6 = intArrayOf(
        12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
        10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
        9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
        4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13
    )

    private val sBox7 = intArrayOf(
        4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
        13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
        1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
        6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12
    )

    private val sBox8 = intArrayOf(
        13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
        1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
        7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
        2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11
    )

    private fun ip(state: LongArray, inBytes: ByteArray): LongArray {
        state[0] = (
            bitNum(inBytes, 57, 31) or bitNum(inBytes, 49, 30) or bitNum(inBytes, 41, 29) or
                bitNum(inBytes, 33, 28) or bitNum(inBytes, 25, 27) or bitNum(inBytes, 17, 26) or
                bitNum(inBytes, 9, 25) or bitNum(inBytes, 1, 24) or bitNum(inBytes, 59, 23) or
                bitNum(inBytes, 51, 22) or bitNum(inBytes, 43, 21) or bitNum(inBytes, 35, 20) or
                bitNum(inBytes, 27, 19) or bitNum(inBytes, 19, 18) or bitNum(inBytes, 11, 17) or
                bitNum(inBytes, 3, 16) or bitNum(inBytes, 61, 15) or bitNum(inBytes, 53, 14) or
                bitNum(inBytes, 45, 13) or bitNum(inBytes, 37, 12) or bitNum(inBytes, 29, 11) or
                bitNum(inBytes, 21, 10) or bitNum(inBytes, 13, 9) or bitNum(inBytes, 5, 8) or
                bitNum(inBytes, 63, 7) or bitNum(inBytes, 55, 6) or bitNum(inBytes, 47, 5) or
                bitNum(inBytes, 39, 4) or bitNum(inBytes, 31, 3) or bitNum(inBytes, 23, 2) or
                bitNum(inBytes, 15, 1) or bitNum(inBytes, 7, 0)
        )
        state[1] = (
            bitNum(inBytes, 56, 31) or bitNum(inBytes, 48, 30) or bitNum(inBytes, 40, 29) or
                bitNum(inBytes, 32, 28) or bitNum(inBytes, 24, 27) or bitNum(inBytes, 16, 26) or
                bitNum(inBytes, 8, 25) or bitNum(inBytes, 0, 24) or bitNum(inBytes, 58, 23) or
                bitNum(inBytes, 50, 22) or bitNum(inBytes, 42, 21) or bitNum(inBytes, 34, 20) or
                bitNum(inBytes, 26, 19) or bitNum(inBytes, 18, 18) or bitNum(inBytes, 10, 17) or
                bitNum(inBytes, 2, 16) or bitNum(inBytes, 60, 15) or bitNum(inBytes, 52, 14) or
                bitNum(inBytes, 44, 13) or bitNum(inBytes, 36, 12) or bitNum(inBytes, 28, 11) or
                bitNum(inBytes, 20, 10) or bitNum(inBytes, 12, 9) or bitNum(inBytes, 4, 8) or
                bitNum(inBytes, 62, 7) or bitNum(inBytes, 54, 6) or bitNum(inBytes, 46, 5) or
                bitNum(inBytes, 38, 4) or bitNum(inBytes, 30, 3) or bitNum(inBytes, 22, 2) or
                bitNum(inBytes, 14, 1) or bitNum(inBytes, 6, 0)
        )
        return state
    }

    private fun f(r: Long, k: LongArray): Long {
        val s = LongArray(8)
        var tr = (
            bitNumIntR(r, 0, 5) or bitNumIntR(r, 1, 4) or bitNumIntR(r, 2, 3) or
                bitNumIntR(r, 3, 2) or bitNumIntR(r, 4, 1) or bitNumIntR(r, 5, 0) or
                bitNumIntR(r, 4, 11) or bitNumIntR(r, 5, 10) or bitNumIntR(r, 6, 9) or
                bitNumIntR(r, 7, 8) or bitNumIntR(r, 8, 7) or bitNumIntR(r, 9, 6) or
                bitNumIntR(r, 8, 17) or bitNumIntR(r, 9, 16) or bitNumIntR(r, 10, 15) or
                bitNumIntR(r, 11, 14) or bitNumIntR(r, 12, 13) or bitNumIntR(r, 13, 12) or
                bitNumIntR(r, 12, 23) or bitNumIntR(r, 13, 22) or bitNumIntR(r, 14, 21) or
                bitNumIntR(r, 15, 20) or bitNumIntR(r, 16, 19) or bitNumIntR(r, 17, 18) or
                bitNumIntR(r, 16, 29) or bitNumIntR(r, 17, 28) or bitNumIntR(r, 18, 27) or
                bitNumIntR(r, 19, 26) or bitNumIntR(r, 20, 25) or bitNumIntR(r, 21, 24) or
                bitNumIntR(r, 31, 31)
        ) xor (k[0] or (k[1] shl 8) or (k[2] shl 16) or (k[3] shl 24))

        s[0] = sBoxBit(tr).let { sBox1[it.toInt()].toLong() }
        s[1] = sBoxBit(tr shr 6).let { sBox2[it.toInt()].toLong() }
        s[2] = sBoxBit(tr shr 12).let { sBox3[it.toInt()].toLong() }
        s[3] = sBoxBit(tr shr 18).let { sBox4[it.toInt()].toLong() }
        s[4] = sBoxBit(tr shr 24).let { sBox5[it.toInt()].toLong() }

        tr = (
            bitNumIntR(r, 20, 1) or bitNumIntR(r, 21, 0) or bitNumIntR(r, 20, 7) or
                bitNumIntR(r, 21, 6) or bitNumIntR(r, 22, 5) or bitNumIntR(r, 23, 4) or
                bitNumIntR(r, 24, 3) or bitNumIntR(r, 25, 2) or bitNumIntR(r, 24, 13) or
                bitNumIntR(r, 25, 12) or bitNumIntR(r, 26, 11) or bitNumIntR(r, 27, 10) or
                bitNumIntR(r, 28, 9) or bitNumIntR(r, 29, 8) or bitNumIntR(r, 28, 19) or
                bitNumIntR(r, 29, 18) or bitNumIntR(r, 30, 17) or bitNumIntR(r, 31, 16) or
                bitNumIntR(r, 0, 15) or bitNumIntR(r, 1, 14)
        ) xor ((k[3] shr 8) or (k[4] shl 2) or (k[5] shl 10))

        s[5] = sBoxBit(tr).let { sBox6[it.toInt()].toLong() }
        s[6] = sBoxBit(tr shr 6).let { sBox7[it.toInt()].toLong() }
        s[7] = sBoxBit(tr shr 12).let { sBox8[it.toInt()].toLong() }

        return (
            bitNumIntL(s[0], 28, 16) or bitNumIntL(s[0], 29, 25) or bitNumIntL(s[0], 30, 12) or
                bitNumIntL(s[0], 31, 21) or bitNumIntL(s[1], 28, 25) or bitNumIntL(s[1], 29, 17) or
                bitNumIntL(s[1], 30, 24) or bitNumIntL(s[1], 31, 8) or bitNumIntL(s[2], 28, 27) or
                bitNumIntL(s[2], 29, 11) or bitNumIntL(s[2], 30, 15) or bitNumIntL(s[2], 31, 19) or
                bitNumIntL(s[3], 28, 23) or bitNumIntL(s[3], 29, 10) or bitNumIntL(s[3], 30, 2) or
                bitNumIntL(s[3], 31, 18) or bitNumIntL(s[4], 28, 4) or bitNumIntL(s[4], 29, 7) or
                bitNumIntL(s[4], 30, 14) or bitNumIntL(s[4], 31, 29) or bitNumIntL(s[5], 28, 9) or
                bitNumIntL(s[5], 29, 1) or bitNumIntL(s[5], 30, 22) or bitNumIntL(s[5], 31, 30) or
                bitNumIntL(s[6], 28, 6) or bitNumIntL(s[6], 29, 26) or bitNumIntL(s[6], 30, 13) or
                bitNumIntL(s[6], 31, 3) or bitNumIntL(s[7], 28, 20) or bitNumIntL(s[7], 29, 0) or
                bitNumIntL(s[7], 30, 5) or bitNumIntL(s[7], 31, 28)
        )
    }

    private fun fp(state: LongArray, out: ByteArray) {
        val st0 = state[0]
        val st1 = state[1]
        val fpTable = (
            bitNumIntR(st1, 0, 31) or bitNumIntR(st0, 0, 30) or bitNumIntR(st1, 1, 29) or
                bitNumIntR(st0, 1, 28) or bitNumIntR(st1, 2, 27) or bitNumIntR(st0, 2, 26) or
                bitNumIntR(st1, 3, 25) or bitNumIntR(st0, 3, 24) or bitNumIntR(st1, 4, 23) or
                bitNumIntR(st0, 4, 22) or bitNumIntR(st1, 5, 21) or bitNumIntR(st0, 5, 20) or
                bitNumIntR(st1, 6, 19) or bitNumIntR(st0, 6, 18) or bitNumIntR(st1, 7, 17) or
                bitNumIntR(st0, 7, 16) or bitNumIntR(st1, 8, 15) or bitNumIntR(st0, 8, 14) or
                bitNumIntR(st1, 9, 13) or bitNumIntR(st0, 9, 12) or bitNumIntR(st1, 10, 11) or
                bitNumIntR(st0, 10, 10) or bitNumIntR(st1, 11, 9) or bitNumIntR(st0, 11, 8) or
                bitNumIntR(st1, 12, 7) or bitNumIntR(st0, 12, 6) or bitNumIntR(st1, 13, 5) or
                bitNumIntR(st0, 13, 4) or bitNumIntR(st1, 14, 3) or bitNumIntR(st0, 14, 2) or
                bitNumIntR(st1, 15, 1) or bitNumIntR(st0, 15, 0)
        )
        out[3] = ((fpTable shr 24) and 0xff).toByte()
        out[2] = ((fpTable shr 16) and 0xff).toByte()
        out[1] = ((fpTable shr 8) and 0xff).toByte()
        out[0] = (fpTable and 0xff).toByte()

        val fpTable2 = (
            bitNumIntR(st1, 16, 31) or bitNumIntR(st0, 16, 30) or bitNumIntR(st1, 17, 29) or
                bitNumIntR(st0, 17, 28) or bitNumIntR(st1, 18, 27) or bitNumIntR(st0, 18, 26) or
                bitNumIntR(st1, 19, 25) or bitNumIntR(st0, 19, 24) or bitNumIntR(st1, 20, 23) or
                bitNumIntR(st0, 20, 22) or bitNumIntR(st1, 21, 21) or bitNumIntR(st0, 21, 20) or
                bitNumIntR(st1, 22, 19) or bitNumIntR(st0, 22, 18) or bitNumIntR(st1, 23, 17) or
                bitNumIntR(st0, 23, 16) or bitNumIntR(st1, 24, 15) or bitNumIntR(st0, 24, 14) or
                bitNumIntR(st1, 25, 13) or bitNumIntR(st0, 25, 12) or bitNumIntR(st1, 26, 11) or
                bitNumIntR(st0, 26, 10) or bitNumIntR(st1, 27, 9) or bitNumIntR(st0, 27, 8) or
                bitNumIntR(st1, 28, 7) or bitNumIntR(st0, 28, 6) or bitNumIntR(st1, 29, 5) or
                bitNumIntR(st0, 29, 4) or bitNumIntR(st1, 30, 3) or bitNumIntR(st0, 30, 2) or
                bitNumIntR(st1, 31, 1) or bitNumIntR(st0, 31, 0)
        )
        out[7] = ((fpTable2 shr 24) and 0xff).toByte()
        out[6] = ((fpTable2 shr 16) and 0xff).toByte()
        out[5] = ((fpTable2 shr 8) and 0xff).toByte()
        out[4] = (fpTable2 and 0xff).toByte()
    }

    private fun desKeySetup(key: ByteArray, schedule: Array<LongArray>, mode: DESMode) {
        val c = LongArray(17)
        val d = LongArray(17)
        c[0] = (
            bitNum(key, 56, 27) or bitNum(key, 48, 26) or bitNum(key, 40, 25) or
                bitNum(key, 32, 24) or bitNum(key, 24, 23) or bitNum(key, 16, 22) or
                bitNum(key, 8, 21) or bitNum(key, 0, 20) or bitNum(key, 57, 19) or
                bitNum(key, 49, 18) or bitNum(key, 41, 17) or bitNum(key, 33, 16) or
                bitNum(key, 25, 15) or bitNum(key, 17, 14) or bitNum(key, 9, 13) or
                bitNum(key, 1, 12) or bitNum(key, 58, 11) or bitNum(key, 50, 10) or
                bitNum(key, 42, 9) or bitNum(key, 34, 8) or bitNum(key, 26, 7) or
                bitNum(key, 18, 6) or bitNum(key, 10, 5) or bitNum(key, 2, 4) or
                bitNum(key, 59, 3) or bitNum(key, 51, 2) or bitNum(key, 43, 1) or
                bitNum(key, 35, 0)
        )
        d[0] = (
            bitNum(key, 62, 27) or bitNum(key, 54, 26) or bitNum(key, 46, 25) or
                bitNum(key, 38, 24) or bitNum(key, 30, 23) or bitNum(key, 22, 22) or
                bitNum(key, 14, 21) or bitNum(key, 6, 20) or bitNum(key, 61, 19) or
                bitNum(key, 53, 18) or bitNum(key, 45, 17) or bitNum(key, 37, 16) or
                bitNum(key, 29, 15) or bitNum(key, 21, 14) or bitNum(key, 13, 13) or
                bitNum(key, 5, 12) or bitNum(key, 60, 11) or bitNum(key, 52, 10) or
                bitNum(key, 44, 9) or bitNum(key, 36, 8) or bitNum(key, 28, 7) or
                bitNum(key, 20, 6) or bitNum(key, 12, 5) or bitNum(key, 4, 4) or
                bitNum(key, 27, 3) or bitNum(key, 19, 2) or bitNum(key, 11, 1) or
                bitNum(key, 3, 0)
        )

        for (i in 1..16) {
            val shift = if (i == 1 || i == 2 || i == 9 || i == 16) 1 else 2
            c[i] = ((c[i - 1] shl shift) and 0x0fffffff) or (c[i - 1] shr (28 - shift))
            d[i] = ((d[i - 1] shl shift) and 0x0fffffff) or (d[i - 1] shr (28 - shift))

            val round = if (mode == DESMode.DES_ENCRYPT) i - 1 else 16 - i
            val ci = c[i]
            val di = d[i]

            schedule[round][0] = (
                bitNumIntR(ci, 18, 7) or bitNumIntR(ci, 15, 6) or bitNumIntR(ci, 20, 5) or
                    bitNumIntR(ci, 24, 4) or bitNumIntR(ci, 17, 3) or bitNumIntR(ci, 27, 2) or
                    bitNumIntR(ci, 14, 1) or bitNumIntR(ci, 23, 0)
            )
            schedule[round][1] = (
                bitNumIntR(ci, 19, 7) or bitNumIntR(ci, 16, 6) or bitNumIntR(ci, 26, 5) or
                    bitNumIntR(ci, 12, 4) or bitNumIntR(ci, 25, 3) or bitNumIntR(ci, 21, 2) or
                    bitNumIntR(ci, 22, 1) or bitNumIntR(ci, 7, 0)
            )
            schedule[round][2] = (
                bitNumIntR(ci, 4, 7) or bitNumIntR(ci, 9, 6) or bitNumIntR(ci, 8, 5) or
                    bitNumIntR(ci, 0, 4) or bitNumIntR(ci, 5, 3) or bitNumIntR(ci, 10, 2) or
                    bitNumIntR(ci, 1, 1) or bitNumIntR(ci, 13, 0)
            )
            schedule[round][3] = (
                bitNumIntR(ci, 3, 7) or bitNumIntR(ci, 11, 6) or bitNumIntR(ci, 6, 5) or
                    bitNumIntR(ci, 2, 4) or bitNumIntR(di, 24, 3) or bitNumIntR(di, 19, 2) or
                    bitNumIntR(di, 15, 1) or bitNumIntR(di, 27, 0)
            )
            schedule[round][4] = (
                bitNumIntR(di, 14, 7) or bitNumIntR(di, 21, 6) or bitNumIntR(di, 25, 5) or
                    bitNumIntR(di, 17, 4) or bitNumIntR(di, 23, 3) or bitNumIntR(di, 9, 2) or
                    bitNumIntR(di, 18, 1) or bitNumIntR(di, 11, 0)
            )
            schedule[round][5] = (
                bitNumIntR(di, 5, 7) or bitNumIntR(di, 22, 6) or bitNumIntR(di, 20, 5) or
                    bitNumIntR(di, 10, 4) or bitNumIntR(di, 7, 3) or bitNumIntR(di, 16, 2) or
                    bitNumIntR(di, 26, 1) or bitNumIntR(di, 13, 0)
            )
        }
    }

    private fun desCrypt(inBytes: ByteArray, schedule: Array<LongArray>): ByteArray {
        val state = LongArray(2)
        ip(state, inBytes)

        for (i in 0..14 step 2) {
            state[0] = state[0] xor f(state[1], schedule[i])
            state[1] = state[1] xor f(state[0], schedule[i + 1])
        }
        state[0] = state[0] xor f(state[1], schedule[15])

        val out = ByteArray(8)
        fp(state, out)
        return out
    }

    private val KEY1 = "!@#)(*$%123ZXC!@!@#)(*$%123ZXC!".toByteArray(Charsets.US_ASCII)
    private val KEY2 = "!@#)(*$%123ZXC!@!@#)(*$%123ZXC!".toByteArray(Charsets.US_ASCII)
    private val KEY3 = intArrayOf(
        0x23, 0x74, 0x6d, 0x65, 0x6d, 0x75, 0x73, 0x69,
        0x63, 0x30, 0x32, 0x37, 0x32, 0x62, 0x37, 0x33,
        0x62, 0x23
    ).map { it.toByte() }.toByteArray()

    private fun funcDes(buff: ByteArray, key: ByteArray, length: Int): ByteArray {
        val schedule = Array(16) { LongArray(6) }
        desKeySetup(key, schedule, DESMode.DES_ENCRYPT)
        val output = ByteArray((length + 7) / 8 * 8)
        for (i in 0 until length step 8) {
            val block = buff.copyOfRange(i, minOf(i + 8, length))
            val paddedBlock = if (block.size < 8) block.copyOf(8) else block
            desCrypt(paddedBlock, schedule).copyInto(output, i)
        }
        return output
    }

    private fun funcDdes(buff: ByteArray, key: ByteArray, length: Int): ByteArray {
        val schedule = Array(16) { LongArray(6) }
        desKeySetup(key, schedule, DESMode.DES_DECRYPT)
        val output = ByteArray((length + 7) / 8 * 8)
        for (i in 0 until length step 8) {
            val block = buff.copyOfRange(i, minOf(i + 8, length))
            val paddedBlock = if (block.size < 8) block.copyOf(8) else block
            desCrypt(paddedBlock, schedule).copyInto(output, i)
        }
        return output
    }

    private fun lyricDecode(data: ByteArray, length: Int): ByteArray {
        var content = funcDdes(data, KEY1, length)
        content = funcDes(content, KEY2, length)
        content = funcDdes(content, KEY3, length)
        return content
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        return ByteArray(s.length / 2) { i ->
            Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16).toByte()
        }
    }

    fun decodeLyric(data: String): String {
        if (data.isEmpty() || data.length > 1024 * 1024 || data.length % 16 != 0) return ""
        return try {
            val bytes = hexStringToByteArray(data)
            val decrypted = lyricDecode(bytes, bytes.size)
            InflaterInputStream(ByteArrayInputStream(decrypted)).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 2 * 1024 * 1024) return ""
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }
}
