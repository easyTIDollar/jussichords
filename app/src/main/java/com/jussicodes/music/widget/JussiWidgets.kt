package com.jussicodes.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceId
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jussicodes.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class WidgetStyle { CLASSIC, SQUARE, CAPSULE }

/**
 * 三款组件共用一套组合逻辑：快照 + 红心（含乐观覆盖）+ 封面位图。
 * 播放态由 WidgetUpdater 统一推进，任何一款变化都会一起重绘。
 */
abstract class BaseMusicWidget(
    private val style: WidgetStyle,
    private val coverStyle: CoverStyle,
    private val cornerRatio: Float,
    private val sizes: SizeMode,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = sizes

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 进程内实时帧优先：播放服务还活着时它一定是最新状态，不依赖落盘
        val snapshot = WidgetUpdater.current(context)
        // 合成跑到这里就说明桌面上一定有实例：把「有人用」的判定钉死，
        // 别因为某些 ROM 上 AppWidgetManager 查询为空而把整条刷新链路掐掉。
        WidgetUpdater.markAlive()
        WidgetDebug.event(
            context,
            "compose id=${id.hashCode()} song=${snapshot.songId} play=${snapshot.isPlaying} " +
                "load=${snapshot.isLoading} has=${snapshot.hasSong}"
        )
        val cover = if (snapshot.hasSong) {
            WidgetCoverLoader.cached(snapshot, coverStyle, cornerRatio)
        } else {
            null
        }
        if (snapshot.hasSong && cover == null) {
            // 合成里只读缓存，缺图交给后台补；补好后再推一帧，绝不让网络阻塞合成。
            WidgetCoverLoader.prefetch(context, snapshot, coverStyle, cornerRatio) {
                WidgetHub.updateAll(context)
            }
        }
        // 万一桌面确实没有实例（比如刚被移除），isActive 里会查一次，不让空转。
        WidgetUpdater.ensureProgressTicker(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val overrideSong = prefs[WidgetLikeKeys.SongId]
            val liked = if (overrideSong != null && overrideSong == snapshot.songId && snapshot.songId != 0L) {
                prefs[WidgetLikeKeys.Value] ?: snapshot.isLiked
            } else {
                snapshot.isLiked
            }
            WidgetBody(
                style = style,
                snapshot = snapshot,
                liked = liked,
                cover = cover,
                palette = WidgetPalette.of(context),
                context = context,
            )
        }
    }

    /**
     * 合成异常兜底：记下异常 + 重新画一帧（provideGlance 检测到记录就渲染错误卡）。
     * 不抛出去，否则 Glance 会复用默认行为（保留旧帧，什么也不显示）。
     */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        WidgetDebug.record(context, throwable)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            delay(200)
            runCatching { update(context, glanceId) }
        }
    }
}

object ClassicWidget : BaseMusicWidget(
    WidgetStyle.CLASSIC, CoverStyle.ROUNDED, 0.24f, SizeMode.Responsive(WidgetSizes.Classic)
)

object SquareWidget : BaseMusicWidget(
    WidgetStyle.SQUARE, CoverStyle.LARGE, 0.18f, SizeMode.Responsive(WidgetSizes.Square)
)

object CapsuleWidget : BaseMusicWidget(
    WidgetStyle.CAPSULE, CoverStyle.CIRCLE, 0f, SizeMode.Responsive(WidgetSizes.Capsule)
)

@Composable
private fun WidgetBody(
    style: WidgetStyle,
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    when (style) {
        WidgetStyle.CLASSIC -> ClassicLayout(snapshot, liked, cover, palette, context)
        WidgetStyle.SQUARE -> SquareLayout(snapshot, liked, cover, palette, context)
        WidgetStyle.CAPSULE -> CapsuleLayout(snapshot, liked, cover, palette, context)
    }
}

// —————————————————————— 通用零件 ——————————————————————

@Composable
private fun ControlIcon(
    iconRes: Int,
    description: String,
    action: androidx.glance.action.Action,
    tint: Color,
    size: Dp = 48.dp,
) {
    Box(
        modifier = GlanceModifier.size(size).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }
}

@Composable
private fun Cover(
    cover: Bitmap?,
    modifier: GlanceModifier,
    palette: WidgetPalette,
    iconSize: Dp = 24.dp,
    circle: Boolean = false,
) {
    if (cover != null) {
        Image(
            provider = ImageProvider(cover),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.then(
                roundedCard(
                    color = palette.placeholderBottom,
                    radius = if (circle) 64.dp else 16.dp,
                    drawable = R.drawable.widget_cover_placeholder,
                )
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(ColorProvider(palette.secondary.copy(alpha = 0.65f))),
            )
        }
    }
}

private fun titleStyle(color: Color, size: TextUnit): TextStyle =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = FontWeight.Medium)

private fun bodyStyle(color: Color, size: TextUnit): TextStyle =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = FontWeight.Normal)

/** Glance 的文字不支持 ellipsize，这里按可用宽度预裁，保证单行末尾有 "…"。 */
private fun ellipsize(
    context: Context,
    text: String,
    maxWidth: Dp,
    sizeSp: Float,
    bold: Boolean = false,
): String {
    if (text.isEmpty() || maxWidth.value <= 8f) return text
    val metrics = context.resources.displayMetrics
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = sizeSp * metrics.scaledDensity
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val widthPx = maxWidth.value * metrics.density
    return TextUtils.ellipsize(text, paint, widthPx, TextUtils.TruncateAt.END).toString()
}

@Composable
private fun EmptyBlock(
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    context: Context,
    titleSize: TextUnit = 14.sp,
    hintSize: TextUnit = 11.sp,
    align: TextAlign = TextAlign.Start,
    maxWidth: Dp,
) {
    Text(
        text = ellipsize(context, snapshot.statusTitle(context), maxWidth, titleSize.value, bold = true),
        maxLines = 1,
        style = titleStyle(palette.onCard, titleSize).copy(textAlign = align),
    )
    Spacer(GlanceModifier.height(3.dp))
    Text(
        text = ellipsize(context, snapshot.statusHint(context), maxWidth, hintSize.value),
        maxLines = 1,
        style = bodyStyle(palette.secondary, hintSize).copy(textAlign = align),
    )
}

// —————————————————————— 1. 经典 4×2 ——————————————————————

@Composable
private fun ClassicLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val compact = size.height < 122.dp
    val coverSize = minOf(
        if (compact) 56f else 66f,
        (size.height.value - 24f - 48f).coerceAtLeast(40f),
    ).dp
    val textWidth = size.width - 24.dp - coverSize - 10.dp

    Column(
        modifier = roundedCard(palette.card).fillMaxSize().padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(
                cover = cover,
                modifier = GlanceModifier.size(coverSize).clickable(openPlayerAction(context)),
                palette = palette,
                iconSize = if (compact) 20.dp else 24.dp,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(openPlayerOrAppAction(context, snapshot)),
            ) {
                if (snapshot.hasSong) {
                    Text(
                        text = ellipsize(context, snapshot.title, textWidth, if (compact) 14f else 15f, bold = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, if (compact) 14.sp else 15.sp),
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text = ellipsize(context, snapshot.artistLine(context), textWidth, 12f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 12.sp),
                    )
                    if (!compact && snapshot.album.isNotBlank()) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = ellipsize(context, snapshot.album, textWidth, 11f),
                            maxLines = 1,
                            style = bodyStyle(palette.secondary.copy(alpha = 0.75f), 11.sp),
                        )
                    }
                } else {
                    EmptyBlock(
                        snapshot = snapshot,
                        palette = palette,
                        context = context,
                        maxWidth = textWidth,
                    )
                }
            }
        }

        if (snapshot.hasSong) {
            // 窄屏（250dp 的最小格子）放不下 5 个按钮：44dp×5 已经吃掉整行宽度，
            // 图标会挤在一起。这里按可用宽度收敛按钮数量和点击区尺寸。
            val wide = size.width >= 300.dp
            val controlSize = if (wide) 44.dp else 40.dp
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(
                    R.drawable.ic_widget_prev, "上一首", prevAction(), palette.onCard,
                    size = controlSize,
                )
                ControlIcon(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (snapshot.isPlaying) "暂停" else "播放",
                    action = playPauseAction(),
                    tint = palette.onCard,
                    size = controlSize,
                )
                ControlIcon(
                    R.drawable.ic_widget_next, "下一首", nextAction(), palette.onCard,
                    size = controlSize,
                )
                // 私人 FM 入口：窄屏先让位给基础控制
                if (wide) {
                    ControlIcon(
                        R.drawable.ic_widget_fm, "私人FM", fmAction(),
                        tint = if (snapshot.isFm) palette.accent else palette.secondary,
                        size = controlSize,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                ControlIcon(
                    iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
                    description = if (liked) "取消喜欢" else "喜欢",
                    action = likeAction(),
                    tint = if (liked) palette.accent else palette.secondary,
                    size = controlSize,
                )
            }
        }
    }
}

// —————————————————————— 2. 方形 2×2 ——————————————————————

@Composable
private fun SquareLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val textWidth = size.width - 26.dp - 52.dp
    val scrimHeight = if (size.height < 170.dp) 58.dp else 66.dp

    Box(modifier = roundedCard(palette.card).fillMaxSize()) {
        if (snapshot.hasSong) {
            Cover(
                cover = cover,
                modifier = GlanceModifier.fillMaxSize(),
                palette = palette,
                iconSize = 32.dp,
            )
        } else {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .then(roundedCard(palette.placeholderTop, drawable = R.drawable.widget_cover_placeholder)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = GlanceModifier.size(34.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(palette.secondary.copy(alpha = 0.6f))),
                )
            }
        }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(GlanceModifier.defaultWeight())
                if (snapshot.hasSong) {
                    ControlIcon(
                        iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
                        description = if (liked) "取消喜欢" else "喜欢",
                        action = likeAction(),
                        tint = if (liked) palette.accent else Color.White,
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(scrimHeight)
                    .background(
                        imageProvider = ImageProvider(R.drawable.widget_scrim_bottom),
                        contentScale = ContentScale.FillBounds,
                    )
                    .clickable(openPlayerOrAppAction(context, snapshot)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().padding(start = 14.dp)) {
                    if (snapshot.hasSong) {
                        Text(
                            text = ellipsize(context, snapshot.title, textWidth, 15f, bold = true),
                            maxLines = 1,
                            style = titleStyle(Color.White, 15.sp),
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = ellipsize(context, snapshot.artistLine(context), textWidth, 12f),
                            maxLines = 1,
                            style = bodyStyle(Color.White.copy(alpha = 0.82f), 12.sp),
                        )
                    } else {
                        Text(
                            text = ellipsize(context, snapshot.statusTitle(context), textWidth, 14f, bold = true),
                            maxLines = 1,
                            style = titleStyle(Color.White, 14.sp),
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = ellipsize(context, snapshot.statusHint(context), textWidth, 11f),
                            maxLines = 1,
                            style = bodyStyle(Color.White.copy(alpha = 0.78f), 11.sp),
                        )
                    }
                }
                if (snapshot.hasSong) {
                    ControlIcon(
                        iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        description = if (snapshot.isPlaying) "暂停" else "播放",
                        action = playPauseAction(),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

// —————————————————————— 3. 胶囊 4×1（背景层是实时进度条） ——————————————————————

@Composable
private fun CapsuleLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    // 4×1 最小只有 250dp：40dp 封面 + 3 个 48dp 按钮 + 间距后只剩 30 多 dp，
    // 歌名会被裁成一个省略号。窄屏只保留「播放 / 下一首」，宽屏才加「上一首」。
    val wide = size.width >= 300.dp
    val showPrev = snapshot.hasSong && !snapshot.isFm && wide
    val buttons = when {
        !snapshot.hasSong -> 0
        showPrev -> 3
        else -> 2
    }
    val textWidth = size.width - 20.dp - 40.dp - 8.dp - (48.dp * buttons) - 8.dp

    Box(modifier = roundedCard(palette.card).fillMaxSize()) {
        // 实时进度：和 App 迷你播放器一样，用主题色半透明把整条胶囊从左往右填满
        if (snapshot.hasSong && snapshot.durationMs > 0L) {
            val fraction = snapshot.progress().coerceIn(0f, 1f)
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        // Glance 没有 fillMaxWidth(fraction)，按实测尺寸算宽度
                        .width(size.width * fraction)
                        .then(roundedCard(palette.accent.copy(alpha = 0.20f)))
                ) {}
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(
                cover = cover,
                modifier = GlanceModifier.size(40.dp).clickable(openPlayerAction(context)),
                palette = palette,
                iconSize = 18.dp,
                circle = true,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(openPlayerOrAppAction(context, snapshot)),
            ) {
                if (snapshot.hasSong) {
                    Text(
                        text = ellipsize(context, snapshot.title, textWidth, 14f, bold = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 14.sp),
                    )
                    Text(
                        text = ellipsize(context, snapshot.artistAlbumLine(context), textWidth, 11f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 11.sp),
                    )
                } else {
                    EmptyBlock(
                        snapshot = snapshot,
                        palette = palette,
                        context = context,
                        titleSize = 13.sp,
                        hintSize = 10.sp,
                        maxWidth = textWidth,
                    )
                }
            }
            if (snapshot.hasSong) {
                if (showPrev) ControlIcon(R.drawable.ic_widget_prev, "上一首", prevAction(), palette.onCard)
                ControlIcon(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (snapshot.isPlaying) "暂停" else "播放",
                    action = playPauseAction(),
                    tint = palette.onCard,
                )
                ControlIcon(R.drawable.ic_widget_next, "下一首", nextAction(), palette.onCard)
            }
        }
    }
}

/** 空状态点卡片进 App，有歌时进播放页。 */
internal fun openPlayerOrAppAction(context: Context, snapshot: WidgetSnapshot): androidx.glance.action.Action =
    if (snapshot.hasSong) openPlayerAction(context) else openAppAction(context)
