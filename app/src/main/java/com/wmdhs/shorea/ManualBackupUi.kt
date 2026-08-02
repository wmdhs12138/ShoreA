package com.wmdhs.shorea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ManualBackupActionsDialog(
    manual: HardnessManual,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportSpreadsheet: () -> Unit,
    onImportSpreadsheet: () -> Unit,
    onDismiss: () -> Unit,
) {
    val compoundCount = manual.compounds.size
    val entryCount = manual.inspectionEntries.size
    val partCount = manual.totalPartAssociationCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入与导出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前有 $compoundCount 个胶料 · $entryCount 个检测标准 · $partCount 个部品关联",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Excel 适合批量编辑和录入；JSON 备份会完整保留应用资料，适合换机与恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onImportSpreadsheet) { Text("导入 Excel") }
                    TextButton(onClick = onExportSpreadsheet, enabled = manual.compounds.isNotEmpty()) { Text("导出 Excel") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onImportBackup) { Text("导入备份") }
                    TextButton(onClick = onExportBackup, enabled = manual.compounds.isNotEmpty()) { Text("导出备份") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun ManualImportPreviewDialog(
    backup: ManualBackup,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认导入备份") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackupSummaryRow(
                    label = "备份时间",
                    value = formatBackupTime(
                        backup.exportedAtEpochMillis,
                    ),
                )
                BackupSummaryRow(
                    label = "资料数量",
                    value = "${backup.compoundCount} 个胶料 · " +
                        "${backup.entryCount} 个检测标准 · " +
                        "${backup.partCount} 个部品关联",
                )
                Text(
                    text = "合并会保留现有资料，只添加缺少的胶料和标准；覆盖会完全替换当前手册。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onMerge) {
                    Text("合并")
                }
                TextButton(onClick = onReplace) {
                    Text(
                        text = "覆盖",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}


@Composable
internal fun ManualSpreadsheetImportPreviewDialog(
    spreadsheet: SpreadsheetImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认导入 Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BackupSummaryRow(
                    label = "识别结果",
                    value = "${spreadsheet.compoundCount} 个胶料 · ${spreadsheet.entryCount} 个检测标准 · ${spreadsheet.partCount} 个部品关联",
                )
                BackupSummaryRow(
                    label = "数据行",
                    value = if (spreadsheet.skippedRowCount == 0) {
                        "${spreadsheet.sourceRowCount} 行全部可导入"
                    } else {
                        "${spreadsheet.sourceRowCount} 行，其中 ${spreadsheet.skippedRowCount} 行因缺少胶料号、标准号或部品号而跳过"
                    },
                )
                Text(
                    text = "合并会按胶料号和标准号补充资料；覆盖会完全替换当前手册。建议首次导入选择覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onMerge) { Text("合并") }
                TextButton(onClick = onReplace) {
                    Text("覆盖", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BackupSummaryRow(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
