package com.rcmiku.ncmapi.api.radio

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object RadioApi {
    suspend fun radioInfo(radioId: Long): Result<RadioInfoResponse> =
        apiGet("/dj/detail", mapOf("rid" to radioId))

    suspend fun programRadio(radioId: Long, limit: Int, offset: Int): Result<ProgramRadioResponse> =
        apiGet("/dj/program", mapOf("rid" to radioId, "limit" to limit, "offset" to offset))
}
