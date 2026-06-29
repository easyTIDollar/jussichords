package com.rcmiku.ncmapi.utils

import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
