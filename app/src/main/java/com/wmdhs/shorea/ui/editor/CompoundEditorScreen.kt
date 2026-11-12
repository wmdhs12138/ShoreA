package com.wmdhs.shorea.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.compactNumber
import com.wmdhs.shorea.data.model.hasDuplicateCompoundCode
import com.wmdhs.shorea.ui.CompoundForm
import com.wmdhs.shorea.ui.ManualViewModel
import java.util.Locale

/**
 * 新建或编辑胶料。
 *
 * @param onSaved 保存成功后回调，参数为胶料 ID。
 */
@Composable
internal fun CompoundEditorScreen(
    viewModel: ManualViewModel,
    compoundId: Long?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val initial = compoundId?.let(state.manual::findCompound)
    LaunchedEffect(compoundId, initial == null, state.isLoading) {
        if (!state.isLoading && (!state.canEdit || (compoundId != null && initial == null))) onDismiss()
    }

    var compoundCode by rememberSaveable { mutableStateOf(initial?.compoundCode.orEmpty()) }
    var baseRubber by rememberSaveable { mutableStateOf(initial?.baseRubber.orEmpty()) }
    var temperature by rememberSaveable { mutableStateOf(initial?.testPieceCureTemperatureC.orEmpty()) }
    var testPieceTime by rememberSaveable { mutableStateOf(initial?.testPieceCureTimeMinutes.orEmpty()) }
    var customBlockTime by rememberSaveable { mutableStateOf(initial?.customBlockCureTimeMinutes.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }

    val normalizedCode = compoundCode.trim().uppercase(Locale.ROOT)
    val duplicateCode = hasDuplicateCompoundCode(state.manual.compounds, normalizedCode, compoundId)
    val hasUnsavedChanges = compoundCode != initial?.compoundCode.orEmpty() ||
        baseRubber != initial?.baseRubber.orEmpty() ||
        temperature != initial?.testPieceCureTemperatureC.orEmpty() ||
        testPieceTime != initial?.testPieceCureTimeMinutes.orEmpty() ||
        customBlockTime != initial?.customBlockCureTimeMinutes.orEmpty() ||
        notes != initial?.notes.orEmpty()
    val canSave = state.canEdit && normalizedCode.isNotEmpty() && !duplicateCode &&
        (initial == null || hasUnsavedChanges)

    EditorScaffold(
        title = if (initial == null) "添加胶料" else "编辑胶料",
        subtitle = initial?.compoundCode,
        canSave = canSave,
        hasUnsavedChanges = hasUnsavedChanges,
        onDismiss = onDismiss,
        onSave = {
            val savedId = viewModel.saveCompound(
                existingId = compoundId,
                form = CompoundForm(
                    compoundCode = normalizedCode,
                    baseRubber = baseRubber,
                    testPieceCureTemperatureC = temperature,
                    testPieceCureTimeMinutes = testPieceTime,
                    customBlockCureTimeMinutes = customBlockTime,
                    notes = notes,
                ),
            )
            if (savedId != null) onSaved(savedId)
        },
    ) {
        item {
            FormSection(R.drawable.ic_science, "基本信息", "胶料号与基胶用于识别和分类") {
                FormTextField(
                    value = compoundCode,
                    onValueChange = { compoundCode = it },
                    label = "胶料号 *",
                    placeholder = "例如：P01331201",
                    supportingText = if (duplicateCode) "该胶料号已存在，请编辑原记录" else "保存时自动转为大写",
                    isError = duplicateCode,
                    capitalization = KeyboardCapitalization.Characters,
                )
                FormTextField(
                    value = baseRubber,
                    onValueChange = { baseRubber = it },
                    label = "基胶（胶种）",
                    placeholder = "例如：三元乙丙橡胶",
                )
            }
        }
        item {
            val calculatedBlockTime = testPieceTime.toDoubleOrNull()?.let { compactNumber(it * 2) }
            FormSection(R.drawable.ic_thermostat, "试片硫化条件", "硬度块时间默认按试片时间的双倍计算") {
                FormTextField(
                    value = temperature,
                    onValueChange = { temperature = sanitizeDecimalInput(it) },
                    label = "硫化温度（℃）",
                    placeholder = "例如：180",
                    keyboardType = KeyboardType.Decimal,
                )
                FormTextField(
                    value = testPieceTime,
                    onValueChange = { testPieceTime = sanitizeDecimalInput(it) },
                    label = "试片硫化时间（min）",
                    placeholder = "例如：9",
                    supportingText = if (calculatedBlockTime != null && customBlockTime.isBlank()) {
                        "硬度块时间将自动计算为 $calculatedBlockTime min"
                    } else {
                        null
                    },
                    keyboardType = KeyboardType.Decimal,
                )
                FormTextField(
                    value = customBlockTime,
                    onValueChange = { customBlockTime = sanitizeDecimalInput(it) },
                    label = "硬度块自定义时间（可选）",
                    placeholder = "留空则自动使用试片时间 ×2",
                    supportingText = "仅在个别胶料不按双倍时间时填写",
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }
        item {
            FormSection(R.drawable.ic_notes, "备注", "用于记录胶料层面的补充信息") {
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "胶料备注",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }
}
