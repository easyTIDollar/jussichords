package com.jussicodes.music.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.jussicodes.music.lx.LxPluginManager
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.ScaledDialogContent
import com.jussicodes.music.ui.icons.Script
import kotlinx.coroutines.launch

/**
 * LX 脚本音源管理页：导入 / 启停 / 删除 / 上移下移。
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
                title = { Text("LX 脚本音源") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        val bottomInset = MiniPlayerHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
            itemsIndexed(plugins, key = { _, item -> item.id }) { index, item ->
                LxPluginRow(
                    item = item,
                    isBuiltin = item.id == LxPluginManager.BUILTIN_ID,
                    canMoveUp = index > 0,
                    canMoveDown = index < plugins.lastIndex,
                    onToggle = { enabled ->
                        scope.launch { LxPluginManager.toggle(item.id, enabled) }
                    },
                    onMoveUp = { scope.launch { LxPluginManager.setPriority(item.id, index - 1) } },
                    onMoveDown = { scope.launch { LxPluginManager.setPriority(item.id, index + 1) } },
                    onDelete = { if (item.id != LxPluginManager.BUILTIN_ID) removingId = item.id }
                )
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isBuiltin: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
            Switch(checked = item.isEnabled, onCheckedChange = onToggle)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(enabled = canMoveUp, onClick = onMoveUp) { Text("上移") }
            TextButton(enabled = canMoveDown, onClick = onMoveDown) { Text("下移") }
            TextButton(
                enabled = !isBuiltin,
                onClick = onDelete
            ) { Text("删除", color = if (isBuiltin) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
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