package com.wmdhs.shorea.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.InspectionEntry
import com.wmdhs.shorea.data.model.RubberCompound

/** 危险操作确认：确认按钮使用 error 配色并带形状变形动效。 */
@Composable
internal fun DestructiveConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_delete,
    dismissLabel: String = "取消",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(dismissLabel) }
        },
    )
}

@Composable
internal fun DeleteInspectionDialog(
    entry: InspectionEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = DestructiveConfirmDialog(
    title = "删除检测标准？",
    text = "将删除标准“${entry.displayTitle}”及其 ${entry.partNumbers.size} 个部品关联。删除后可在提示条中撤回。",
    confirmLabel = "删除",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

@Composable
internal fun DeleteCompoundDialog(
    compound: RubberCompound,
    entryCount: Int,
    partCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = DestructiveConfirmDialog(
    title = "删除胶料？",
    text = "删除胶料“${compound.compoundCode}”将同时删除 $entryCount 个检测标准和 $partCount 个部品关联。删除后可在提示条中撤回。",
    confirmLabel = "删除",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
