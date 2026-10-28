package app.uskoritel

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun CleanScreen(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasAccess by remember { mutableStateOf(Perms.hasStorage(ctx)) }
    LaunchedEffect(tick) { hasAccess = Perms.hasStorage(ctx) }
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasAccess = Perms.hasStorage(ctx) }

    var scanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<List<JunkItem>?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<JunkType, Boolean>() }
    var confirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val list = found.orEmpty()
    val chosen = list.filter { selected[it.path] == true }
    val chosenSize = chosen.sumOf { it.size }
    val rootPath = JunkScanner.root.path

    fun startScan() {
        scanning = true
        message = null
        found = null
        scannedCount = 0
        scope.launch {
            val result = JunkScanner.scan { scannedCount = it }
            selected.clear()
            result.forEach { selected[it.path] = it.type.autoSelect }
            found = result
            scanning = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Очистка", "Ненужные файлы во внутренней памяти") }

        if (!hasAccess) {
            item {
                SectionCard {
                    Text(
                        "Чтобы найти мусорные файлы, приложению нужен доступ к памяти телефона. " +
                            "Без твоего подтверждения ничего удаляться не будет.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ctx.openFirst(*Perms.storageIntents(ctx))
                        } else {
                            legacyLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ),
                            )
                        }
                    }) { Text("Дать доступ") }
                }
            }
        } else {
            item {
                Button(
                    onClick = { startScan() },
                    enabled = !scanning && !deleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (found == null) "Найти мусор" else "Сканировать заново", fontWeight = FontWeight.Bold)
                }
            }

            if (scanning) {
                item {
                    SectionCard {
                        Text("Сканирую… проверено файлов: $scannedCount")
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            message?.let { m ->
                item { SectionCard { Text(m, color = Good, fontWeight = FontWeight.SemiBold) } }
            }

            if (found != null && !scanning) {
                if (list.isEmpty()) {
                    item { SectionCard { Text("Мусора не найдено — всё чисто ✨") } }
                }
                JunkType.entries.forEach { type ->
                    val group = list.filter { it.type == type }
                    if (group.isNotEmpty()) {
                        val isOpen = expanded[type] == true
                        item(key = "header_${type.name}") {
                            GroupHeader(type, group, selected, isOpen) { expanded[type] = !isOpen }
                        }
                        if (isOpen) {
                            items(group.take(300), key = { it.path }) { j ->
                                FileRow(j, rootPath, selected[j.path] == true) { selected[j.path] = it }
                            }
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { confirm = true },
                            enabled = chosen.isNotEmpty() && !deleting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Bad),
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (deleting) "Удаляю…" else "Удалить выбранное · ${fmtBytes(chosenSize)}",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        ctx.openFirst(
                            Intent(StorageManager.ACTION_MANAGE_STORAGE),
                            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Открыть системную очистку (кэш всех приложений)") }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Удалить ${chosen.size} шт.?") },
            text = { Text("Освободится ${fmtBytes(chosenSize)}. Удалённые файлы восстановить не получится.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    deleting = true
                    val toDelete = chosen
                    scope.launch {
                        val freed = JunkScanner.delete(toDelete)
                        val gone = toDelete.map { it.path }.toSet()
                        found = list.filterNot { it.path in gone }
                        gone.forEach { selected.remove(it) }
                        message = "Готово! Освобождено ${fmtBytes(freed)}"
                        deleting = false
                    }
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    type: JunkType,
    group: List<JunkItem>,
    selected: SnapshotStateMap<String, Boolean>,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
) {
    val all = group.all { selected[it.path] == true }
    val none = group.none { selected[it.path] == true }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(
                state = when {
                    all -> ToggleableState.On
                    none -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                },
                onClick = {
                    val value = !all
                    group.forEach { selected[it.path] = value }
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onToggleOpen() },
            ) {
                Text(type.title, fontWeight = FontWeight.SemiBold)
                val sizeText = if (type == JunkType.EMPTY) "" else " · ${fmtBytes(group.sumOf { it.size })}"
                Text(
                    "${group.size} шт$sizeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Good,
                )
                Text(
                    type.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleOpen) {
                Icon(
                    if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isOpen) "Свернуть" else "Показать файлы",
                )
            }
        }
    }
}

@Composable
private fun FileRow(item: JunkItem, rootPath: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Column(Modifier.weight(1f)) {
            Text(File(item.path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.path.removePrefix(rootPath),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.type != JunkType.EMPTY) {
            Spacer(Modifier.width(8.dp))
            Text(fmtBytes(item.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}
