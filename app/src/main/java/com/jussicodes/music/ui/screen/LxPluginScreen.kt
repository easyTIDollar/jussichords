package com.jussicodes.music.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.lx.ImportResult
import com.jussicodes.music.lx.LxPluginItem
import com.jussicodes.music.lx.LxOnlineSources
import com.jussicodes.music.lx.LxPluginManager
import com.jussicodes.music.lx.OnlineSource
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.ScaledDialogContent
import com.jussicodes.music.ui.icons.Script
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * LX 脚本音源管理页：导入 / 启停 / 删除 / 长按拖动排序 / 多选批量操作。
 * 与设置页「音源三」同一份数据源 [LxPluginManager.plugins]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxPluginScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by LxPluginManager.plugins.collectAsState(initial = emptyList())

    var importing by remember { mutableStateOf(false) }
    var pendingDuplicate by remember { mutableStateOf<Pair<LxPluginItem, LxPluginItem>?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    var urlDialogVisible by remember { mutableStateOf(false) }
    var onlineListVisible by remember { mutableStateOf(false) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }

    // ---- 列表顺序（长按拖动）与多选 ----
    val listState = rememberLazyListState()
    var displayed by remember { mutableStateOf(plugins) }
    var dragChanged by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 用 item key（脚本 id）换算插件下标：LazyColumn 顶部还有说明/按钮等非插件条目，
    // 直接拿 from.index/to.index 当插件下标会错位。
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        val fromIdx = displayed.indexOfFirst { it.id == fromId }
        val toIdx = displayed.indexOfFirst { it.id == toId }
        if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) {
            return@rememberReorderableLazyListState
        }
        displayed = displayed.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        dragChanged = true
    }

    // 拖拽中不要被 DataStore 回流覆盖本地顺序
    LaunchedEffect(plugins) {
        if (!reorderableState.isAnyItemDragging) displayed = plugins
    }
    // 松手才落盘，避免每一帧都写一次
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && dragChanged) {
            dragChanged = false
            LxPluginManager.reorder(displayed.map { it.id })
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (result == null) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            } else {
                when (val r = LxPluginManager.importScript("", result)) {
                    is ImportResult.Success -> {
                        LxPluginManager.confirmImport(r.item, null)
                        Toast.makeText(context, "已导入 ${r.item.name}", Toast.LENGTH_SHORT).show()
                    }
                    is ImportResult.Duplicate -> pendingDuplicate = r.existing to r.newItem
                    is ImportResult.Failure ->
                        Toast.makeText(context, "导入失败：${r.message}", Toast.LENGTH_LONG).show()
                }
            }
            importing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "已选 ${selected.size} 项" else "LX 脚本音源") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(onClick = {
                            val allIds = displayed.map { it.id }
                            selected = if (allIds.isNotEmpty() && selected.size == allIds.size) {
                                emptySet()
                            } else {
                                allIds.toSet()
                            }
                        }) {
                            Text(
                                if (displayed.isNotEmpty() && selected.size == displayed.size) "取消全选" else "全选"
                            )
                        }
                        TextButton(onClick = {
                            selectionMode = false
                            selected = emptySet()
                        }) { Text("完成") }
                    } else if (displayed.isNotEmpty()) {
                        TextButton(onClick = {
                            selectionMode = true
                            selected = emptySet()
                        }) { Text("选择") }
                    }
                },
            )
        }
    ) { padding ->
        val bottomInset = MiniPlayerHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                top = padding.calculateTopPadding(),
                end = 12.dp,
                bottom = bottomInset + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "在独立 WebView 沙盒里运行 LX Music 社区脚本，按列表顺序依次尝试解析直链。" +
                        "长按卡片可拖动排序，右上角「选择」进入多选批量启停/删除。" +
                        "脚本可在沙盒内自行发起网络请求；仅在你主动启用时参与解析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "内置音源仅作参考，不保证可用，请自行导入所需音源。" +
                            "作者不对任何第三方 LX 音源负责，" +
                            "音源脚本由其作者与来源站点自行提供，内容与版权责任由原作者及使用者自行承担。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            if (importing) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("text/javascript", "text/plain", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("导入文件") }
                    OutlinedButton(
                        onClick = { urlDialogVisible = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("通过链接导入") }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onlineListVisible = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("在线音源列表") }
            }
            if (plugins.isEmpty()) {
                item {
                    Text(
                        text = "还没有脚本。导入 LX Music 社区的 .js 音源脚本后即可使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (selectionMode) {
                item {
                    val deletableCount = selected.count { it != LxPluginManager.BUILTIN_ID }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    val ids = selected
                                    scope.launch { LxPluginManager.setEnabled(ids, true) }
                                }
                            ) { Text("启用") }
                            TextButton(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    val ids = selected
                                    scope.launch { LxPluginManager.setEnabled(ids, false) }
                                }
                            ) { Text("停用") }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                enabled = deletableCount > 0,
                                onClick = { batchDeleteConfirm = true }
                            ) {
                                Text(
                                    text = "删除",
                                    color = if (deletableCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
            items(displayed, key = { it.id }) { item ->
                ReorderableItem(
                    state = reorderableState,
                    key = item.id,
                    enabled = !selectionMode
                ) { isDragging ->
                    // 长按整张卡片即进入拖动；多选模式下拖动手势关掉，点击改成勾选。
                    val rowModifier = if (selectionMode) {
                        Modifier
                    } else {
                        Modifier.longPressDraggableHandle()
                    }
                    LxPluginRow(
                        item = item,
                        isBuiltin = item.id == LxPluginManager.BUILTIN_ID,
                        selectionMode = selectionMode,
                        selected = selected.contains(item.id),
                        dragging = isDragging,
                        modifier = rowModifier,
                        onToggle = { enabled ->
                            scope.launch { LxPluginManager.toggle(item.id, enabled) }
                        },
                        onSelectToggle = {
                            selected = if (selected.contains(item.id)) {
                                selected - item.id
                            } else {
                                selected + item.id
                            }
                        },
                        onDelete = { if (item.id != LxPluginManager.BUILTIN_ID) removingId = item.id }
                    )
                }
            }
        }
    }

    pendingDuplicate?.let { (existing, incoming) ->
        DuplicateScriptDialog(
            existingName = existing.name,
            incomingVersion = incoming.version,
            onOverwrite = {
                scope.launch { LxPluginManager.confirmImport(incoming, existing.id) }
                pendingDuplicate = null
            },
            onKeepBoth = {
                scope.launch { LxPluginManager.confirmImport(incoming.copy(name = incoming.name), null) }
                pendingDuplicate = null
            },
            onDismiss = { pendingDuplicate = null }
        )
    }

    if (batchDeleteConfirm) {
        val count = selected.count { it != LxPluginManager.BUILTIN_ID }
        Dialog(
            onConfirmation = {
                val ids = selected
                scope.launch {
                    LxPluginManager.removeAll(ids)
                    selected = emptySet()
                    selectionMode = false
                }
                batchDeleteConfirm = false
            },
            onDismissRequest = { batchDeleteConfirm = false },
            dialogTitle = "删除脚本",
            dialogText = "确定删除选中的 $count 个脚本？沙盒会一并销毁。"
        )
    }

    removingId?.let { id ->
        val target = plugins.firstOrNull { it.id == id }
        Dialog(
            onConfirmation = {
                scope.launch { LxPluginManager.remove(id) }
                removingId = null
            },
            onDismissRequest = { removingId = null },
            dialogTitle = "删除脚本",
            dialogText = "确定删除「${target?.name ?: ""}」？沙盒会一并销毁。",
        )
    }

    if (onlineListVisible) {
        OnlineSourcesDialog(
            installedNames = plugins.map { it.name }.toSet(),
            onDismiss = { onlineListVisible = false },
            onInstall = { source -> LxPluginManager.installOnlineSource(source) }
        )
    }

    if (urlDialogVisible) {
        UrlScriptDialog(
            onDismiss = { urlDialogVisible = false },
            onConfirm = { url ->
                urlDialogVisible = false
                importing = true
                scope.launch {
                    val script = LxPluginManager.downloadScript(url)
                    if (script == null) {
                        Toast.makeText(context, "下载脚本失败", Toast.LENGTH_LONG).show()
                    } else {
                        when (val r = LxPluginManager.importScript("", script)) {
                            is ImportResult.Success -> {
                                LxPluginManager.confirmImport(r.item, null)
                                Toast.makeText(context, "已导入 ${r.item.name}", Toast.LENGTH_SHORT).show()
                            }
                            is ImportResult.Duplicate -> pendingDuplicate = r.existing to r.newItem
                            is ImportResult.Failure ->
                                Toast.makeText(context, "导入失败：${r.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    importing = false
                }
            }
        )
    }
}

@Composable
private fun LxPluginRow(
    item: LxPluginItem,
    isBuiltin: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onSelectToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = if (selectionMode) modifier.clickable(onClick = onSelectToggle) else modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Script, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (item.version.isNotBlank()) add("v${item.version}")
                    if (item.author.isNotBlank()) add(item.author)
                    if (item.sources.isNotEmpty()) add(item.sources.joinToString("/"))
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onSelectToggle() })
            } else {
                Switch(checked = item.isEnabled, onCheckedChange = onToggle)
            }
        }
        // 多选时操作统一走顶部工具条，单条不再露按钮
        if (!selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = !isBuiltin,
                    onClick = onDelete
                ) {
                    Text(
                        text = "删除",
                        color = if (isBuiltin) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateScriptDialog(
    existingName: String,
    incomingVersion: String,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit
) {
    ComposeDialog(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp)) {
                    Text("脚本重名", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = "已存在同名脚本「$existingName」" +
                            (if (incomingVersion.isNotBlank()) "（新版本 $incomingVersion）" else "") + "。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = onKeepBoth) { Text("仍然导入") }
                        TextButton(onClick = onOverwrite) { Text("覆盖原脚本") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlScriptDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    ComposeDialog(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp)) {
                    Text("通过链接导入脚本", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(top = 12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://…/source.js") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(
                            enabled = url.isNotBlank(),
                            onClick = { onConfirm(url.trim()) }
                        ) { Text("导入") }
                    }
                }
            }
        }
    }
}
/** 在线音源列表弹窗：拉 lx.jussichords.kdns.fr 的公开列表，点「安装」下载脚本入库。 */
@Composable
private fun OnlineSourcesDialog(
    installedNames: Set<String>,
    onDismiss: () -> Unit,
    onInstall: suspend (OnlineSource) -> String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<OnlineSource>>(emptyList()) }
    var installingSlug by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(installedNames) }

    suspend fun load() {
        loading = true
        error = null
        LxOnlineSources.fetch()
            .onSuccess { sources = it }
            .onFailure { error = it.message ?: "网络请求失败" }
        loading = false
    }

    fun install(source: OnlineSource) {
        if (installingSlug != null) return
        installingSlug = source.slug
        scope.launch {
            val name = onInstall(source)
            installingSlug = null
            if (name != null) {
                installed = installed + source.name + name
                Toast.makeText(context, "已安装 $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "安装失败：脚本下载或校验没通过", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    ComposeDialog(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("在线音源", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "来自 lx.jussichords.kdns.fr 的公开音源列表",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            enabled = !loading,
                            onClick = { scope.launch { load() } }
                        ) { Text("刷新") }
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        loading -> Box(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }

                        error != null -> Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = "获取列表失败：$error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                        }

                        sources.isEmpty() -> Text(
                            text = "线上暂时没有可用音源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sources, key = { it.slug.ifBlank { it.url } }) { source ->
                                OnlineSourceRow(
                                    source = source,
                                    isInstalled = installed.contains(source.name),
                                    installing = installingSlug == source.slug,
                                    onInstall = { install(source) }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSourceRow(
    source: OnlineSource,
    isInstalled: Boolean,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (source.author.isNotBlank()) add(source.author)
                    if (source.sizeText.isNotBlank()) add(source.sizeText)
                    if (source.versionNo > 0) add("v${source.versionNo}")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (source.description.isNotBlank()) {
                    Text(
                        text = source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (installing) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (isInstalled) {
                // 只做「有没有装」，不做版本比较：装了就直接置灰。
                TextButton(onClick = {}, enabled = false) { Text("已安装") }
            } else {
                TextButton(onClick = onInstall) { Text("安装") }
            }
        }
    }
}
