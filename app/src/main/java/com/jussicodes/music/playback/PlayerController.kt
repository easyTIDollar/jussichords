package com.jussicodes.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jussicodes.music.JetMeloApp
import com.jussicodes.music.utils.SongListUtil
import com.rcmiku.ncmapi.utils.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
object PlayerController {
    private lateinit var appContext: Context
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var directoriesReady: Deferred<Unit>? = null
    var controller by mutableStateOf<MediaController?>(null)
        private set

    private val sessionToken: SessionToken by lazy {
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    }

    fun init(context: Context) {
        withController(context) { }
    }

    /** 主动操作立即连接，整组队列与播放命令等连接成功后在主线程执行。 */
    fun withController(
        context: Context = JetMeloApp.context,
        block: suspend (MediaController) -> Unit
    ) {
        scope.launch {
            try {
                block(awaitController(context))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlayerController", "播放服务连接或操作失败", error)
                Toast.makeText(context.applicationContext, "播放服务连接或操作失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 不阻塞主线程，也不因单个等待者取消而取消共享连接。 */
    suspend fun awaitController(context: Context = JetMeloApp.context): MediaController = withContext(Dispatchers.Main.immediate) {
        attachContext(context)

        // 复用原有的后台目录准备；提前点击播放也必须等目录可用，避免丢失队列保存。
        val preparation = directoriesReady ?: scope.async(Dispatchers.IO) {
            FileProvider.init(appContext.cacheDir.resolve("ncm"))
            SongListUtil.init(appContext.filesDir.resolve("playlist"))
        }.also { directoriesReady = it }
        preparation.await()

        controller?.let { return@withContext it }

        val future = controllerFuture
            ?: MediaController.Builder(appContext, sessionToken).buildAsync()
                .also { controllerFuture = it }
        try {
            suspendCancellableCoroutine<MediaController> { continuation ->
                future.addListener(
                    {
                        if (continuation.isActive) {
                            try {
                                continuation.resume(future.get().also {
                                    controller = it
                                })
                            } catch (error: Exception) {
                                continuation.resumeWithException(error)
                            }
                        }
                    },
                    ContextCompat.getMainExecutor(appContext)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // 失败后允许下一次主动操作重试，不永久复用失败的 future。
            if (controllerFuture === future) {
                controllerFuture = null
            }
            throw error
        }
    }

    private fun attachContext(context: Context) {
        if (!this::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }
}

