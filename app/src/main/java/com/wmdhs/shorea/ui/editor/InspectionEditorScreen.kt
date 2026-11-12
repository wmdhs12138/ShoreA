package com.wmdhs.shorea.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.HardnessSet
import com.wmdhs.shorea.data.model.findPartNumberConflicts
import com.wmdhs.shorea.data.model.hasDuplicateStandardNumber
import com.wmdhs.shorea.data.model.parsePartNumbers
import com.wmdhs.shorea.ui.InspectionForm
import com.wmdhs.shorea.ui.ManualViewModel
import com.wmdhs.shorea.ui.components.hardnessInputHint

/** 新建或编辑某胶料下的检测标准。 */
@Composable
internal fun InspectionEditorScreen(
    viewModel: ManualViewModel,
    compoundId: Long,
    entryId: Long?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val manual = state.manual
    val compound = manual.findCompound(compoundId)
    val initial = entryId?.let(manual::findEntry)?.takeIf { it.compoundId == compoundId }
    LaunchedEffect(compound == null, entryId, initial == null, state.isLoading) {
        val missing = compound == null || (entryId != null && initial == null)
        if (!state.isLoading && (!state.canEdit || missing)) onDismiss()
    }
    if (compound == null) return

    val initialPartText = initial?.partNumbers?.joinToString("\n").orEmpty()
    var standardNumber by rememberSaveable { mutableStateOf(initial?.standardNumber.orEmpty()) }
    var partNumbersText by rememberSaveable { mutableStateOf(initialPartText) }
    var testPieceHardness by rememberSaveable { mutableStateOf(initial?.hardness?.testPieceHardness.orEmpty()) }
    var blockHardness by rememberSaveable { mutableStateOf(initial?.hardness?.blockHardness.orEmpty()) }
    var productHardness by rememberSaveable { mutableStateOf(initial?.hardness?.productHardness.orEmpty()) }
    var productCategory by rememberSaveable { mutableStateOf(initial?.productCategory.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(initial?.color.orEmpty()) }
    var tensileStrength by rememberSaveable { mutableStateOf(initial?.tensileStrength.orEmpty()) }
    var elongation by rememberSaveable { mutableStateOf(initial?.elongation.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }

    val entries = remember(manual, compoundId) { manual.entriesForCompound(compoundId) }
    val normalizedStandard = standardNumber.trim()
    val partNumbers = remember(partNumbersText) { parsePartNumbers(partNumbersText) }
    val duplicateStandard = hasDuplicateStandardNumber(entries, compoundId, normalizedStandard, entryId)
    val partConflicts = remember(entries, partNumbers, entryId) {
        findPartNumberConflicts(entries, compoundId, partNumbers, entryId)
    }
    val hasUnsavedChanges = standardNumber != initial?.standardNumber.orEmpty() ||
        partNumbersText != initialPartText ||
        testPieceHardness != initial?.hardness?.testPieceHardness.orEmpty() ||
        blockHardness != initial?.hardness?.blockHardness.orEmpty() ||
        productHardness != initial?.hardness?.productHardness.orEmpty() ||
        productCategory != initial?.productCategory.orEmpty() ||
        color != initial?.color.orEmpty() ||
        tensileStrength != initial?.tensileStrength.orEmpty() ||
        elongation != initial?.elongation.orEmpty() ||
        notes != initial?.notes.orEmpty()
    val canSave = state.canEdit && partNumbers.isNotEmpty() && !duplicateStandard &&
        partConflicts.isEmpty() && (initial == null || hasUnsavedChanges)

    EditorScaffold(
        title = if (initial == null) "添加检测标准" else "编辑检测标准",
        subtitle = compound.compoundCode,
        canSave = canSave,
        hasUnsavedChanges = hasUnsavedChanges,
        onDismiss = onDismiss,
        onSave = {
            val savedId = viewModel.saveInspection(
                compoundId = compoundId,
                existingEntryId = entryId,
                form = InspectionForm(
                    standardNumber = normalizedStandard,
                    partNumbers = partNumbers,
                    hardness = HardnessSet(
                        testPieceHardness = testPieceHardness,
                        blockHardness = blockHardness,
                        productHardness = productHardness,
                    ),
                    productCategory = productCategory,
                    color = color,
                    tensileStrength = tensileStrength,
                    elongation = elongation,
                    notes = notes,
                ),
            )
            if (savedId != null) onSaved()
        },
    ) {
        item {
            FormSection(R.drawable.ic_description, "检测标准", "标准号用于区分检测要求，可留空") {
                FormTextField(
                    value = standardNumber,
                    onValueChange = { standardNumber = it },
                    label = "检测标准号",
                    placeholder = "例如：B2-12-1078/A",
                    supportingText = if (duplicateStandard) {
                        "该检测标准号已存在，请编辑原记录"
                    } else {
                        "标准号为空时仍可保存，部品号必须填写"
                    },
                    isError = duplicateStandard,
                    capitalization = KeyboardCapitalization.Characters,
                )
            }
        }
        item {
            val conflictText = partConflicts.entries.joinToString("；") { (part, standards) ->
                "$part 已属于 ${standards.joinToString("、")}"
            }
            FormSection(R.drawable.ic_inventory_2, "适用部品", "执行同一检测标准的多个部品号放在同一条记录") {
                FormTextField(
                    value = partNumbersText,
                    onValueChange = { partNumbersText = it },
                    label = "部品号 *",
                    placeholder = "每行一个完整部品号\n例如：\n3DE000023A\n3DE000023C",
                    supportingText = when {
                        partConflicts.isNotEmpty() -> "同一胶料下部品号不能重复：$conflictText"
                        partNumbers.isNotEmpty() -> "已识别 ${partNumbers.size} 个部品号；保存时去重并排序"
                        else -> "也支持逗号、分号或空格分隔"
                    },
                    isError = partConflicts.isNotEmpty(),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    capitalization = KeyboardCapitalization.Characters,
                )
            }
        }
        item {
            FormSection(R.drawable.ic_speed, "硬度标准", "主要有效硬度优先取硬度块，其次产品，最后试片") {
                FormTextField(
                    value = testPieceHardness,
                    onValueChange = { testPieceHardness = it },
                    label = "试片硬度",
                    placeholder = "例如：33+2-2",
                    supportingText = hardnessInputHint(testPieceHardness),
                )
                FormTextField(
                    value = blockHardness,
                    onValueChange = { blockHardness = it },
                    label = "硬度块硬度",
                    placeholder = "例如：34+3-3",
                    supportingText = hardnessInputHint(blockHardness),
                )
                FormTextField(
                    value = productHardness,
                    onValueChange = { productHardness = it },
                    label = "产品硬度",
                    placeholder = "例如：35+3-3 或 30-40",
                    supportingText = hardnessInputHint(productHardness),
                )
            }
        }
        item {
            FormSection(R.drawable.ic_article, "其他资料", "在详细视图与胶料详情中显示") {
                FormTextField(
                    value = productCategory,
                    onValueChange = { productCategory = it },
                    label = "产品类别",
                    placeholder = "例如：橡胶避振脚",
                )
                FormTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = "颜色",
                    placeholder = "例如：黑色",
                )
                FormTextField(
                    value = tensileStrength,
                    onValueChange = { tensileStrength = it },
                    label = "拉伸强度",
                    placeholder = "例如：标准 ≥11.76 MPa；快检 ≥9 MPa",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                FormTextField(
                    value = elongation,
                    onValueChange = { elongation = it },
                    label = "伸长率",
                    placeholder = "例如：≥400%",
                )
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "检测标准备注",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }
}
