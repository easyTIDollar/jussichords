package com.jussicodes.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jussicodes.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Jussi 桌面组件。
 *
 * 只有一件事：**把“正在放什么”和最基本的操作放到桌面上**。
 * 按钮预算：播放/暂停、上一首、下一首（播放控件）、喜欢、私人 FM；点封面/文字进播放页。
 * 没有音量、没有列表、没有设置项——组件不是第二个 App。
 *
 * 视觉与 App 同源：
 * - 卡片底色由当前封面主色（[WidgetStateStore.seedFor]）轻微染色；
 * - 强调色 = 封面主色，播放键是强调色实心圆（对应播放页的 FilledIconButton 语言）；
 * - 圆角、字号、行距都和播放页的封面卡对齐。
 *
 * 三档布局按 [LocalSize] 自动切换，桌面拉伸时不会出现“按钮挤成一排”的中间态：
 * - 4×2 宽卡：左侧封面 + 右侧曲目信息，底部一条控制行 + 一条细进度线；
 * - 2×2 方形：封面铺满、底部压暗，标题两行 + 圆形播放键，喜欢 / FM 收在右上角；
 * - 4×1 窄条：圆形小封面 + 单行曲目信息 + 播放 / 喜欢（够宽才加下一首），进度线贴卡底。
 */
class JussiMusicWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.All)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 进程内实时帧优先：播放服务还活着时它一定是最新状态，不依赖落盘。
        val snapshot = WidgetUpdater.current(context)
        // 合成跑到这里就说明桌面上一定有实例：把「有人用」的判定钉死，
        // 别因为某些 ROM 上 AppWidgetManager 查询为空而把整条刷新链路掐掉。
        WidgetUpdater.markAlive()
        WidgetDebug.log(
            "compose id=${id.hashCode()} song=${snapshot.songId} play=${snapshot.isPlaying} " +
                "load=${snapshot.isLoading} has=${snapshot.hasSong}"
        )
        // 组件刚添加 / 系统重建时补一下进度 ticker。
        WidgetUpdater.ensureProgressTicker(context)

        provideContent {
            val size = LocalSize.current
            val layout = layoutFor(size)
            val coverStyle = coverStyleFor(layout)
            val prefs = currentState<Preferences>()
            // 红心乐观覆盖：只对本实例、当前这首歌生效。
            val overrideSong = prefs[WidgetLikeKeys.SongId]
            val liked = if (overrideSong != null && overrideSong == snapshot.songId && snapshot.songId != 0L) {
                prefs[WidgetLikeKeys.Value] ?: snapshot.isLiked
            } else {
                snapshot.isLiked
            }
            val cover = WidgetCoverLoader.cached(snapshot, coverStyle, coverRatio(coverStyle))
            if (snapshot.hasSong && cover == null) {
                // 合成里只读缓存，缺图交给后台补；补好后再推一帧，绝不让网络阻塞合成。
                WidgetCoverLoader.prefetch(
                    context = context,
                    snapshot = snapshot,
                    style = coverStyle,
                    cornerRatio = coverRatio(coverStyle),
                ) { WidgetHub.updateAll(context) }
            }
            val palette = WidgetPalette.of(
                context,
                WidgetStateStore.seedFor(context, snapshot.coverUrl),
            )

            Box(modifier = GlanceModifier.fillMaxSize()) {
                WidgetBody(snapshot, liked, cover, palette, context, layout)
            }
        }
    }

    /**
     * 合成异常兜底：记下异常 + 200ms 后重画一帧。
     * 不抛出去，否则 Glance 会直接保留旧帧，用户看到的就是“组件不死不活”。
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

private enum class WidgetLayout { CARD, SQUARE, STRIP }

/**
 * 尺寸 → 布局。
 *
 * 只有三档，阈值取在候选尺寸之间（见 [WidgetSizes]），不会在拖拽中途落到第四种形态：
 * - 高度不到 100dp 一定是 4×1；
 * - 接近正方形（宽 ≤ 260dp 且高 ≥ 宽 × 0.85）走封面满铺；
 * - 其余是宽卡。
 */
private fun layoutFor(size: DpSize): WidgetLayout = when {
    size.height <= 100.dp -> WidgetLayout.STRIP
    size.width <= 260.dp && size.height >= size.width * 0.85f -> WidgetLayout.SQUARE
    else -> WidgetLayout.CARD
}

private fun coverStyleFor(layout: WidgetLayout): CoverStyle = when (layout) {
    WidgetLayout.CARD -> CoverStyle.THUMB
    WidgetLayout.SQUARE -> CoverStyle.FULL
    WidgetLayout.STRIP -> CoverStyle.CIRCLE
}

private fun coverRatio(style: CoverStyle): Float = when (style) {
    CoverStyle.THUMB -> 0.22f
    CoverStyle.FULL -> 0f
    CoverStyle.CIRCLE -> 0f
}

@Composable
private fun WidgetBody(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
    layout: WidgetLayout,
) {
    when (layout) {
        WidgetLayout.CARD -> CardLayout(snapshot, liked, cover, palette, context)
        WidgetLayout.SQUARE -> SquareLayout(snapshot, liked, cover, palette, context)
        WidgetLayout.STRIP -> StripLayout(snapshot, liked, cover, palette, context)
    }
}

// —————————————————————— 布局 1：4×2 宽卡 ——————————————————————

@Composable
private fun CardLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val pad = if (size.height < 130.dp) 12.dp else 14.dp
    // 封面跟卡片高度走，但给控制行留出 46dp，避免小格子把封面压成一条。
    val coverSize = ((size.height - pad * 2 - 50.dp).value)
        .coerceIn(56f, 88f).dp
    val controls = if (size.width >= 270.dp) 3 else 1
    val textWidth = size.width - pad * 2 - coverSize - 14.dp

    Column(
        modifier = roundedCard(palette.card)
            .fillMaxSize()
            .padding(pad)
            .clickable(openTargetAction(context, snapshot)),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.size(coverSize),
                radius = WidgetTokens.CoverRadius,
                iconSize = 22.dp,
            )
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (snapshot.hasSong) {
                    Text(
                        text = ellipsize(context, snapshot.title, textWidth, 15f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 15.sp),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = ellipsize(context, snapshot.subtitle(context), textWidth, 12f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 12.sp),
                    )
                } else {
                    EmptyCopy(snapshot, palette, context, textWidth)
                }
            }
        }

        Spacer(GlanceModifier.height(6.dp))
        ProgressLine(snapshot, palette)

        if (snapshot.hasSong) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (controls == 3) {
                    BareIcon(R.drawable.ic_widget_prev, R.string.widget_action_prev, prevAction(), palette.onCard)
                }
                PlayButton(snapshot.isPlaying, palette, 40.dp)
                if (controls == 3) {
                    BareIcon(R.drawable.ic_widget_next, R.string.widget_action_next, nextAction(), palette.onCard)
                }
                Spacer(GlanceModifier.defaultWeight())
                BareIcon(
                    iconRes = R.drawable.ic_widget_fm,
                    descriptionRes = R.string.widget_action_fm,
                    action = fmAction(),
                    tint = if (snapshot.isFm) palette.accent else palette.secondary,
                )
                Spacer(GlanceModifier.width(4.dp))
                LikeIcon(liked, palette)
            }
        }
    }
}

// —————————————————————— 布局 2：2×2 方形 ——————————————————————

@Composable
private fun SquareLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val pad = 14.dp
    val textWidth = size.width - pad * 2 - 52.dp

    Box(
        modifier = roundedCard(palette.card)
            .fillMaxSize()
            .clickable(openTargetAction(context, snapshot)),
    ) {
        if (snapshot.hasSong) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
                radius = 0.dp,
                iconSize = 30.dp,
                fill = true,
            )
            // 底部压暗：标题直接压在封面上，靠它保证可读性。
            Box(
                modifier = GlanceModifier.fillMaxSize().background(
                    imageProvider = ImageProvider(R.drawable.widget_scrim_bottom),
                    contentScale = ContentScale.FillBounds,
                )
            ) {}
        } else {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = GlanceModifier.size(30.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.secondary.copy(alpha = 0.55f))),
                    )
                }
            }
        }

        Column(modifier = GlanceModifier.fillMaxSize().padding(pad)) {
            // 顶部右角：私人 FM / 喜欢。两张小图标放在封面之上，靠投影和压暗都省了。
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(GlanceModifier.defaultWeight())
                if (snapshot.hasSong) {
                    BareIcon(
                        iconRes = R.drawable.ic_widget_fm,
                        descriptionRes = R.string.widget_action_fm,
                        action = fmAction(),
                        tint = if (snapshot.isFm) palette.accent else Color.White.copy(alpha = 0.86f),
                        size = 34.dp,
                        iconSize = 19.dp,
                    )
                    LikeIcon(
                        liked = liked,
                        palette = palette,
                        tintOverride = if (liked) palette.accent else Color.White.copy(alpha = 0.86f),
                        size = 34.dp,
                        iconSize = 20.dp,
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (snapshot.hasSong) {
                        Text(
                            text = ellipsize(context, snapshot.title, textWidth, 15f, medium = true),
                            maxLines = 1,
                            style = titleStyle(Color.White, 15.sp),
                        )
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            text = ellipsize(context, snapshot.subtitle(context), textWidth, 11.5f),
                            maxLines = 1,
                            style = bodyStyle(Color.White.copy(alpha = 0.82f), 11.5.sp),
                        )
                    } else {
                        Text(
                            text = ellipsize(context, snapshot.statusTitle(context), textWidth, 14f, medium = true),
                            maxLines = 1,
                            style = titleStyle(palette.onCard, 14.sp),
                        )
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            text = ellipsize(context, snapshot.statusHint(context), textWidth, 11f),
                            maxLines = 1,
                            style = bodyStyle(palette.secondary, 11.sp),
                        )
                    }
                }
                if (snapshot.hasSong) {
                    Spacer(GlanceModifier.width(6.dp))
                    PlayButton(snapshot.isPlaying, palette, 44.dp)
                }
            }
            if (snapshot.hasSong) {
                Spacer(GlanceModifier.height(8.dp))
                ProgressLine(snapshot, palette)
            }
        }
    }
}

// —————————————————————— 布局 3：4×1 窄条 ——————————————————————

@Composable
private fun StripLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val pad = 10.dp
    // 4×1 最小只有 250dp：封面 38 + 歌名 + 两个 40dp 点击区就到头了，
    // 够宽（≥ 310dp）再加一个「下一首」，宁可少一个次要按钮，也不要挤成一排点不准的图标。
    val showNext = snapshot.hasSong && size.width >= 310.dp
    val buttons = when {
        !snapshot.hasSong -> 0
        showNext -> 3
        else -> 2
    }
    val textWidth = size.width - pad * 2 - 38.dp - 10.dp - (40.dp * buttons) - 4.dp

    Box(modifier = roundedCard(palette.card).fillMaxSize()) {
        // 进度线贴卡底：窄条没地方放横向进度，用底边这一条表示“放到哪了”。
        if (snapshot.hasSong) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                ProgressLine(snapshot, palette, inset = pad)
            }
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.size(38.dp).clickable(openTargetAction(context, snapshot)),
                radius = 19.dp,
                iconSize = 18.dp,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(
                modifier = GlanceModifier.defaultWeight()
                    .clickable(openTargetAction(context, snapshot)),
            ) {
                if (snapshot.hasSong) {
                    Text(
                        text = ellipsize(context, snapshot.title, textWidth, 14f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 14.sp),
                    )
                    Text(
                        text = ellipsize(context, snapshot.subtitle(context), textWidth, 11f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 11.sp),
                    )
                } else {
                    Text(
                        text = ellipsize(context, snapshot.statusTitle(context), textWidth, 13f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 13.sp),
                    )
                    Text(
                        text = ellipsize(context, snapshot.statusHint(context), textWidth, 10.5f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 10.5.sp),
                    )
                }
            }
            if (snapshot.hasSong) {
                LikeIcon(liked, palette, size = 40.dp, iconSize = 21.dp)
                PlayButton(snapshot.isPlaying, palette, 36.dp)
                if (showNext) {
                    BareIcon(
                        iconRes = R.drawable.ic_widget_next,
                        descriptionRes = R.string.widget_action_next,
                        action = nextAction(),
                        tint = palette.onCard,
                        size = 40.dp,
                        iconSize = 22.dp,
                    )
                }
            }
        }
    }
}

// —————————————————————— 通用零件 ——————————————————————

private fun titleStyle(color: Color, size: TextUnit): TextStyle =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = FontWeight.Medium)

private fun bodyStyle(color: Color, size: TextUnit): TextStyle =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = FontWeight.Normal)

/** 圆形主按钮：强调色实心 + 对比色图标，对应播放页的 FilledIconButton。 */
@Composable
private fun PlayButton(playing: Boolean, palette: WidgetPalette, size: Dp) {
    Box(
        modifier = GlanceModifier.size(size)
            .then(roundedCard(palette.accent, size / 2, R.drawable.widget_circle))
            .clickable(playPauseAction()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = null,
            modifier = GlanceModifier.size(size * 0.46f),
            colorFilter = ColorFilter.tint(ColorProvider(palette.onAccent)),
        )
    }
}

/** 裸图标按钮（上一首 / 下一首 / 私人 FM）：点击区固定，图标居中。 */
@Composable
private fun BareIcon(
    iconRes: Int,
    descriptionRes: Int,
    action: Action,
    tint: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    val description = LocalContext.current.getString(descriptionRes)
    Box(
        modifier = GlanceModifier.size(size).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }
}

@Composable
private fun LikeIcon(
    liked: Boolean,
    palette: WidgetPalette,
    tintOverride: Color? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    BareIcon(
        iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
        descriptionRes = if (liked) R.string.widget_action_unlike else R.string.widget_action_like,
        action = likeAction(),
        tint = tintOverride ?: if (liked) palette.accent else palette.secondary,
        size = size,
        iconSize = iconSize,
    )
}

/**
 * 封面。缺图时画一块同色占位（不让组件出现空洞），有图就按样式裁好的圆角/圆图直接贴。
 * [fill] 用于方形满铺：铺满容器、不参与圆角（圆角由卡片裁）。
 */
@Composable
private fun CoverArt(
    cover: Bitmap?,
    palette: WidgetPalette,
    modifier: GlanceModifier,
    radius: Dp,
    iconSize: Dp,
    fill: Boolean = false,
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
                if (fill) {
                    GlanceModifier.fillMaxSize()
                        .background(palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.16f))
                } else {
                    roundedCard(
                        color = palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.16f),
                        radius = if (radius > 0.dp) radius else WidgetTokens.CoverRadius,
                    )
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(ColorProvider(palette.secondary.copy(alpha = 0.7f))),
            )
        }
    }
}

/**
 * 细进度线。
 *
 * Glance 画不了动画，实时感靠 WidgetUpdater 的 1s ticker：每轮把真实播放位置写进快照再重绘。
 * 没有时长（直播 / 解析中）就不画，免得停在 0% 让人以为卡住了。
 */
@Composable
private fun ProgressLine(snapshot: WidgetSnapshot, palette: WidgetPalette, inset: Dp = 0.dp) {
    if (!snapshot.hasSong || snapshot.durationMs <= 0L) return
    val fraction = snapshot.progress().coerceIn(0f, 1f)
    val width = (LocalSize.current.width - inset * 2).coerceAtLeast(0.dp)
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(2.dp).padding(horizontal = inset),
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(2.dp)
                .background(palette.track),
        )
        Box(
            modifier = GlanceModifier.height(2.dp)
                // Glance 没有 fillMaxWidth(fraction)，按实测宽度自己乘。
                .width(width * fraction)
                .background(palette.accent),
        )
    }
}

@Composable
private fun EmptyCopy(
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    context: Context,
    textWidth: Dp,
) {
    Text(
        text = ellipsize(context, snapshot.statusTitle(context), textWidth, 15f, medium = true),
        maxLines = 1,
        style = titleStyle(palette.onCard, 15.sp),
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = ellipsize(context, snapshot.statusHint(context), textWidth, 12f),
        maxLines = 1,
        style = bodyStyle(palette.secondary, 12.sp),
    )
}

/**
 * Glance 的文字不支持 ellipsize，这里按可用宽度预裁，保证单行末尾有 "…"。
 * 不写死文字宽度——不同 DPI、不同格子尺寸下都会被裁到看不见歌名。
 */
private fun ellipsize(
    context: Context,
    text: String,
    maxWidth: Dp,
    sizeSp: Float,
    medium: Boolean = false,
): String {
    if (text.isEmpty() || maxWidth.value <= 8f) return text
    val metrics = context.resources.displayMetrics
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = sizeSp * metrics.scaledDensity
        typeface = if (medium) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val widthPx = maxWidth.value * metrics.density
    return TextUtils.ellipsize(text, paint, widthPx, TextUtils.TruncateAt.END).toString()
}

/** 空状态点卡片进 App，有歌时进播放页。 */
internal fun openTargetAction(context: Context, snapshot: WidgetSnapshot): Action =
    if (snapshot.hasSong) openPlayerAction(context) else openAppAction(context)
