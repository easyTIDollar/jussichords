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
 * 两款布局按 [LocalSize] 排：4×1 横条 + 2×3 方块各自固定尺寸、桌面不可拉伸，尺寸只用于按实际
 * 格子像素微调字号与控件，不再靠拉伸切换布局。
 * - 4×1 横条：整卡背景即进度，左侧小封面 + 信息 + 控制键 + 竖线 + 快捷键；
 * - 2×3 竖版方块：上方大封面（左右半区点击切歌）+ 信息 + 短进度 + 三键居中。
 *
 * 两处平台限制（RemoteViews 决定，不是没做）：
 * - 进度条不能拖动，改为 5 段热区「点哪跳哪」；
 * - 封面不能滑动，2×3 用左右两半点击代替左滑/右滑切歌。
 */
abstract class BaseMusicWidget(private val widgetLayout: WidgetLayout) : GlanceAppWidget() {

    /**
     * 两款桌面小组件是独立条目（4×1 横条 + 2×3 方块各一个），
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
            WidgetDebug.log("compose " + widgetLayout.name + " size=" + size.width.value.toInt() + "x" + size.height.value.toInt() + "dp")
            val coverStyle = coverStyleFor(widgetLayout)
            // 读 state 有两个作用：拿红心乐观值，同时让内容在 state 变化时重放。
            val prefs = currentState<Preferences>()
            // 红心乐观覆盖：只对本实例、当前这首歌生效。
            val overrideSong = prefs[WidgetLikeKeys.SongId]
            val liked = if (overrideSong != null && overrideSong == snapshot.songId && snapshot.songId != 0L) {
                prefs[WidgetLikeKeys.Value] ?: snapshot.isLiked
            } else {
                snapshot.isLiked
            }
            val coverRatioValue = coverRatio(coverStyle, size)
            val cover = WidgetCoverLoader.cached(snapshot, coverStyle, coverRatioValue)
            if (snapshot.hasSong && cover == null) {
                // 合成里只读缓存，缺图交给后台补；补好后再推一帧，绝不让网络阻塞合成。
                WidgetCoverLoader.prefetch(
                    context = context,
                    snapshot = snapshot,
                    style = coverStyle,
                    cornerRatio = coverRatioValue,
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
     * 合成异常兜底：200ms 后重画一帧。
     * 不抛出去，否则 Glance 会直接保留旧帧，用户看到的就是“组件不死不活”。
     */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            delay(200)
            runCatching { update(context, glanceId) }
        }
    }
}

// —————————————————————— 尺寸分档 ——————————————————————

enum class WidgetLayout { TILE, BANNER }

private fun coverStyleFor(layout: WidgetLayout): CoverStyle = when (layout) {
    WidgetLayout.TILE -> CoverStyle.FULL
    WidgetLayout.BANNER -> CoverStyle.SMALL
}

/**
 * 封面圆角比例（画在位图上，随封面等比缩放）。
 *
 * 2×3（FULL）要求与卡片同圆角：[WidgetTokens.CardRadius] = 26dp。位图的圆角是按比例画的，
 * 所以用封面实际显示宽度反算比例（- 20dp = 两侧各 10dp 内边距，与 TileLayout 的 pad 一致）。
 * 夹在 0.08~0.42 之间，防止极端尺寸下退化成圆角过大的胶囊。
 */
private fun coverRatio(style: CoverStyle, size: DpSize): Float = when (style) {
    CoverStyle.FULL -> {
        val side = tileCoverSide(size).value.coerceAtLeast(1f)
        (WidgetTokens.CardRadius.value / side).coerceIn(0.08f, 0.42f)
    }
    CoverStyle.SMALL -> 0.25f
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
        WidgetLayout.TILE -> TileLayout(snapshot, liked, cover, palette, context, size)
        WidgetLayout.BANNER -> BannerLayout(snapshot, liked, cover, palette, context, size)
    }
}

// —————————————————————— 布局 1：4×1 横条（整卡进度） ——————————————————————

/**
 * 整卡背景当进度条：底层铺一条按播放比例拉伸的强调色填充，上层照常排内容。
 * 参考的是 App 内悬浮小播放器那套「背景即进度」的视觉。
 *
 * 五个控件始终保留，按可用宽度同步缩放按钮与间距，文字至少保留 40dp。
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
    // 宿主给出多行高度时，内容仍按单行规格居中排列。
    val rowHeight = minOf(size.height, 84.dp).coerceAtLeast(48.dp)
    val coverSize = (rowHeight - pad * 2).coerceIn(28.dp, 72.dp)
    val inner = size.width - pad * 2
    val minTextWidth = 40.dp
    val dividerWidth = 1.dp
    val gapRatio = 6f / 32f
    // 五键为 5 × btn + 4dp，分隔线两侧各半个间距，合计四个完整间距。
    val maxBtn = ((inner - coverSize - 8.dp - minTextWidth - 4.dp - dividerWidth) /
        (5f + 4f * gapRatio)).coerceAtLeast(0.dp)
    // 常规范围为 22～32dp；宿主特别窄或封面较大时，继续回退以保全文字和五键。
    val btn = maxBtn.coerceIn(22.dp, 32.dp).coerceAtMost(maxBtn)
    val gap = btn * gapRatio
    val controlHeight = btn + 4.dp
    val iconSize = (btn * 0.58f).coerceIn(12.dp, 20.dp)
    val controlsWidth = btn * 5 + 4.dp + dividerWidth + gap * 4
    val textWidth = inner - coverSize - 8.dp - controlsWidth

    Box(
        modifier = roundedCard(palette.card).fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 背景进度：播放到哪，整张卡就亮到哪。
        if (snapshot.hasSong && snapshot.durationMs > 0L) {
            val fraction = snapshot.progress().coerceIn(0f, 1f)
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = GlanceModifier.width(size.width * fraction)
                        .fillMaxHeight()
                        .background(palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.18f)),
                ) {}
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(rowHeight).padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.size(coverSize)
                    .clickable(openAppAction(context)),
                radius = coverSize / 4,
                iconSize = 18.dp,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.width(textWidth)
                    .clickable(openAppAction(context)),
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
                // 控制簇作为外层行的第 4 个子元素，六个槽位均在 Glance 的 10 个上限内。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BannerSlot(btn, controlHeight, prevAction()) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_prev),
                            contentDescription = context.getString(R.string.widget_action_prev),
                            modifier = GlanceModifier.size(iconSize),
                            colorFilter = ColorFilter.tint(ColorProvider(palette.onCard)),
                        )
                    }
                    BannerSlot(btn + gap, controlHeight, playPauseAction()) {
                        PlayButton(snapshot.isPlaying, palette, controlHeight)
                    }
                    BannerSlot(btn + gap, controlHeight, nextAction()) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_next),
                            contentDescription = context.getString(R.string.widget_action_next),
                            modifier = GlanceModifier.size(iconSize),
                            colorFilter = ColorFilter.tint(ColorProvider(palette.onCard)),
                        )
                    }
                    BannerSlot(1.dp + gap, controlHeight) {
                        Box(
                            modifier = GlanceModifier.width(1.dp).height(18.dp)
                                .background(palette.track),
                        ) {}
                    }
                    BannerSlot(btn + gap, controlHeight, fmAction()) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_fm),
                            contentDescription = context.getString(R.string.widget_action_fm),
                            modifier = GlanceModifier.size(iconSize),
                            colorFilter = ColorFilter.tint(
                                ColorProvider(if (snapshot.isFm) palette.accent else palette.secondary),
                            ),
                        )
                    }
                    BannerSlot(btn, controlHeight, likeAction()) {
                        Image(
                            provider = ImageProvider(if (liked) R.drawable.ic_widget_like_fill else R.drawable.ic_widget_like),
                            contentDescription = context.getString(
                                if (liked) R.string.widget_action_unlike else R.string.widget_action_like,
                            ),
                            modifier = GlanceModifier.size(iconSize),
                            colorFilter = ColorFilter.tint(
                                ColorProvider(if (liked) palette.accent else palette.secondary),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 横条控制槽：内容居中，按钮的整个槽位都响应点击。 */
@Composable
private fun BannerSlot(
    width: Dp,
    height: Dp,
    action: Action? = null,
    content: @Composable () -> Unit,
) {
    val modifier = GlanceModifier.width(width).height(height)
    Box(
        modifier = if (action != null) modifier.clickable(action) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// —————————————————————— 布局 2：2×3 竖版方块 ——————————————————————

/** 保持封面高度预算沿用原播放键尺寸，避免改变封面与信息区的排布。 */
private fun tileCoverPlayBudget(inner: Dp): Dp = (inner * 0.38f).coerceIn(30.dp, 46.dp)

/**
 * 2×3 封面边长（正方形，宽 = 高）：宽度不超过内容宽度，高度不超过「可用高度 − 信息块自然高度」。
 *
 * 封面的绘制、点击区和圆角比例全部走这一个函数，避免三处各算一遍导致
 * 容器与图片比例不一致（容器比图片扁就会把封面压成横幅，人物被拉长）。
 */
private fun tileCoverSide(size: DpSize): Dp {
    val inner = (size.width - 20.dp).coerceAtLeast(40.dp)
    val avail = (size.height - 20.dp).coerceAtLeast(60.dp)
    // 71dp = 标题 20 + 间距 2 + 副标题 15 + 间距 4 + 进度 22 + 间距 8；再加播放键高度。
    val content = 71.dp + tileCoverPlayBudget(inner)
    return inner.coerceAtMost((avail - content).coerceAtLeast(44.dp))
}

/**
 * 自上而下：1:1 方形封面、歌名歌手、短进度与时长、三键居中。
 * 封面尺寸由 [tileCoverSide] 统一给出（正方形，绝不拉伸），按钮尺寸按可用宽度反推。
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
    val pad = 10.dp
    val inner = (size.width - pad * 2).coerceAtLeast(60.dp)
    val playSize = (inner * 0.40f).coerceIn(36.dp, 52.dp)
    val baseSideSize = (playSize * 0.78f).coerceIn(26.dp, 41.dp)
    val baseGap = (playSize * 0.30f).coerceIn(8.dp, 16.dp)
    // 仅在超宽时同比缩小侧键与间距，播放键尺寸保持不变，左右至少各留 4dp。
    val scale = ((inner - 8.dp - playSize).value /
        (baseSideSize * 2 + baseGap * 2).value).coerceIn(0f, 1f)
    val sideSize = baseSideSize * scale
    val gap = baseGap * scale
    val sideIconSize = (sideSize * 0.62f).coerceIn(16.dp, 20.dp)
    val coverSide = tileCoverSide(size)

    // 此列有歌时有 9 个直接子元素，无歌时为 5 个，均在 Glance 的 10 个上限内。
    Column(
        modifier = roundedCard(palette.card)
            .fillMaxSize()
            .padding(pad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 封面容器必须是正方形：宽高一致、图片 Crop 填充，任何一边被拉伸都会让人物变形。
        // 整块封面点击打开 App（不再左右分区切歌，切歌交给下面控制键）。
        Box(
            modifier = GlanceModifier.size(coverSide)
                .clickable(openAppAction(context)),
        ) {
            CoverArt(
                cover = cover,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
                radius = WidgetTokens.CardRadius,
                iconSize = 26.dp,
                fill = true,
            )
        }

        Spacer(GlanceModifier.height(10.dp))
        if (snapshot.hasSong) {
            Text(
                text = ellipsize(context, snapshot.title, inner, 13f, medium = true),
                maxLines = 1,
                style = titleStyle(palette.onCard, 13.sp),
                modifier = GlanceModifier.clickable(openAppAction(context)),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = ellipsize(context, snapshot.subtitle(context), inner, 10.5f),
                maxLines = 1,
                style = bodyStyle(palette.secondary, 10.5.sp),
                modifier = GlanceModifier.clickable(openAppAction(context)),
            )
        } else {
            Text(
                text = ellipsize(context, snapshot.statusTitle(context), inner, 13f, medium = true),
                maxLines = 1,
                style = titleStyle(palette.onCard, 13.sp),
                modifier = GlanceModifier.clickable(openAppAction(context)),
            )
        }

        // 有歌时两段弹性间隙平分富余高度；空态只保留状态标题下方的一段。
        Spacer(GlanceModifier.defaultWeight())
        if (snapshot.hasSong) {
            ProgressRow(snapshot, palette, inner, compact = true)
            Spacer(GlanceModifier.defaultWeight())
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot.hasSong) {
                BareIcon(
                    iconRes = R.drawable.ic_widget_prev,
                    descriptionRes = R.string.widget_action_prev,
                    action = prevAction(),
                    tint = palette.onCard,
                    size = sideSize,
                    iconSize = sideIconSize,
                )
                Spacer(GlanceModifier.width(gap))
            }
            PlayButton(snapshot.isPlaying, palette, playSize)
            if (snapshot.hasSong) {
                Spacer(GlanceModifier.width(gap))
                BareIcon(
                    iconRes = R.drawable.ic_widget_next,
                    descriptionRes = R.string.widget_action_next,
                    action = nextAction(),
                    tint = palette.onCard,
                    size = sideSize,
                    iconSize = sideIconSize,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        }
        // 末尾不留 Spacer：根 Column 直接子元素必须 ≤10（Glance 的 setChildren 只取前 10 个）。
    }
}

// —————————————————————— 通用零件 ——————————————————————

private fun titleStyle(
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Medium,
): TextStyle = TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

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
    height: Dp = size,
) {
    val description = LocalContext.current.getString(descriptionRes)
    Box(
        modifier = GlanceModifier.width(size).height(height).clickable(action),
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
    height: Dp = size,
) {
    BareIcon(
        iconRes = if (liked) R.drawable.ic_widget_like_fill else R.drawable.ic_widget_like,
        descriptionRes = if (liked) R.string.widget_action_unlike else R.string.widget_action_like,
        action = likeAction(),
        tint = tintOverride ?: if (liked) palette.accent else palette.secondary,
        size = size,
        iconSize = iconSize,
        height = height,
    )
}

/** 1dp 竖分隔线：把「播放控件」和「快捷功能」分开。 */
@Composable
private fun VDivider(color: Color, gap: Dp) {
    Spacer(GlanceModifier.width(gap / 2))
    Box(
        modifier = GlanceModifier.width(1.dp).height(18.dp)
            .background(color),
    ) {}
    Spacer(GlanceModifier.width(gap / 2))
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
 * 进度行：`当前时间 ──条── 总时长`，整行撑满给定宽度。
 *
 * 两端对齐的口径：Glance 1.1.1 的 Text 没有 textAlign，固定宽的槽位会让右端时长
 * 在槽内左对齐、和滑轨端点错开。这里把两侧时间槽量成文字实际宽度，
 * 左时间的右边缘、右时间的左边缘就都紧贴滑轨两端。
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
    val context = LocalContext.current
    val gap = if (compact) 6.dp else 7.dp
    val fontSize = if (compact) 9f else 10f
    val hasDuration = snapshot.hasSong && snapshot.durationMs > 0L
    val startText = if (hasDuration) {
        formatClock((snapshot.progress() * snapshot.durationMs).toLong())
    } else {
        "--:--"
    }
    val endText = if (hasDuration) formatClock(snapshot.durationMs) else "--:--"
    val timeWidth = clockWidth(context, startText, endText, sizeSp = fontSize).coerceAtLeast(20.dp)
    val barWidth = (width - (timeWidth + gap) * 2).coerceAtLeast(20.dp)

    Row(
        modifier = GlanceModifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = startText,
            maxLines = 1,
            style = bodyStyle(palette.secondary, fontSize.sp),
            modifier = GlanceModifier.width(timeWidth),
        )
        Spacer(GlanceModifier.width(gap))
        SeekBar(palette, barWidth, snapshot.progress().coerceIn(0f, 1f), hasDuration)
        Spacer(GlanceModifier.width(gap))
        Text(
            text = endText,
            maxLines = 1,
            style = bodyStyle(palette.secondary, fontSize.sp),
            modifier = GlanceModifier.width(timeWidth),
        )
    }
}

/** 时间文字的实际宽度（dp），两端时间共用同一槽宽，避免每秒跳动。 */
private fun clockWidth(
    context: Context,
    startText: String,
    endText: String,
    sizeSp: Float,
): Dp {
    val metrics = context.resources.displayMetrics
    val widest = maxOf(
        measureWidth(context, startText, sizeSp),
        measureWidth(context, endText, sizeSp),
        measureWidth(context, "--:--", sizeSp),
    )
    // +1dp 余量：测量与实际渲染的取整差可能把末位数字挤掉。
    return (widest / metrics.density + 1f).dp
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

/** 空状态点卡片进 App，有歌时进播放页的旧行为已取消：组件内点击一律进 App 主界面。 */

// —————————————————————— 两个独立的桌面小组件 ——————————————————————

/** 4×1：横向扁长条，整卡背景即进度。 */
class BannerWidget : BaseMusicWidget(WidgetLayout.BANNER)

/** 2×3：竖版卡片，封面在上、控件在下。 */
class TileWidget : BaseMusicWidget(WidgetLayout.TILE)
