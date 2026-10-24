package com.jussicodes.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.LandscapeDetailLeftFraction
import com.jussicodes.music.constants.ThumbnailCornerRadius

/**
 * 横屏（矮屏）详情页的「主从双栏」骨架：左侧展示栏 + 右侧曲目列表。
 *
 * 比例取 **左 40% / 右 60%**（[LandscapeDetailLeftFraction]）。
 *
 * 左栏内部是**横向两块**（不是上下堆叠）：
 * 1. 左块：封面 —— 边长取「剩余高度」与「半块宽」的较小值，居中放置。
 *    不能用 fillMaxWidth + aspectRatio(1f)：会撑破 411dp 高的视口。
 * 2. 右块：标题 / 艺术家 / 时间 / 简介 + **竖向**排的 3 枚药丸按钮（收藏 /
 *    播放 / 评论）。简介超过 3 行截断，点击标题或简介弹出全屏简介浮层
 *    （半透明压暗背景，点背景或关闭按钮退出）。
 *
 * 左栏顶部仍有操作按钮行（返回 / 置顶 / 分享等，横跨整栏）。
 * 右栏：可选列表头部（搜索框）+ 纵向滚动列表。
 */
@Composable
fun MasterDetailLandscape(
    modifier: Modifier = Modifier,
    /** 左栏顶部的操作按钮行。 */
    topActions: @Composable RowScope.() -> Unit = {},
    /** 封面内容（调用方自行提供 AsyncImage / 共享元素与点击行为）。 */
    cover: @Composable () -> Unit = {},
    /** 浮层标题（受控浮层显示用）。标题展示本身由 headerContent 负责。 */
    title: String = "",
    subtitle: (@Composable () -> Unit) = {},
    meta: String? = null,
    description: String? = null,
    /**
     * 封面上方的标题区（歌单：歌单名；专辑：专辑名+歌手+日期）。由调用方
     * 组装，浮层触发逻辑也在调用方 —— 骨架只管摆位。
     */
    headerContent: (@Composable () -> Unit) = {},
    /**
     * 标题点击回调（骨架在浮层可见性上持有状态，由调用方决定 header 里的
     * 哪个元素触发 —— 通常是歌单名）。
     */
    onTitleClick: () -> Unit = {},
    /** 浮层可见性（受控）。null = 骨架内部自持（专辑页无浮层需求）。 */
    descriptionOverlayVisible: Boolean? = null,
    /** 浮层关闭回调（受控模式下点背景/关闭按钮时回调）。 */
    onDescriptionOverlayDismiss: () -> Unit = {},
    /**
     * 药丸按钮内容（**横向**排在封面下方，RowScope 接收者可分配宽度）。
     */
    pillActions: @Composable RowScope.() -> Unit = {},
    /** 右栏列表上方的固定区域（搜索框等）。 */
    listHeader: (@Composable () -> Unit) = {},
    /**
     * 右栏曲目列表内容。
     *
     * 必须是**非 composable** 的扩展函数类型 `LazyListScope.() -> Unit`（与
     * LazyColumn 的 content 参数同一形态）：`LazyColumn { }` 的 builder lambda
     * 本身不是 composable 上下文，只有每个 item 的行内容才是，所以任何
     * @Composable 形态的 listContent 在这里都会报
     * 「@Composable invocations can only happen from the context of a @Composable function」。
     * 行内容（itemsIndexed 的 lambda）自带 @Composable，不受影响。
     */
    listContent: LazyListScope.() -> Unit = {},
    /**
     * 右栏**自定义内容**（非列表形态）。传了就用它替换默认的 LazyColumn，
     * 适合「右栏是 tab + pager」这类结构（歌手页横屏：探索/歌曲/专辑三 tab）。
     * 不传就走 listContent 的普通列表。
     */
    rightContent: (@Composable () -> Unit)? = null,
) {
    // 浮层可见性受控：由调用方持有状态（歌单页需要从标题点击打开浮层）。
    // descriptionOverlayVisible != null 时完全受控；否则骨架内部自持（专辑页不需要浮层）。
    var internalOverlay by remember { mutableStateOf(false) }
    val showDescriptionOverlay = descriptionOverlayVisible ?: internalOverlay
    val hasDescription = !description.isNullOrBlank()

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── 顶栏：横跨整个屏幕宽度 ─────────────────────────
            // 早先顶栏放在左栏内部（只占 40% 宽），搜索/更多按钮卡在屏幕
            // 中间偏左的位置。现在顶栏提到最外层，横跨左右两栏：返回在左端，
            // 搜索 / 更多由调用方用 Spacer(weight) 推到最右端。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = topActions
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // ── 左侧展示栏（40%） ──────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(LandscapeDetailLeftFraction)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                ) {
                    // 结构（自上而下，整块在左栏剩余空间垂直居中）：
                    // 1. headerContent —— 标题/歌手/日期（由调用方给，歌单页点标题
                    //    展开全屏简介浮层；专辑页纯展示）
                    // 2. 封面：缩小并水平居中
                    // 3. 3 枚按钮：横排，左右与封面两端对齐
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        headerContent()
                        Spacer(Modifier.height(10.dp))
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // coverSize 缩小（0.6 倍可用空间），保证同屏能看到
                            // 标题栏 + 封面 + 按钮排。
                            val coverSize = minOf(maxWidth, maxHeight) * 0.6f
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(coverSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                ) { cover() }
                                Spacer(Modifier.height(10.dp))
                                // 3 枚按钮：横排，等分左栏宽度（与竖屏页一致，不再与封面宽度对齐）
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = pillActions
                                )
                            }
                        }
                    }
                }
                // ── 右侧内容区（60%） ─────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f - LandscapeDetailLeftFraction)
                        .fillMaxHeight()
                ) {
                    listHeader()
                    if (rightContent != null) {
                        // 自定义右栏（tab + pager 等），占满剩余高度
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            rightContent()
                        }
                    } else {
                        // LazyColumn 的 content 是接收者函数类型，直接传值，别包一层 lambda。
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            content = listContent
                        )
                    }
                }
            }
        }

        // ── 全屏简介浮层：半透明压暗背景 + 居中可滚动简介 ──
        AnimatedVisibility(
            visible = showDescriptionOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        if (descriptionOverlayVisible != null) {
                            onDescriptionOverlayDismiss()
                        } else {
                            internalOverlay = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (descriptionOverlayVisible != null) {
                                onDescriptionOverlayDismiss()
                            } else {
                                internalOverlay = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                    }
                    // 日期/播放数：与简介正文区分 —— 小字号 + 主色调 + 加大上间距
                    meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 按钮排的标准高度：圆形图标按钮的直径 = 胶囊按钮的高度，两者等高对齐。 */
val DetailActionRowHeight = 36.dp

/**
 * 主操作胶囊按钮（「播放」）：浅绿（primaryContainer）填充高亮，图标 + 文字，
 * 宽度**自适应内容** —— 不做 weight/弹性伸缩，永远是舒展的药丸形。
 */
@Composable
fun DetailPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = modifier.height(DetailActionRowHeight)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 次要操作圆角矩形线框图标按钮（「已收藏」/「评论」）：无文字，深底细描边，
 * 尺寸与主胶囊等高 [DetailActionRowHeight] 的正方形（圆角矩形）。
 */
@Composable
fun DetailCircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(DetailActionRowHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
