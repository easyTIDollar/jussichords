package com.rcmiku.ncmapi.utils

fun base64Encode(data: ByteArray): String {
    return java.util.Base64.getEncoder().encodeToString(data)
}
