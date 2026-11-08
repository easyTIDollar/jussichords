package com.jussicodes.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.SystemClock
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
import java.util.Locale

/**
 * Jussi 桌面组件。
 *
 * 只有一件事：**把“正在放什么”和最基本的操作放到桌面上**。
 * 按钮预算：播放/暂停、上一首、下一曲（播放控件）、喜欢、私人 FM；点封面/文字进播放页。
 * 没有音量、没有列表、没有设置项——组件不是第二个 App。
 *
 * 视觉与 App 同源：
 * - 卡片底色由当前封面主色（[WidgetStateStore.seedFor]）轻微染色；
 * - 强调色 = 封面主色，播放键是强调色实心圆（对应播放页的 FilledIconButton 语言）；
 * - 圆角、字号、行距都和播放页的封面卡对齐。
 *
 * 三档布局按 [LocalSize] 排：三款组件各自固定尺寸、桌面不可拉伸，尺寸只用于按实际
 * 格子像素微调字号与控件，不再靠拉伸切换布局。
 * - 4×2 双列卡：左大封面 + 右三行（歌曲信息 / 进度与时长 / 控制行）；
 * - 4×1 横条：整卡背景即进度，左侧小封面 + 信息 + 控制键 + 竖线 + 快捷键；
 * - 2×3 竖版方块：上方大封面（左右半区点击切歌）+ 信息 + 短进度 + 三键居中。
 *
 * 两处平台限制（RemoteViews 决定，不是没做）：
 * - 进度条不能拖动，改为 5 段热区「点哪跳哪」；
 * - 封面不能滑动，2×3 用左右两半点击代替左滑/右滑切歌。
 */
abstract class BaseMusicWidget(private val widgetLayout: WidgetLayout) : GlanceAppWidget() {

    companion object {
        /** 上一次合成时记下的帧摘要：只在内容真的变了时写一条日志。 */
        @Volatile
        private var lastComposeDigest: String = ""
    }

    /**
     * 每个桌面小组件是独立条目（4×2 / 4×1 / 2×3 各一个），
     * 骨架由子类钉死、XML 里 resizeMode=none，所以只用 Exact 按真实尺寸排，不再做尺寸猜测。
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 合成跑到这里就说明桌面上一定有实例：把「有人用」的判定钉死，
        // 别因为某些 ROM 上 AppWidgetManager 查询为空而把整条刷新链路掐掉。
        WidgetUpdater.markAlive()
        // 组件刚添加 / 系统重建时补一下进度 ticker。
        WidgetUpdater.ensureProgressTicker(context)

        provideContent {
            // 关键：快照必须在这里取，不能提到 provideGlance 顶层。
            // Glance 用 remember 缓存 runGlance（一个常驻 channelFlow），provideGlance 只跑一次；
            // 之后每次刷新只是重放这个内容 lambda，闭包捕获的外层变量永远停在首帧。
            val snapshot = WidgetUpdater.current(context)
            val size = LocalSize.current
            val coverStyle = coverStyleFor(widgetLayout)
            val digest = "${snapshot.songId}|${snapshot.isPlaying}|${snapshot.title.take(12)}"
            if (digest != lastComposeDigest) {
                lastComposeDigest = digest
                WidgetDebug.event(context, "compose $digest")
            }
            // 读 state 有两个作用：拿红心乐观值，同时让内容在 state 变化时重放。
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
                WidgetBody(snapshot, liked, cover, palette, context, widgetLayout, size)
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

// —————————————————————— 尺寸分档 ——————————————————————

enum class WidgetLayout { CARD, TILE, BANNER }

private fun coverStyleFor(layout: WidgetLayout): CoverStyle = when (layout) {
    WidgetLayout.CARD -> CoverStyle.THUMB
    WidgetLayout.TILE -> CoverStyle.FULL
    WidgetLayout.BANNER -> CoverStyle.SMALL
}

/** 封面圆角比例：三档都是「方形圆角」，按各自尺寸算像素，别在小图上切出大圆角。 */
private fun coverRatio(style: CoverStyle): Float = when (style) {
    CoverStyle.THUMB -> 0.17f
    CoverStyle.FULL -> 0.15f
    CoverStyle.SMALL -> 0.22f
}

@Composable
private fun WidgetBody(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
    layout: WidgetLayout,
    size: DpSize,
) {
    when (layout) {
        WidgetLayout.CARD -> CardLayout(snapshot, liked, cover, palette, context, size)
        WidgetLayout.TILE -> TileLayout(snapshot, liked, cover, palette, context, size)
        WidgetLayout.BANNER -> BannerLayout(snapshot, liked, cover, palette, context, size)
    }
}

// —————————————————————— 布局 1：4×2 双列卡 ——————————————————————

/**
 * 左侧大方形圆角封面；右侧纵向三层：歌曲信息（居中）/ 进度与时长 / 控制栏。
 *
 * 控制栏严格留在右列：主控制区（上一曲、圆形播放/暂停、下一曲）靠左，
 * 副控制区（收音机、爱心）靠右，中间用可伸缩的空隙自然分开。
 * 250dp 规格下右列只有 130dp，所以按钮尺寸按此反推（34dp 主键 + 22/20dp 副键）。
 */
@Composable
private fun CardLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 10.dp
    val sideSize = 22.dp
    val playSize = 34.dp
    val quickSize = 20.dp
    // 右列必须能放下整整一行按钮，封面再从剩余宽度里取，避免小格子上把按钮挤扁。
    val controlWidth = sideSize * 2 + playSize + quickSize * 2
    val coverLimit = size.width - pad * 2 - controlWidth - 10.dp
    val coverSize = (size.height - pad * 2).coerceAtMost(coverLimit).coerceIn(52.dp, 112.dp)
    val rightWidth = size.width - pad * 2 - coverSize - 10.dp

    Row(
        modifier = roundedCard(palette.card).fillMaxSize().padding(pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            cover = cover,
            palette = palette,
            modifier = GlanceModifier.size(coverSize)
                .clickable(openTargetAction(context, snapshot)),
            radius = 14.dp,
            iconSize = 22.dp,
        )
        Spacer(GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.width(rightWidth).fillMaxHeight()) {
            // 上层：歌名 / 歌手，水平居中，垂直方向吃掉剩余空间
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (snapshot.hasSong) {
                    Text(
                        text = scrollingTitle(context, snapshot.title, rightWidth, 14f),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 14.sp),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = ellipsize(context, snapshot.subtitle(context), rightWidth, 11f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 11.sp),
                    )
                } else {
                    Text(
                        text = ellipsize(context, snapshot.statusTitle(context), rightWidth, 13f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 13.sp),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = ellipsize(context, snapshot.statusHint(context), rightWidth, 10.5f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 10.5.sp),
                    )
                }
            }

            // 中层：进度条 + 两端时长
            ProgressRow(snapshot, palette, rightWidth, compact = true)
            Spacer(GlanceModifier.height(4.dp))

            // 下层：主控制区 + 副控制区
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(playSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (snapshot.hasSong) {
                    BareIcon(
                        iconRes = R.drawable.ic_widget_prev,
                        descriptionRes = R.string.widget_action_prev,
                        action = prevAction(),
                        tint = palette.onCard,
                        size = sideSize,
                        iconSize = 13.dp,
                    )
                }
                PlayButton(snapshot.isPlaying, palette, playSize)
                if (snapshot.hasSong) {
                    BareIcon(
                        iconRes = R.drawable.ic_widget_next,
                        descriptionRes = R.string.widget_action_next,
                        action = nextAction(),
                        tint = palette.onCard,
                        size = sideSize,
                        iconSize = 13.dp,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                BareIcon(
                    iconRes = R.drawable.ic_widget_fm,
                    descriptionRes = R.string.widget_action_fm,
                    action = fmAction(),
                    tint = if (snapshot.isFm) palette.accent else palette.secondary,
                    size = quickSize,
                    iconSize = 12.dp,
                )
                LikeIcon(liked, palette, size = quickSize, iconSize = 12.dp)
            }
        }
    }
}

// —————————————————————— 布局 2：4×1 横条（整卡进度） ——————————————————————

/**
 * 整卡背景当进度条：底层铺一条按播放比例拉伸的强调色填充，上层照常排内容。
 * 参考的是 App 内悬浮小播放器那套「背景即进度」的视觉。
 *
 * 宽度只有 250dp 时五个按钮必须收紧到 28dp，否则文字会被挤成一个字。
 */
@Composable
private fun BannerLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 10.dp
    val coverSize = (size.height - pad * 2).coerceIn(32.dp, 46.dp)
    val btn = if (size.width < 300.dp) 28.dp else 32.dp
    val iconSize = if (size.width < 300.dp) 16.dp else 18.dp
    val buttons = if (snapshot.hasSong) 5 else 0
    val textWidth = (size.width - pad * 2 - coverSize - 8.dp - btn * buttons - 18.dp)
        .coerceAtLeast(24.dp)

    Box(modifier = roundedCard(palette.card).fillMaxSize()) {
        // 背景进度：播放到哪，整张卡就亮到哪。
        if (snapshot.hasSong && snapshot.durationMs > 0L) {
            val fraction = snapshot.progress().coerceIn(0f, 1f)
            Box(
                modifier = GlanceModifier.width(size.width * fraction)
                    .fillMaxHeight()
                    .background(palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.18f)),
            ) {}
        }

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.size(coverSize)
                    .clickable(openTargetAction(context, snapshot)),
                radius = coverSize / 4,
                iconSize = 18.dp,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.width(textWidth)
                    .clickable(openTargetAction(context, snapshot)),
            ) {
                if (snapshot.hasSong) {
                    Text(
                        text = ellipsize(context, snapshot.title, textWidth, 13f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 13.sp),
                    )
                    Text(
                        text = ellipsize(context, snapshot.subtitle(context), textWidth, 10.5f),
                        maxLines = 1,
                        style = bodyStyle(palette.secondary, 10.5.sp),
                    )
                } else {
                    Text(
                        text = ellipsize(context, snapshot.statusTitle(context), textWidth, 12.5f, medium = true),
                        maxLines = 1,
                        style = titleStyle(palette.onCard, 12.5.sp),
                    )
                }
            }

            if (snapshot.hasSong) {
                BareIcon(
                    iconRes = R.drawable.ic_widget_prev,
                    descriptionRes = R.string.widget_action_prev,
                    action = prevAction(),
                    tint = palette.onCard,
                    size = btn,
                    iconSize = iconSize,
                )
                PlayButton(snapshot.isPlaying, palette, btn + 4.dp)
                BareIcon(
                    iconRes = R.drawable.ic_widget_next,
                    descriptionRes = R.string.widget_action_next,
                    action = nextAction(),
                    tint = palette.onCard,
                    size = btn,
                    iconSize = iconSize,
                )
                VDivider(palette.track)
                BareIcon(
                    iconRes = R.drawable.ic_widget_fm,
                    descriptionRes = R.string.widget_action_fm,
                    action = fmAction(),
                    tint = if (snapshot.isFm) palette.accent else palette.secondary,
                    size = btn,
                    iconSize = iconSize,
                )
                LikeIcon(liked, palette, size = btn, iconSize = iconSize)
            }
        }
    }
}

// —————————————————————— 布局 3：2×3 竖版方块 ——————————————————————

/**
 * 自上而下：大封面（占上半，左右半区点击即上一首/下一首）、歌名歌手、短进度与时长、三键居中。
 * 固定 2×3：封面用 weight 吃掉多出来的高度。不同启动器一格高度差异不小（180~270dp），
 * 按钮尺寸按实际高度分两档，宽度永远按 inner 算，不写死某台设备的像素。
 */
@Composable
private fun TileLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 12.dp
    val inner = size.width - pad * 2
    val tall = size.height >= 210.dp
    val playSize = if (tall) 46.dp else 42.dp
    val sideSize = if (tall) 36.dp else 32.dp

    Column(
        modifier = roundedCard(palette.card)
            .fillMaxSize()
            .padding(pad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
                radius = 14.dp,
                iconSize = 26.dp,
                fill = true,
            )
            // 封面左右半区：点左边上一首、点右边下一首（远程视图收不到滑动手势，用它代替）。
            if (snapshot.hasSong) {
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    Box(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight()
                            .clickable(prevAction()),
                    ) {}
                    Box(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight()
                            .clickable(nextAction()),
                    ) {}
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        if (snapshot.hasSong) {
            Text(
                text = ellipsize(context, snapshot.title, inner, 13f, medium = true),
                maxLines = 1,
                style = titleStyle(palette.onCard, 13.sp),
                modifier = GlanceModifier.clickable(openTargetAction(context, snapshot)),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = ellipsize(context, snapshot.subtitle(context), inner, 10.5f),
                maxLines = 1,
                style = bodyStyle(palette.secondary, 10.5.sp),
                modifier = GlanceModifier.clickable(openTargetAction(context, snapshot)),
            )
            Spacer(GlanceModifier.height(6.dp))
            ProgressRow(snapshot, palette, inner, compact = true)
        } else {
            Text(
                text = ellipsize(context, snapshot.statusTitle(context), inner, 13f, medium = true),
                maxLines = 1,
                style = titleStyle(palette.onCard, 13.sp),
                modifier = GlanceModifier.clickable(openTargetAction(context, snapshot)),
            )
        }

        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (snapshot.hasSong) {
                BareIcon(
                    iconRes = R.drawable.ic_widget_prev,
                    descriptionRes = R.string.widget_action_prev,
                    action = prevAction(),
                    tint = palette.onCard,
                    size = sideSize,
                    iconSize = 18.dp,
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            PlayButton(snapshot.isPlaying, palette, playSize)
            if (snapshot.hasSong) {
                Spacer(GlanceModifier.width(6.dp))
                BareIcon(
                    iconRes = R.drawable.ic_widget_next,
                    descriptionRes = R.string.widget_action_next,
                    action = nextAction(),
                    tint = palette.onCard,
                    size = sideSize,
                    iconSize = 18.dp,
                )
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

/** 1dp 竖分隔线：把「播放控件」和「快捷功能」分开。 */
@Composable
private fun VDivider(color: Color) {
    Spacer(GlanceModifier.width(6.dp))
    Box(
        modifier = GlanceModifier.width(1.dp).height(18.dp)
            .background(color),
    ) {}
    Spacer(GlanceModifier.width(6.dp))
}

/**
 * 封面。缺图时画一块同色占位（不让组件出现空洞），有图就按样式裁好的圆角图直接贴。
 * [fill] 用于 2×3 的满容器封面：铺满容器，圆角由外层裁。
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
 * 进度行：`当前时间 ──条── 总时长`。
 *
 * RemoteViews 不支持拖动，所以条本身切 5 段热区，点哪一段就跳到哪里（精度 20%）。
 * 没有时长（直播 / 还在解析）就画一条静态底线，时间位显示 --:--。
 */
@Composable
private fun ProgressRow(
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    width: Dp,
    compact: Boolean = false,
) {
    val timeWidth = if (compact) 26.dp else 30.dp
    val gap = if (compact) 4.dp else 6.dp
    val barWidth = (width - timeWidth * 2 - gap * 2).coerceAtLeast(20.dp)
    val fontSize = if (compact) 9f else 10f
    val hasDuration = snapshot.hasSong && snapshot.durationMs > 0L

    Row(
        modifier = GlanceModifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hasDuration) {
                formatClock((snapshot.progress() * snapshot.durationMs).toLong())
            } else {
                "--:--"
            },
            maxLines = 1,
            style = bodyStyle(palette.secondary, fontSize.sp),
            modifier = GlanceModifier.width(timeWidth),
        )
        Spacer(GlanceModifier.width(gap))
        SeekBar(palette, barWidth, snapshot.progress().coerceIn(0f, 1f), hasDuration)
        Spacer(GlanceModifier.width(gap))
        Text(
            text = if (hasDuration) formatClock(snapshot.durationMs) else "--:--",
            maxLines = 1,
            style = bodyStyle(palette.secondary, fontSize.sp),
            modifier = GlanceModifier.width(timeWidth),
        )
    }
}

/**
 * 细进度条 + 5 段点击热区。
 * Glance 画不了动画，实时感靠 WidgetUpdater 的 1s ticker。
 */
@Composable
private fun SeekBar(
    palette: WidgetPalette,
    width: Dp,
    fraction: Float,
    hasDuration: Boolean,
) {
    val height = 3.dp
    Box(
        modifier = GlanceModifier.width(width).height(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = GlanceModifier.width(width).height(height)
                .background(palette.track),
        ) {}
        if (fraction > 0f) {
            Box(
                modifier = GlanceModifier.width(width * fraction).height(height)
                    .background(palette.accent),
            ) {}
        }
        if (hasDuration) {
            // 点击热区铺满整条，分五段：点哪跳哪。
            Row(modifier = GlanceModifier.width(width).height(14.dp)) {
                repeat(5) { index ->
                    Box(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            .clickable(seekAction((index + 0.5f) / 5f)),
                    ) {}
                }
            }
        }
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

/** 毫秒 → m:ss（超过一小时补 h:mm:ss）。 */
private fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 量一次文字宽度（像素）：组件里没有 TextView，只能自己按 Paint 算。 */
private fun measureWidth(context: Context, text: String, sizeSp: Float, medium: Boolean = false): Float {
    if (text.isEmpty()) return 0f
    val metrics = context.resources.displayMetrics
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = sizeSp * metrics.scaledDensity
        typeface = if (medium) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    return paint.measureText(text)
}

/**
 * 超长标题「滚动显示」。
 *
 * Glance 的 Text 改不了 ellipsize/marquee 属性，所以只能自己滚：1s ticker 每秒重绘一帧，
 * 这里按当前时间把可视窗口沿「标题 + 分隔点」循环往后挪一格，看起来就是向左滚动。
 * 文字没超宽就原样返回，不做无意义的重排。
 */
private fun scrollingTitle(context: Context, text: String, maxWidth: Dp, sizeSp: Float): String {
    val metrics = context.resources.displayMetrics
    val limit = maxWidth.value * metrics.density
    if (text.isEmpty() || limit <= 24f) return text
    if (measureWidth(context, text, sizeSp, medium = true) <= limit) return text

    val loop = "$text   •   "
    val doubled = loop + loop
    val perChar = measureWidth(context, doubled, sizeSp, medium = true) / doubled.length
    if (perChar <= 0f) return text
    val visible = (limit / perChar).toInt().coerceIn(4, loop.length)
    val offset = ((SystemClock.elapsedRealtime() / 900L) % loop.length).toInt()
    return doubled.substring(offset, (offset + visible).coerceAtMost(doubled.length))
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

// —————————————————————— 三个独立的桌面小组件 ——————————————————————

/** 4×2：横向双列卡（左大封面 + 右三层）。 */
class CardWidget : BaseMusicWidget(WidgetLayout.CARD)

/** 4×1：横向扁长条，整卡背景即进度。 */
class BannerWidget : BaseMusicWidget(WidgetLayout.BANNER)

/** 2×3：竖版卡片，封面在上、控件在下。 */
class TileWidget : BaseMusicWidget(WidgetLayout.TILE)
