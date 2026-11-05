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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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

enum class WidgetStyle { CLASSIC, SQUARE, CAPSULE, FM, VINYL }

/**
 * 五款组件共用一套组合逻辑：快照 + 红心（含乐观覆盖）+ 封面位图。
 */
abstract class BaseMusicWidget(
    private val style: WidgetStyle,
    private val coverStyle: CoverStyle,
    private val cornerRatio: Float,
    private val sizes: SizeMode,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = sizes

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStateStore.read(context)
        val cover = if (snapshot.hasSong) {
            WidgetCoverLoader.load(context, snapshot, coverStyle, cornerRatio)
        } else {
            null
        }
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
                palette = if (style == WidgetStyle.FM) WidgetPalette.fm() else WidgetPalette.of(context),
                context = context,
            )
        }
    }
}

object ClassicWidget : BaseMusicWidget(
    WidgetStyle.CLASSIC, CoverStyle.ROUNDED, 0.24f, SizeMode.Responsive(WidgetSizes.Classic)
)

object SquareWidget : BaseMusicWidget(
    WidgetStyle.SQUARE, CoverStyle.ROUNDED, 0.18f, SizeMode.Responsive(WidgetSizes.Square)
)

object CapsuleWidget : BaseMusicWidget(
    WidgetStyle.CAPSULE, CoverStyle.CIRCLE, 0f, SizeMode.Responsive(WidgetSizes.Capsule)
)

object FmWidget : BaseMusicWidget(
    WidgetStyle.FM, CoverStyle.ROUNDED, 0.18f, SizeMode.Responsive(WidgetSizes.Fm)
)

object VinylWidget : BaseMusicWidget(
    WidgetStyle.VINYL, CoverStyle.VINYL, 0f, SizeMode.Responsive(WidgetSizes.Vinyl)
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
        WidgetStyle.FM -> FmLayout(snapshot, liked, palette, context)
        WidgetStyle.VINYL -> VinylLayout(snapshot, liked, cover, palette, context)
    }
}

// —————————————————————— 通用零件 ——————————————————————

@Composable
private fun ControlIcon(
    iconRes: Int,
    description: String,
    action: androidx.glance.action.Action,
    tint: Color,
) {
    Box(
        modifier = GlanceModifier.size(48.dp).clickable(action),
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
                    radius = 16.dp,
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
                modifier = GlanceModifier.defaultWeight().clickable(openPlayerAction(context)),
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
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!snapshot.isFm) ControlIcon(R.drawable.ic_widget_prev, "上一首", prevAction(), palette.onCard)
                ControlIcon(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (snapshot.isPlaying) "暂停" else "播放",
                    action = playPauseAction(),
                    tint = palette.onCard,
                )
                ControlIcon(R.drawable.ic_widget_next, "下一首", nextAction(), palette.onCard)
                Spacer(GlanceModifier.defaultWeight())
                ControlIcon(
                    iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
                    description = if (liked) "取消喜欢" else "喜欢",
                    action = likeAction(),
                    tint = if (liked) palette.accent else palette.secondary,
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
                    .clickable(openPlayerAction(context)),
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

// —————————————————————— 3. 胶囊 4×1 ——————————————————————

@Composable
private fun CapsuleLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val buttons = if (snapshot.hasSong) (if (snapshot.isFm) 2 else 3) else 0
    val textWidth = size.width - 20.dp - 48.dp - 8.dp - (48.dp * buttons) - 4.dp

    Row(
        modifier = roundedCard(palette.card).fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            cover = cover,
            modifier = GlanceModifier.size(40.dp).clickable(openPlayerAction(context)),
            palette = palette,
            iconSize = 18.dp,
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().clickable(openPlayerAction(context)),
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
            if (!snapshot.isFm) ControlIcon(R.drawable.ic_widget_prev, "上一首", prevAction(), palette.onCard)
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

// —————————————————————— 4. 私人 FM 4×2（固定深色） ——————————————————————

@Composable
private fun FmLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val textWidth = size.width - 28.dp

    Column(modifier = roundedCard(palette.card).fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(6.dp)
                    .then(roundedCard(palette.accent, radius = 3.dp, drawable = R.drawable.widget_cover_placeholder)),
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_fm_label),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (snapshot.hasSong) {
            Text(
                text = ellipsize(context, snapshot.title, textWidth, 17f, bold = true),
                maxLines = 1,
                style = titleStyle(palette.onCard, 17.sp),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = ellipsize(context, snapshot.artistLine(context), textWidth, 12f),
                maxLines = 1,
                style = bodyStyle(palette.secondary, 12.sp),
            )
        } else {
            EmptyBlock(
                snapshot = snapshot,
                palette = palette,
                context = context,
                titleSize = 15.sp,
                hintSize = 11.sp,
                maxWidth = textWidth,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (snapshot.hasSong) {
                ControlIcon(
                    iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
                    description = if (liked) "取消喜欢" else "喜欢",
                    action = likeAction(),
                    tint = if (liked) palette.accent else palette.secondary,
                )
                ControlIcon(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (snapshot.isPlaying) "暂停" else "播放",
                    action = playPauseAction(),
                    tint = palette.onCard,
                )
                ControlIcon(R.drawable.ic_widget_next, "下一首", nextAction(), palette.onCard)
            } else {
                Spacer(GlanceModifier.defaultWeight())
                ControlIcon(R.drawable.ic_widget_fm, "私人FM", fmAction(), palette.accent)
            }
        }
    }
}

// —————————————————————— 5. 唱片 4×4 ——————————————————————

@Composable
private fun VinylLayout(
    snapshot: WidgetSnapshot,
    liked: Boolean,
    cover: Bitmap?,
    palette: WidgetPalette,
    context: Context,
) {
    val size = LocalSize.current
    val artSize = (minOf(size.width, size.height).value * 0.46f).dp
    val textWidth = size.width - 24.dp

    Column(
        modifier = roundedCard(palette.card).fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (snapshot.hasSong) {
                Cover(
                    cover = cover,
                    modifier = GlanceModifier.size(artSize).clickable(openPlayerAction(context)),
                    palette = palette,
                    iconSize = 30.dp,
                )
            } else {
                Box(
                    modifier = GlanceModifier.size(artSize)
                        .then(roundedCard(palette.chip, radius = artSize / 2)),
                ) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize()
                            .then(roundedCard(palette.placeholderTop, radius = artSize / 4)),
                    ) {}
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Column(
            modifier = GlanceModifier.fillMaxWidth().clickable(openPlayerAction(context)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (snapshot.hasSong) {
                Text(
                    text = ellipsize(context, snapshot.title, textWidth, 16f, bold = true),
                    maxLines = 1,
                    style = titleStyle(palette.onCard, 16.sp).copy(textAlign = TextAlign.Center),
                )
                Text(
                    text = ellipsize(context, snapshot.artistAlbumLine(context), textWidth, 12f),
                    maxLines = 1,
                    style = bodyStyle(palette.secondary, 12.sp).copy(textAlign = TextAlign.Center),
                )
            } else {
                Text(
                    text = ellipsize(context, snapshot.statusTitle(context), textWidth, 15f, bold = true),
                    maxLines = 1,
                    style = titleStyle(palette.onCard, 15.sp).copy(textAlign = TextAlign.Center),
                )
                Text(
                    text = ellipsize(context, snapshot.statusHint(context), textWidth, 11f),
                    maxLines = 1,
                    style = bodyStyle(palette.secondary, 11.sp).copy(textAlign = TextAlign.Center),
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (snapshot.hasSong) {
                ControlIcon(
                    iconRes = if (liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite,
                    description = if (liked) "取消喜欢" else "喜欢",
                    action = likeAction(),
                    tint = if (liked) palette.accent else palette.secondary,
                )
                if (!snapshot.isFm) {
                    ControlIcon(R.drawable.ic_widget_prev, "上一首", prevAction(), palette.onCard)
                }
                ControlIcon(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (snapshot.isPlaying) "暂停" else "播放",
                    action = playPauseAction(),
                    tint = palette.onCard,
                )
                ControlIcon(R.drawable.ic_widget_next, "下一首", nextAction(), palette.onCard)
                ControlIcon(R.drawable.ic_widget_fm, "私人FM", fmAction(), palette.secondary)
            } else {
                ControlIcon(R.drawable.ic_widget_fm, "私人FM", fmAction(), palette.accent)
            }
        }
    }
}
