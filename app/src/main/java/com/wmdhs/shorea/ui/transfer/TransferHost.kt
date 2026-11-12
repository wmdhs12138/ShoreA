package com.wmdhs.shorea.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.transfer.formatBackupTime
import com.wmdhs.shorea.data.transfer.manualBackupFileName
import com.wmdhs.shorea.data.transfer.manualSpreadsheetFileName
import com.wmdhs.shorea.ui.FileOperation
import com.wmdhs.shorea.ui.ManualViewModel
import com.wmdhs.shorea.ui.PendingImport
import com.wmdhs.shorea.ui.components.ShapeIcon

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * 导入导出入口：常驻组合以保证文件选择器返回结果时启动器仍已注册；
 * 面板与导入预览对话框按 ViewModel 状态显示。
 */
@Composable
internal fun TransferHost(viewModel: ManualViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerActive by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        pickerActive = false
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pickerActive = false
        if (uri != null) viewModel.importBackup(uri)
    }
    val exportSpreadsheetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME),
    ) { uri ->
        pickerActive = false
        if (uri != null) viewModel.exportSpreadsheet(uri)
    }
    val importSpreadsheetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pickerActive = false
        if (uri != null) viewModel.importSpreadsheet(uri)
    }

    fun launch(block: () -> Unit) {
        if (state.fileOperation == null && !pickerActive) {
            pickerActive = true
            block()
        }
    }

    if (state.transferSheetVisible) {
        TransferSheet(
            compoundCount = state.manual.compounds.size,
            entryCount = state.manual.inspectionEntries.size,
            partCount = state.manual.totalPartAssociationCount,
            canExport = state.canEdit && state.manual.compounds.isNotEmpty(),
            operation = state.fileOperation,
            enabled = state.fileOperation == null && !pickerActive,
            onImportSpreadsheet = {
                launch { importSpreadsheetLauncher.launch(arrayOf(XLSX_MIME, "application/octet-stream")) }
            },
            onExportSpreadsheet = { launch { exportSpreadsheetLauncher.launch(manualSpreadsheetFileName()) } },
            onImportBackup = {
                launch {
                    importBackupLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                }
            },
            onExportBackup = { launch { exportBackupLauncher.launch(manualBackupFileName()) } },
            onDismiss = viewModel::hideTransferSheet,
        )
    }

    state.pendingImport?.let { pending ->
        ImportPreviewDialog(
            pending = pending,
            onMerge = viewModel::mergePendingImport,
            onReplace = viewModel::replaceWithPendingImport,
            onDismiss = viewModel::dismissPendingImport,
        )
    }
}

@Composable
private fun TransferSheet(
    compoundCount: Int,
    entryCount: Int,
    partCount: Int,
    canExport: Boolean,
    operation: FileOperation?,
    enabled: Boolean,
    onImportSpreadsheet: () -> Unit,
    onExportSpreadsheet: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOperation by rememberUpdatedState(operation)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        // 文件读写进行中不允许关闭面板。
        confirmValueChange = { currentOperation == null || it != SheetValue.Hidden },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("导入与导出", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    text = "当前 $compoundCount 种胶料 · $entryCount 项标准 · $partCount 个部品关联",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(targetState = operation, label = "transfer-content") { shownOperation ->
                if (shownOperation != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        ContainedLoadingIndicator()
                        Text(shownOperation.label, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TransferGroup(
                            title = "Excel 表格",
                            actions = listOf(
                                TransferAction(R.drawable.ic_file_open, "导入 Excel", "批量录入或合并表格资料", enabled, onImportSpreadsheet),
                                TransferAction(
                                    R.drawable.ic_table_view,
                                    "导出 Excel",
                                    "13 列送样参考表，便于批量检查和编辑",
                                    enabled && canExport,
                                    onExportSpreadsheet,
                                ),
                            ),
                        )
                        TransferGroup(
                            title = "JSON 备份",
                            actions = listOf(
                                TransferAction(R.drawable.ic_file_open, "导入备份", "完整恢复或合并应用资料", enabled, onImportBackup),
                                TransferAction(
                                    R.drawable.ic_data_object,
                                    "导出备份",
                                    "换机与灾备的完整副本",
                                    enabled && canExport,
                                    onExportBackup,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private class TransferAction(
    @DrawableRes val icon: Int,
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun TransferGroup(title: String, actions: List<TransferAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        actions.forEachIndexed { index, action ->
            SegmentedListItem(
                onClick = action.onClick,
                enabled = action.enabled,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                leadingContent = {
                    ShapeIcon(
                        icon = action.icon,
                        polygon = MaterialShapes.Square,
                        size = 40.dp,
                        iconSize = 22.dp,
                        containerColor = if (action.enabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (action.enabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                },
                supportingContent = { Text(action.description) },
            ) {
                Text(action.title)
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    pending: PendingImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val (title, summary, detail) = when (pending) {
        is PendingImport.Backup -> {
            val backup = pending.backup
            Triple(
                "确认导入 JSON 备份",
                "${backup.compoundCount} 种胶料 · ${backup.entryCount} 项标准 · ${backup.partCount} 个部品关联",
                "备份时间：${formatBackupTime(backup.exportedAtEpochMillis)}",
            )
        }

        is PendingImport.Spreadsheet -> {
            val sheet = pending.spreadsheet
            Triple(
                "确认导入 Excel",
                buildList {
                    add("${sheet.compoundCount} 种胶料")
                    if (sheet.baseRubberCount > 0) add("${sheet.baseRubberCount} 种基胶")
                    add("${sheet.entryCount} 项标准")
                    add("${sheet.partCount} 个部品关联")
                }.joinToString(" · "),
                if (sheet.skippedRowCount == 0) {
                    "${sheet.sourceRowCount} 行全部可导入"
                } else {
                    "${sheet.sourceRowCount} 行中有 ${sheet.skippedRowCount} 行缺少关键字段，已跳过"
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ShapeIcon(
                icon = if (pending is PendingImport.Backup) R.drawable.ic_data_object else R.drawable.ic_table_view,
                polygon = MaterialShapes.Cookie6Sided,
                size = 56.dp,
                iconSize = 28.dp,
            )
        },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(summary, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("合并会保留当前资料并补充新内容；覆盖会完全替换当前手册。")
            }
        },
        confirmButton = {
            Button(onClick = onMerge, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painterResource(R.drawable.ic_merge),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
                )
                Text("合并")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("取消") }
                TextButton(
                    onClick = { confirmReplace = true },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("覆盖") }
            }
        },
    )

    if (confirmReplace) {
        com.wmdhs.shorea.ui.components.DestructiveConfirmDialog(
            title = "覆盖当前手册？",
            text = "覆盖会替换当前手册中的全部胶料、检测标准和部品关联，且无法撤回。请确认已留有备份。",
            confirmLabel = "确认覆盖",
            dismissLabel = "返回",
            icon = R.drawable.ic_error,
            onConfirm = {
                confirmReplace = false
                onReplace()
            },
            onDismiss = { confirmReplace = false },
        )
    }
}
