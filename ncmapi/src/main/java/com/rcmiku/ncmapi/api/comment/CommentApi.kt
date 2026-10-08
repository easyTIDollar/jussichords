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
        cursor: String? = null
    ): Result<CommentNewResponse> {
        val params = mutableMapOf<String, Any>(
            "id" to id,
            "type" to type,
            "pageNo" to pageNo,
            "pageSize" to pageSize,
            "sortType" to sortType
        )
        if (pageNo > 1 && !cursor.isNullOrBlank()) {
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

    /**
     * 发送评论（t=1 发送）。路由 /comment/add → eapi /api/resource/comments/add（osx 写信封）。
     * 需登录；未登录返回 failure。成功以 code==200 判定。
     */
    suspend fun postComment(
        id: Long,
        type: Int,
        content: String,
        commentId: Long? = null
    ): Result<ApiCodeResponse> {
        if (!CookieProvider.isLoggedIn()) {
            return Result.failure(IllegalStateException("请先登录后再发表评论"))
        }
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalStateException("评论内容不能为空"))
        }
        val route = if (commentId != null && commentId > 0) "/comment/reply" else "/comment/add"
        val params = mutableMapOf<String, Any>(
            "id" to id,
            "type" to type,
            "content" to trimmed
        )
        commentId?.let { params["cid"] = it }
        return apiGet<ApiCodeResponse>(route, params).mapCatching {
            if (it.code == 200) {
                it
            } else {
                throw IllegalStateException(it.message ?: it.msg ?: "发表评论失败 code ${it.code}")
            }
        }
    }

    /**
     * 删除评论（t=0 删除）。路由 /comment/delete → eapi /api/resource/comments/delete（osx 写信封）。
     * 仅能删本人评论；需登录。成功以 code==200 判定。
     */
    suspend fun deleteComment(
        id: Long,
        type: Int,
        cid: Long
    ): Result<ApiCodeResponse> {
        if (!CookieProvider.isLoggedIn()) {
            return Result.failure(IllegalStateException("请先登录后再删除评论"))
        }
        return apiGet<ApiCodeResponse>(
            "/comment/delete",
            mapOf("id" to id, "type" to type, "cid" to cid)
        ).mapCatching {
            if (it.code == 200) {
                it
            } else {
                throw IllegalStateException(it.message ?: it.msg ?: "删除评论失败 code ${it.code}")
            }
        }
    }
}
