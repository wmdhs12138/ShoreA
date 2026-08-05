package com.wmdhs.shorea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class ManualFileOperation(val label: String) {
    IMPORT_JSON("正在解析 JSON 备份"), EXPORT_JSON("正在写入 JSON 备份"),
    IMPORT_EXCEL("正在解析 Excel"), EXPORT_EXCEL("正在写入 Excel"),
}

@Composable
internal fun ManualBackupActionsDialog(
    manual: HardnessManual,
    operation: ManualFileOperation?,
    pickerActive: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportSpreadsheet: () -> Unit,
    onImportSpreadsheet: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (operation == null && !pickerActive) onDismiss() },
        title = { Text("导入与导出", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前有 ${manual.compounds.size} 个胶料 · ${manual.inspectionEntries.size} 个检测标准 · ${manual.totalPartAssociationCount} 个部品关联",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (operation != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ExpressiveLoadingGlyph(color = MaterialTheme.colorScheme.primary)
                            Text(operation.label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                val enabled = operation == null && !pickerActive
                FileActionCard("导入 Excel", "批量录入或合并表格资料", enabled, onImportSpreadsheet)
                FileActionCard("导出 Excel", "用于批量检查和编辑 12 列资料", enabled && manual.compounds.isNotEmpty(), onExportSpreadsheet)
                FileActionCard("导入 JSON 备份", "完整恢复或合并应用资料", enabled, onImportBackup)
                FileActionCard("导出 JSON 备份", "换机与灾备的完整副本", enabled && manual.compounds.isNotEmpty(), onExportBackup)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = operation == null && !pickerActive) { Text("关闭") } },
    )
}

@Composable
private fun FileActionCard(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ManualImportPreviewDialog(
    backup: ManualBackup,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) = ImportPreviewDialog(
    title = "确认导入 JSON 备份",
    summary = "${backup.compoundCount} 个胶料 · ${backup.entryCount} 个检测标准 · ${backup.partCount} 个部品关联",
    detail = "备份时间：${formatBackupTime(backup.exportedAtEpochMillis)}。合并会保留当前资料；覆盖会完全替换当前手册。",
    onMerge = onMerge, onReplace = onReplace, onDismiss = onDismiss,
)

@Composable
internal fun ManualSpreadsheetImportPreviewDialog(
    spreadsheet: SpreadsheetImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) = ImportPreviewDialog(
    title = "确认导入 Excel",
    summary = "${spreadsheet.compoundCount} 个胶料 · ${spreadsheet.entryCount} 个检测标准 · ${spreadsheet.partCount} 个部品关联",
    detail = if (spreadsheet.skippedRowCount == 0) "${spreadsheet.sourceRowCount} 行全部可导入" else "${spreadsheet.sourceRowCount} 行中有 ${spreadsheet.skippedRowCount} 行缺少关键字段，已跳过",
    onMerge = onMerge, onReplace = onReplace, onDismiss = onDismiss,
)

@Composable
private fun ImportPreviewDialog(
    title: String,
    summary: String,
    detail: String,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmReplace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(summary, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("“覆盖当前手册”会删除现有资料，请确认已留有备份。", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = onMerge) { Text("合并") } },
        dismissButton = { Row {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = { confirmReplace = true }) { Text("覆盖当前手册", color = MaterialTheme.colorScheme.error) }
        } },
    )
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("确认覆盖当前手册？") },
            text = { Text("覆盖会替换当前手册中的全部胶料、检测标准和部品关联。此操作无法撤回。") },
            confirmButton = { TextButton(onClick = onReplace) { Text("确认覆盖", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("返回") } },
        )
    }
}