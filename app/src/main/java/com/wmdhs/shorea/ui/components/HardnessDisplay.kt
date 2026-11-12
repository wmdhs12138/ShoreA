package com.wmdhs.shorea.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmdhs.shorea.data.model.EffectiveHardness
import com.wmdhs.shorea.data.model.HardnessSource
import com.wmdhs.shorea.data.model.ParsedHardness
import com.wmdhs.shorea.data.model.parseHardness

internal enum class HardnessTextSize { Small, Medium, Large }

/** 把 "34+3-3" 这类原始文本排版为 名义值 + 上下偏差 + 单位。 */
@Composable
internal fun HardnessValueText(
    value: String,
    modifier: Modifier = Modifier,
    size: HardnessTextSize = HardnessTextSize.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showCalculatedRange: Boolean = false,
) {
    val parsed = remember(value) { parseHardness(value) }
    val typography = MaterialTheme.typography
    val mainStyle = when (size) {
        HardnessTextSize.Small -> typography.titleMediumEmphasized
        HardnessTextSize.Medium -> typography.titleLargeEmphasized
        HardnessTextSize.Large -> typography.displaySmallEmphasized
    }
    val deviationStyle = when (size) {
        HardnessTextSize.Large -> typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 16.sp)
        else -> typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 13.sp)
    }
    val unitStyle = if (size == HardnessTextSize.Large) typography.titleMedium else typography.labelLarge
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = parsed.spokenDescription()
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (parsed) {
            is ParsedHardness.Tolerance -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parsed.nominal, style = mainStyle, color = color)
                    Column(modifier = Modifier.padding(start = 3.dp)) {
                        Text("+${parsed.upperDeviation}", style = deviationStyle, color = color, fontWeight = FontWeight.SemiBold)
                        Text("−${parsed.lowerDeviation}", style = deviationStyle, color = color, fontWeight = FontWeight.SemiBold)
                    }
                    Text(" A", style = unitStyle, color = color, fontWeight = FontWeight.SemiBold)
                }
                if (showCalculatedRange) {
                    Text(
                        "允许范围 ${parsed.lowerLimit} – ${parsed.upperLimit} A",
                        style = typography.bodyMedium,
                        color = supportingColor,
                    )
                }
            }

            is ParsedHardness.Range -> {
                Text("${parsed.lower} – ${parsed.upper} A", style = mainStyle, color = color)
                if (showCalculatedRange) {
                    Text("下限 ${parsed.lower} · 上限 ${parsed.upper}", style = typography.bodyMedium, color = supportingColor)
                }
            }

            is ParsedHardness.Minimum -> {
                Text("${if (parsed.inclusive) "≥" else ">"} ${parsed.value} A", style = mainStyle, color = color)
                if (showCalculatedRange) Text("最低要求", style = typography.bodyMedium, color = supportingColor)
            }

            is ParsedHardness.Maximum -> {
                Text("${if (parsed.inclusive) "≤" else "<"} ${parsed.value} A", style = mainStyle, color = color)
                if (showCalculatedRange) Text("最高允许", style = typography.bodyMedium, color = supportingColor)
            }

            is ParsedHardness.Exact -> Text("${parsed.value} A", style = mainStyle, color = color)

            is ParsedHardness.Raw -> Text(
                parsed.raw.ifBlank { "—" },
                style = if (size == HardnessTextSize.Small) typography.bodyMedium else typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun ParsedHardness.spokenDescription(): String = when (this) {
    is ParsedHardness.Tolerance -> "硬度 $nominal A，允许范围 $lowerLimit 到 $upperLimit A"
    is ParsedHardness.Range -> "硬度范围 $lower 到 $upper A"
    is ParsedHardness.Minimum -> "硬度最低 $value A"
    is ParsedHardness.Maximum -> "硬度最高 $value A"
    is ParsedHardness.Exact -> "硬度 $value A"
    is ParsedHardness.Raw -> "硬度 ${raw.ifBlank { "未填写" }}"
}

/** 首页卡片右上角的"答案"：主要有效硬度及其来源。 */
@Composable
internal fun HardnessAnswerPill(
    hardness: EffectiveHardness?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (hardness == null) colors.surfaceContainerHighest else colors.primaryContainer,
        contentColor = if (hardness == null) colors.onSurfaceVariant else colors.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hardness == null) {
                Text("未设硬度", style = MaterialTheme.typography.labelLargeEmphasized)
            } else {
                Text(
                    text = hardness.source.label,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    maxLines = 1,
                )
                HardnessValueText(
                    value = hardness.rawValue,
                    size = HardnessTextSize.Small,
                    color = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/** 次要硬度：来源标签 + 数值，横向流式排列。 */
@Composable
internal fun SecondaryHardnessRow(
    values: List<Pair<HardnessSource, String>>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { (source, value) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HardnessValueText(value = value, size = HardnessTextSize.Small)
            }
        }
    }
}

/** 编辑器中的实时解析提示。 */
internal fun hardnessInputHint(rawValue: String): String? {
    if (rawValue.isBlank()) return null
    return when (val parsed = parseHardness(rawValue)) {
        is ParsedHardness.Tolerance ->
            "识别为 ${parsed.nominal} A（+${parsed.upperDeviation} / −${parsed.lowerDeviation}），允许 ${parsed.lowerLimit} – ${parsed.upperLimit} A"
        is ParsedHardness.Range -> "识别为范围 ${parsed.lower} – ${parsed.upper} A"
        is ParsedHardness.Minimum -> "识别为最低 ${parsed.value} A"
        is ParsedHardness.Maximum -> "识别为最高 ${parsed.value} A"
        is ParsedHardness.Exact -> "识别为 ${parsed.value} A"
        is ParsedHardness.Raw -> "未识别为数值格式，将按原文显示"
    }
}
