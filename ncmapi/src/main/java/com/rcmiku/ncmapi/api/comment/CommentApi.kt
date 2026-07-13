package com.rcmiku.ncmapi.api.comment

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.ApiCodeResponse
import com.rcmiku.ncmapi.model.CommentNewResponse
import com.rcmiku.ncmapi.utils.CookieProvider

object CommentApi {
    suspend fun newComments(
        id: Long,
        type: Int = 0,
        pageNo: Int = 1,
        pageSize: Int = 20,
        sortType: Int = 3,
        cursor: Long? = null
    ): Result<CommentNewResponse> {
        val params = mutableMapOf<String, Any>(
            "id" to id,
            "type" to type,
            "pageNo" to pageNo,
            "pageSize" to pageSize,
            "sortType" to sortType
        )
        if (sortType == 3 && pageNo > 1 && cursor != null) {
            params["cursor"] = cursor
        }
        return apiGet("/comment/new", params)
    }

    suspend fun likeComment(
        id: Long,
        cid: Long,
        like: Boolean,
        type: Int = 0
    ): Result<ApiCodeResponse> {
        if (!CookieProvider.isLoggedIn()) {
            return Result.failure(IllegalStateException("请先登录后再点赞评论"))
        }
        val params = mutableMapOf<String, Any>(
            "id" to id,
            "cid" to cid,
            "t" to if (like) 1 else 0,
            "type" to type,
            "randomCNIP" to false
        )
        CookieProvider.getCookieMap()["__csrf"]?.takeIf { it.isNotBlank() }?.let {
            params["csrf_token"] = it
        }
        return apiGet<ApiCodeResponse>(
            "/comment/like",
            params
        ).mapCatching {
            if (it.code == 200) {
                it
            } else {
                throw IllegalStateException(it.message ?: it.msg ?: "Comment like failed with code ${it.code}")
            }
        }
    }
}
