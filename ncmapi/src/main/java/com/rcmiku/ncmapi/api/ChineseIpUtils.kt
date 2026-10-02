package com.rcmiku.ncmapi.api

import java.util.Random

/**
 * 中国 IP 生成器（复刻自 MeiloX com.ljyh.mei.utils.netease.ChineseIpUtils，去 timber 保持框架无关）。
 * 一起听 eapi 写共享队列（/sync/list/command/report）走 X-Real-IP/X-Forwarded-For 自报国内 IP，
 * NCM 按此信任放行写入；MeiloX 会话稳定 lazy 生成一次，这里同形——顶层 val 调一次即稳定。
 */
object ChineseIpUtils {

    private data class IpRange(
        val start: Long,
        val end: Long,
        val count: Long,
        val location: String
    )

    data class RawIpRange(
        val startIp: String,
        val endIp: String,
        val count: Long,
        val city: String
    )

    private val chinaIPRangesRaw = listOf(
        RawIpRange("1.0.1.0", "1.0.3.255", 768, "福州"),
        RawIpRange("1.0.8.0", "1.0.15.255", 2048, "广州"),
        RawIpRange("1.0.32.0", "1.0.63.255", 8192, "广州"),
        RawIpRange("1.1.0.0", "1.1.0.255", 256, "福州"),
        RawIpRange("1.1.2.0", "1.1.63.255", 15872, "广州"),
        RawIpRange("1.2.0.0", "1.2.2.255", 768, "北京"),
        RawIpRange("1.2.4.0", "1.2.127.255", 31744, "广州"),
        RawIpRange("1.3.0.0", "1.3.255.255", 65536, "广州"),
        RawIpRange("1.4.1.0", "1.4.127.255", 32512, "广州"),
        RawIpRange("1.8.0.0", "1.8.255.255", 65536, "北京"),
        RawIpRange("1.10.0.0", "1.10.9.255", 2560, "福州"),
        RawIpRange("1.10.11.0", "1.10.127.255", 29952, "广州"),
        RawIpRange("1.12.0.0", "1.15.255.255", 262144, "上海"),
        RawIpRange("1.18.128.0", "1.18.128.255", 256, "北京"),
        RawIpRange("1.24.0.0", "1.31.255.255", 524288, "赤峰"),
        RawIpRange("1.45.0.0", "1.45.255.255", 65536, "北京"),
        RawIpRange("1.48.0.0", "1.51.255.255", 262144, "济南"),
        RawIpRange("1.56.0.0", "1.63.255.255", 524288, "伊春"),
        RawIpRange("1.68.0.0", "1.71.255.255", 262144, "忻州"),
        RawIpRange("1.80.0.0", "1.95.255.255", 1048576, "北京"),
        RawIpRange("1.116.0.0", "1.117.255.255", 131072, "上海"),
        RawIpRange("1.119.0.0", "1.119.255.255", 65536, "北京"),
        RawIpRange("1.180.0.0", "1.185.255.255", 393216, "桂林"),
        RawIpRange("1.188.0.0", "1.199.255.255", 786432, "洛阳"),
        RawIpRange("1.202.0.0", "1.207.255.255", 393216, "铜仁")
    )

    private val rangeList: List<IpRange>
    private val totalCount: Long
    private val random = Random()

    init {
        val list = mutableListOf<IpRange>()
        var sum = 0L
        for (row in chinaIPRangesRaw) {
            val startLong = ipToLong(row.startIp)
            val endLong = ipToLong(row.endIp)
            list.add(IpRange(startLong, endLong, row.count, row.city))
            sum += row.count
        }
        rangeList = list
        totalCount = sum
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".").map { it.toLong() }
        if (parts.size != 4) return 0L
        return (parts[0] shl 24) + (parts[1] shl 16) + (parts[2] shl 8) + parts[3]
    }

    private fun longToIp(value: Long): String {
        return buildString {
            append((value ushr 24) and 0xFF)
            append(".")
            append((value ushr 16) and 0xFF)
            append(".")
            append((value ushr 8) and 0xFF)
            append(".")
            append(value and 0xFF)
        }
    }

    private fun getRandomInt(min: Int, max: Int): Int {
        return random.nextInt(max - min + 1) + min
    }

    /** 会话内随机取一个中国 IP（调用方保证只算一次即稳定）。 */
    fun generateRandomChineseIP(): String {
        if (totalCount == 0L) {
            return "116.${getRandomInt(25, 94)}.${getRandomInt(1, 255)}.${getRandomInt(1, 255)}"
        }
        var offset = (random.nextDouble() * totalCount).toLong()
        var chosen: IpRange? = null
        for (range in rangeList) {
            if (offset < range.count) {
                chosen = range
                break
            }
            offset -= range.count
        }
        if (chosen == null) chosen = rangeList.last()
        val segSize = chosen.end - chosen.start + 1
        val randomOffsetInSeg = (random.nextDouble() * segSize).toLong()
        val ipLong = chosen.start + randomOffsetInSeg
        return longToIp(ipLong)
    }
}
