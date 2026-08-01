package com.wmdhs.shorea

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

internal data class CompoundFormData(
    val compoundCode: String,
    val testPieceCureTemperatureC: String,
    val testPieceCureTimeMinutes: String,
    val customBlockCureTimeMinutes: String,
    val notes: String,
)

internal data class GroupFormData(
    val standardNumber: String,
    val partNumbers: List<String>,
    val hardness: HardnessSet,
    val productCategory: String,
    val color: String,
    val tensileStrength: String,
    val elongation: String,
    val notes: String,
)

@Composable
internal fun ManualSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
        label = { Text("搜索") },
        placeholder = {
            Text("搜索胶料号、标准号、部品号或硬度")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("清除")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
internal fun ManualLoadingState(
    innerPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ManualDataErrorState(
    innerPadding: PaddingValues,
    detail: String,
    onRestoreBackup: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "本地资料无法读取",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "为避免覆盖原数据，当前已停止编辑。可以从之前导出的备份恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRestoreBackup) {
                Text("从备份恢复")
            }
        }
    }
}

@Composable
internal fun EmptyManualState(
    innerPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有胶料资料",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击右下角“添加胶料”建立硬度块手册",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EmptyManualSearchState(
    innerPadding: PaddingValues,
    query: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "没有匹配的胶料",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "未找到与“$query”相关的胶料号、标准号、部品号或硬度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SwipeCompoundCard(
    compound: RubberCompound,
    searchQuery: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val itemShape = RoundedCornerShape(20.dp)

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme
                            .colorScheme
                            .errorContainer,
                        shape = itemShape,
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "删除",
                    color = MaterialTheme
                        .colorScheme
                        .onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        CompoundCard(
            compound = compound,
            searchQuery = searchQuery,
            onOpen = onOpen,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompoundCard(
    compound: RubberCompound,
    searchQuery: String,
    onOpen: () -> Unit,
) {
    val recommendations = compound.groups
        .mapNotNull { it.recommendation }
        .distinctBy { recommendation ->
            recommendation.source to recommendation.value
        }
    val matchingGroups = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        compound.groups
            .filter { it.matches(searchQuery) }
            .take(3)
    }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme
                                .colorScheme
                                .primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CompoundGlyph(
                        modifier = Modifier.size(27.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = compound.compoundCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildString {
                            append(compound.groups.size)
                            append(" 个检测标准 · ")
                            append(compound.totalPartCount)
                            append(" 个部品")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    )
                }
            }

            if (recommendations.isEmpty()) {
                Text(
                    text = if (compound.groups.isEmpty()) {
                        "尚未添加部品检测标准"
                    } else {
                        "尚未配置可用于送样的硬度块或产品硬度"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    recommendations.forEach { recommendation ->
                        RecommendationChip(
                            recommendation = recommendation,
                        )
                    }
                }
            }

            if (matchingGroups.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme
                        .colorScheme
                        .surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        matchingGroups.forEach { group ->
                            SearchMatchPreview(group = group)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMatchPreview(
    group: PartSpecificationGroup,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = group.partNumbers.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = group.standardNumber.ifBlank {
                    "未填写标准号"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val previewHardness = group.recommendation?.value
            ?: group.hardness.testPieceHardness
        if (previewHardness.isBlank()) {
            Text(
                text = "无硬度资料",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HardnessValueDisplay(
                value = previewHardness,
                compact = true,
                accent = group.recommendation != null,
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun CompoundDetailSheet(
    compound: RubberCompound,
    onEditCompound: () -> Unit,
    onAddGroup: () -> Unit,
    onEditGroup: (PartSpecificationGroup) -> Unit,
    onDeleteGroup: (PartSpecificationGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme
                                    .colorScheme
                                    .primaryContainer,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompoundGlyph(
                            modifier = Modifier.size(29.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = compound.compoundCode,
                            style = MaterialTheme
                                .typography
                                .headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${compound.totalPartCount} 个关联部品",
                            style = MaterialTheme
                                .typography
                                .bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        )
                    }

                    TextButton(onClick = onEditCompound) {
                        Text("编辑胶料")
                    }
                }
            }

            item {
                CureConditionCard(compound = compound)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "检测标准",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme
                            .typography
                            .titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    TextButton(onClick = onAddGroup) {
                        Text("＋ 添加标准")
                    }
                }
            }

            if (compound.groups.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme
                            .colorScheme
                            .surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 28.dp,
                            ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "还没有检测标准",
                                style = MaterialTheme
                                    .typography
                                    .titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "添加标准号、部品号与三类硬度资料",
                                style = MaterialTheme
                                    .typography
                                    .bodyMedium,
                                color = MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(
                    count = compound.groups.size,
                    key = { index ->
                        compound.groups[index].id
                    },
                ) { index ->
                    SpecificationGroupCard(
                        group = compound.groups[index],
                        onEdit = {
                            onEditGroup(compound.groups[index])
                        },
                        onDelete = {
                            onDeleteGroup(compound.groups[index])
                        },
                    )
                }
            }

            if (compound.notes.isNotBlank()) {
                item {
                    DetailValueCard(
                        label = "胶料备注",
                        value = compound.notes,
                    )
                }
            }

            item {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun CureConditionCard(
    compound: RubberCompound,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme
            .colorScheme
            .surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "制作条件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (
                compound.testPieceCureTemperatureC.isBlank() &&
                compound.testPieceCureTimeMinutes.isBlank()
            ) {
                Text(
                    text = "尚未填写试片硫化条件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            } else {
                ConditionRow(
                    label = "试片",
                    value = cureConditionText(
                        temperature =
                            compound.testPieceCureTemperatureC,
                        time = compound.testPieceCureTimeMinutes,
                    ),
                )
                ConditionRow(
                    label = "硬度块",
                    value = cureConditionText(
                        temperature =
                            compound.testPieceCureTemperatureC,
                        time = compound.blockCureTimeMinutes,
                    ),
                    supporting = if (
                        compound.usesCustomBlockCureTime
                    ) {
                        "使用自定义时间"
                    } else {
                        "按试片时间自动 ×2"
                    },
                )
            }
        }
    }
}

@Composable
private fun ConditionRow(
    label: String,
    value: String,
    supporting: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            }
        }
    }
}

private fun cureConditionText(
    temperature: String,
    time: String,
): String {
    val temperatureText = temperature
        .takeIf { it.isNotBlank() }
        ?.let { "$it℃" }
        ?: "温度未填"
    val timeText = time
        .takeIf { it.isNotBlank() }
        ?.let { "$it min" }
        ?: "时间未填"

    return "$temperatureText × $timeText"
}

@Composable
private fun SpecificationGroupCard(
    group: PartSpecificationGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(group.id) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme
                .colorScheme
                .surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme
                    .colorScheme
                    .secondaryContainer,
                contentColor = MaterialTheme
                    .colorScheme
                    .onSecondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "送样部品",
                        style = MaterialTheme
                            .typography
                            .labelMedium,
                    )
                    Text(
                        text = group.partNumbers.joinToString(" · "),
                        style = MaterialTheme
                            .typography
                            .titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "检测标准号",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
                Text(
                    text = group.standardNumber.ifBlank {
                        "未填写标准号"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            }

            RecommendedHardnessBlock(group = group)

            if (expanded) {
                HorizontalDivider()

                HardnessDetailRow(
                    label = "试片硬度",
                    value = group.hardness.testPieceHardness,
                    supporting = "仅作参考，不自动作为送样硬度",
                )
                HardnessDetailRow(
                    label = "硬度块硬度",
                    value = group.hardness.blockHardness,
                    supporting = "送样第一优先级",
                )
                HardnessDetailRow(
                    label = "产品硬度",
                    value = group.hardness.productHardness,
                    supporting = "硬度块硬度为空时使用",
                )

                OptionalDetailRow(
                    label = "产品类别",
                    value = group.productCategory,
                )
                OptionalDetailRow(
                    label = "颜色",
                    value = group.color,
                )
                OptionalDetailRow(
                    label = "拉伸强度",
                    value = group.tensileStrength,
                )
                OptionalDetailRow(
                    label = "伸长率",
                    value = group.elongation,
                )
                OptionalDetailRow(
                    label = "备注",
                    value = group.notes,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        expanded = !expanded
                    },
                ) {
                    Text(
                        if (expanded) {
                            "收起资料"
                        } else {
                            "详细资料"
                        },
                    )
                }

                TextButton(onClick = onEdit) {
                    Text("编辑")
                }

                TextButton(onClick = onDelete) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun HardnessValueDisplay(
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showCalculatedRange: Boolean = false,
    accent: Boolean = false,
) {
    val parsed = remember(value) { parseHardness(value) }
    val mainStyle = if (compact) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.headlineMedium
    }
    val valueColor = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (parsed) {
            is ParsedHardness.Tolerance -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parsed.nominal,
                        style = mainStyle,
                        color = valueColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(
                        modifier = Modifier.padding(start = 3.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "+${parsed.upperDeviation}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                            ),
                            color = valueColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "−${parsed.lowerDeviation}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                            ),
                            color = valueColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = " A",
                        style = MaterialTheme.typography.labelLarge,
                        color = valueColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (showCalculatedRange) {
                    Text(
                        text = "允许范围 ${parsed.lowerLimit} — ${parsed.upperLimit} A",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            is ParsedHardness.Range -> {
                Text(
                    text = "${parsed.lower} — ${parsed.upper} A",
                    style = mainStyle,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                )
                if (showCalculatedRange) {
                    Text(
                        text = "下限 ${parsed.lower} · 上限 ${parsed.upper}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ParsedHardness.Minimum -> {
                Text(
                    text = "${if (parsed.inclusive) "≥" else ">"} ${parsed.value} A",
                    style = mainStyle,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                )
                if (showCalculatedRange) {
                    Text(
                        text = "最低要求",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ParsedHardness.Maximum -> {
                Text(
                    text = "${if (parsed.inclusive) "≤" else "<"} ${parsed.value} A",
                    style = mainStyle,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                )
                if (showCalculatedRange) {
                    Text(
                        text = "最高允许",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ParsedHardness.Exact -> {
                Text(
                    text = "${parsed.value} A",
                    style = mainStyle,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            is ParsedHardness.Raw -> {
                Text(
                    text = parsed.raw.ifBlank { "—" },
                    style = if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RecommendedHardnessBlock(
    group: PartSpecificationGroup,
) {
    val recommendation = group.recommendation

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = when (recommendation?.source) {
            HardnessSource.BLOCK_STANDARD -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            HardnessSource.PRODUCT_STANDARD -> {
                MaterialTheme.colorScheme.tertiaryContainer
            }

            null -> {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "推荐送样硬度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            )

            if (recommendation == null) {
                Text(
                    text = "未配置送样硬度",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                HardnessValueDisplay(
                    value = recommendation.value,
                    showCalculatedRange = true,
                    accent = true,
                )
            }

            Text(
                text = when (recommendation?.source) {
                    HardnessSource.BLOCK_STANDARD -> {
                        "依据：硬度块标准"
                    }

                    HardnessSource.PRODUCT_STANDARD -> {
                        "依据：产品硬度代用"
                    }

                    null -> {
                        group.hardness.testPieceHardness
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                "仅有试片硬度 $it，不建议直接用于送样"
                            }
                            ?: "硬度块硬度与产品硬度均为空"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HardnessDetailRow(
    label: String,
    value: String,
    supporting: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (value.isBlank()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                HardnessValueDisplay(
                    value = value,
                    compact = true,
                )
            }
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionalDetailRow(
    label: String,
    value: String,
) {
    if (value.isBlank()) {
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecommendationChip(
    recommendation: HardnessRecommendation,
) {
    Surface(
        shape = CircleShape,
        color = when (recommendation.source) {
            HardnessSource.BLOCK_STANDARD -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            HardnessSource.PRODUCT_STANDARD -> {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        },
        contentColor = when (recommendation.source) {
            HardnessSource.BLOCK_STANDARD -> {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            HardnessSource.PRODUCT_STANDARD -> {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
        },
    ) {
        HardnessValueDisplay(
            value = recommendation.value,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp,
            ),
            compact = true,
            accent = true,
        )
    }
}

@Composable
private fun DetailValueCard(
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme
            .colorScheme
            .surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompoundEditorDialog(
    initial: RubberCompound?,
    compounds: List<RubberCompound>,
    onDismiss: () -> Unit,
    onSave: (CompoundFormData) -> Unit,
) {
    var compoundCode by remember(initial?.id) {
        mutableStateOf(initial?.compoundCode.orEmpty())
    }
    var temperature by remember(initial?.id) {
        mutableStateOf(
            initial?.testPieceCureTemperatureC.orEmpty(),
        )
    }
    var testPieceTime by remember(initial?.id) {
        mutableStateOf(
            initial?.testPieceCureTimeMinutes.orEmpty(),
        )
    }
    var customBlockTime by remember(initial?.id) {
        mutableStateOf(
            initial?.customBlockCureTimeMinutes.orEmpty(),
        )
    }
    var notes by remember(initial?.id) {
        mutableStateOf(initial?.notes.orEmpty())
    }

    val normalizedCode = compoundCode
        .trim()
        .uppercase(Locale.ROOT)
    val duplicateCode = normalizedCode.isNotEmpty() &&
        hasDuplicateCompoundCode(
            compounds = compounds,
            compoundCode = normalizedCode,
            excludingId = initial?.id,
        )
    val canSave = normalizedCode.isNotEmpty() && !duplicateCode

    FullScreenEditorDialog(
        title = if (initial == null) {
            "添加胶料"
        } else {
            "编辑胶料"
        },
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CompoundFormData(
                    compoundCode = normalizedCode,
                    testPieceCureTemperatureC =
                        temperature.trim(),
                    testPieceCureTimeMinutes =
                        testPieceTime.trim(),
                    customBlockCureTimeMinutes =
                        customBlockTime.trim(),
                    notes = notes.trim(),
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                EditorSectionTitle(
                    title = "基本信息",
                    description = "胶料号是手册的第一层级",
                )
            }

            item {
                OutlinedTextField(
                    value = compoundCode,
                    onValueChange = {
                        compoundCode = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("胶料号 *") },
                    placeholder = {
                        Text("例如：P01331201")
                    },
                    supportingText = {
                        if (duplicateCode) {
                            Text("该胶料号已存在，请编辑原记录")
                        }
                    },
                    isError = duplicateCode,
                    singleLine = true,
                )
            }

            item {
                EditorSectionTitle(
                    title = "试片硫化条件",
                    description =
                        "硬度块时间默认按试片时间的双倍计算",
                )
            }

            item {
                OutlinedTextField(
                    value = temperature,
                    onValueChange = {
                        temperature = sanitizeDecimalInput(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("硫化温度（℃）") },
                    placeholder = { Text("例如：180") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = testPieceTime,
                    onValueChange = {
                        testPieceTime = sanitizeDecimalInput(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("试片硫化时间（min）") },
                    placeholder = { Text("例如：9") },
                    supportingText = {
                        val calculated = testPieceTime
                            .toDoubleOrNull()
                            ?.times(2)
                            ?.let(::compactEditorNumber)

                        if (
                            calculated != null &&
                            customBlockTime.isBlank()
                        ) {
                            Text(
                                "硬度块时间将自动计算为 $calculated min",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = customBlockTime,
                    onValueChange = {
                        customBlockTime =
                            sanitizeDecimalInput(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("硬度块自定义时间（可选）")
                    },
                    placeholder = {
                        Text("留空则自动使用试片时间 ×2")
                    },
                    supportingText = {
                        Text(
                            "仅在个别胶料不按双倍时间时填写",
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )
            }

            item {
                EditorSectionTitle(
                    title = "备注",
                    description = "用于记录胶料层面的补充信息",
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("胶料备注") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditorDialog(
    compound: RubberCompound,
    initial: PartSpecificationGroup?,
    onDismiss: () -> Unit,
    onSave: (GroupFormData) -> Unit,
) {
    var standardNumber by remember(initial?.id) {
        mutableStateOf(initial?.standardNumber.orEmpty())
    }
    var partNumbersText by remember(initial?.id) {
        mutableStateOf(
            initial?.partNumbers?.joinToString("\n").orEmpty(),
        )
    }
    var testPieceHardness by remember(initial?.id) {
        mutableStateOf(
            initial?.hardness?.testPieceHardness.orEmpty(),
        )
    }
    var blockHardness by remember(initial?.id) {
        mutableStateOf(
            initial?.hardness?.blockHardness.orEmpty(),
        )
    }
    var productHardness by remember(initial?.id) {
        mutableStateOf(
            initial?.hardness?.productHardness.orEmpty(),
        )
    }
    var productCategory by remember(initial?.id) {
        mutableStateOf(initial?.productCategory.orEmpty())
    }
    var color by remember(initial?.id) {
        mutableStateOf(initial?.color.orEmpty())
    }
    var tensileStrength by remember(initial?.id) {
        mutableStateOf(initial?.tensileStrength.orEmpty())
    }
    var elongation by remember(initial?.id) {
        mutableStateOf(initial?.elongation.orEmpty())
    }
    var notes by remember(initial?.id) {
        mutableStateOf(initial?.notes.orEmpty())
    }
    var showPartConflictConfirmation by remember(initial?.id) {
        mutableStateOf(false)
    }

    val normalizedStandardNumber = standardNumber
        .trim()
        .uppercase(Locale.ROOT)
    val parsedPartNumbers = parsePartNumbers(partNumbersText)
    val duplicateStandard = normalizedStandardNumber.isNotEmpty() &&
        hasDuplicateStandardNumber(
            compound = compound,
            standardNumber = normalizedStandardNumber,
            excludingGroupId = initial?.id,
        )
    val partNumberConflicts = findPartNumberConflicts(
        compound = compound,
        partNumbers = parsedPartNumbers,
        excludingGroupId = initial?.id,
    )
    val canSave = normalizedStandardNumber.isNotEmpty() &&
        parsedPartNumbers.isNotEmpty() &&
        !duplicateStandard

    fun submitForm() {
        onSave(
            GroupFormData(
                standardNumber = normalizedStandardNumber,
                partNumbers = parsedPartNumbers,
                hardness = HardnessSet(
                    testPieceHardness = testPieceHardness.trim(),
                    blockHardness = blockHardness.trim(),
                    productHardness = productHardness.trim(),
                ),
                productCategory = productCategory.trim(),
                color = color.trim(),
                tensileStrength = tensileStrength.trim(),
                elongation = elongation.trim(),
                notes = notes.trim(),
            ),
        )
    }

    FullScreenEditorDialog(
        title = if (initial == null) {
            "添加检测标准组"
        } else {
            "编辑检测标准组"
        },
        subtitle = compound.compoundCode,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            if (partNumberConflicts.isEmpty()) {
                submitForm()
            } else {
                showPartConflictConfirmation = true
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                EditorSectionTitle(
                    title = "检测标准",
                    description =
                        "部品号面向客户展示，标准号用于内部区分检测要求",
                )
            }

            item {
                OutlinedTextField(
                    value = standardNumber,
                    onValueChange = {
                        standardNumber = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("检测标准号 *") },
                    placeholder = {
                        Text("例如：B2-12-1078/A")
                    },
                    supportingText = {
                        Text(
                            if (duplicateStandard) {
                                "该检测标准号已存在，请编辑原记录"
                            } else {
                                "标准号作为内部参考，不会突出显示"
                            },
                        )
                    },
                    isError = duplicateStandard,
                    singleLine = true,
                )
            }

            item {
                EditorSectionTitle(
                    title = "适用部品",
                    description =
                        "执行同一检测标准的多个部品号可放在同一组",
                )
            }

            item {
                OutlinedTextField(
                    value = partNumbersText,
                    onValueChange = {
                        partNumbersText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("部品号 *") },
                    placeholder = {
                        Text(
                            "每行一个完整部品号\n例如：\n3DE000023A\n3DE000023C",
                        )
                    },
                    supportingText = {
                        Text(
                            text = if (partNumberConflicts.isEmpty()) {
                                "也支持使用逗号、分号或空格分隔；请填写完整部品号"
                            } else {
                                "${partNumberConflicts.size} 个部品号已用于其他标准，保存时需要确认"
                            },
                            color = if (partNumberConflicts.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    },
                    minLines = 4,
                    maxLines = 7,
                )
            }

            item {
                EditorSectionTitle(
                    title = "硬度标准",
                    description =
                        "优先使用硬度块硬度；为空时使用产品硬度",
                )
            }

            item {
                OutlinedTextField(
                    value = testPieceHardness,
                    onValueChange = {
                        testPieceHardness = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("试片硬度") },
                    placeholder = {
                        Text("例如：33 +2/-2 A")
                    },
                    supportingText = {
                        Text("仅作参考，不用于自动推荐送样硬度")
                    },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = blockHardness,
                    onValueChange = {
                        blockHardness = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("硬度块硬度") },
                    placeholder = {
                        Text("例如：34 +3/-3 A")
                    },
                    supportingText = {
                        Text("送样硬度的第一优先级")
                    },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = productHardness,
                    onValueChange = {
                        productHardness = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("产品硬度") },
                    placeholder = {
                        Text("例如：35 +3/-3 A")
                    },
                    supportingText = {
                        Text("硬度块硬度为空时作为送样依据")
                    },
                    singleLine = true,
                )
            }

            item {
                EditorSectionTitle(
                    title = "低频资料",
                    description =
                        "这些信息默认折叠，需要时再查看",
                )
            }

            item {
                OutlinedTextField(
                    value = productCategory,
                    onValueChange = {
                        productCategory = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("产品类别") },
                    placeholder = {
                        Text("例如：橡胶避振脚")
                    },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("颜色") },
                    placeholder = { Text("例如：黑色") },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = tensileStrength,
                    onValueChange = {
                        tensileStrength = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("拉伸强度") },
                    placeholder = {
                        Text("例如：标准 ≥11.76 MPa；快检 ≥9 MPa")
                    },
                    minLines = 2,
                    maxLines = 4,
                )
            }

            item {
                OutlinedTextField(
                    value = elongation,
                    onValueChange = {
                        elongation = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("伸长率") },
                    placeholder = { Text("例如：≥400%") },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }

    if (showPartConflictConfirmation) {
        val visibleConflicts = partNumberConflicts.entries.take(6)
        val hiddenCount = partNumberConflicts.size - visibleConflicts.size

        AlertDialog(
            onDismissRequest = {
                showPartConflictConfirmation = false
            },
            title = { Text("部品号已用于其他标准") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("以下部品号已出现在其他检测标准中：")
                    visibleConflicts.forEach { (partNumber, standards) ->
                        Text(
                            text = "$partNumber：${standards.joinToString("、")}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (hiddenCount > 0) {
                        Text("另有 $hiddenCount 个冲突部品号未显示")
                    }
                    Text("一个部品可能确实适用多个标准，请确认后再保存。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPartConflictConfirmation = false
                        submitForm()
                    },
                ) {
                    Text("仍然保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPartConflictConfirmation = false
                    },
                ) {
                    Text("返回检查")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenEditorDialog(
    title: String,
    subtitle: String = "",
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            color = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface
            },
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = title,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                )
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme
                                            .typography
                                            .labelMedium,
                                        color = MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            TextButton(onClick = onDismiss) {
                                Text("取消")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = onSave,
                                enabled = canSave,
                            ) {
                                Text("保存")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                content(innerPadding)
            }
        }
    }
}

@Composable
private fun EditorSectionTitle(
    title: String,
    description: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        )
    }
}

@Composable
internal fun DeleteStandardGroupDialog(
    group: PartSpecificationGroup,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除检测标准组？") },
        text = {
            Text(
                "将删除标准 ${group.standardNumber.ifBlank { "（未填写标准号）" }}，适用部品：${group.partNumbers.joinToString("、")}。此操作会立即保存。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                )
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
private fun CompoundGlyph(
    modifier: Modifier = Modifier,
) {
    val iconColor = MaterialTheme
        .colorScheme
        .onPrimaryContainer

    Canvas(modifier = modifier) {
        val lineStartX = 4.dp.toPx()
        val lineEndX = size.width - 4.dp.toPx()
        val strokeWidth = 2.2.dp.toPx()
        val rowYs = floatArrayOf(
            5.dp.toPx(),
            size.height / 2f,
            size.height - 5.dp.toPx(),
        )

        rowYs.forEachIndexed { index, y ->
            drawLine(
                color = iconColor,
                start = Offset(
                    x = lineStartX +
                        (index * 2).dp.toPx(),
                    y = y,
                ),
                end = Offset(
                    x = lineEndX -
                        (index * 2).dp.toPx(),
                    y = y,
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun parsePartNumbers(
    rawValue: String,
): List<String> =
    rawValue
        .split(
            Regex("[\\s,，;；]+"),
        )
        .map { it.trim().uppercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .distinct()

private fun sanitizeDecimalInput(
    rawValue: String,
): String {
    val normalized = rawValue.replace(',', '.')
    val builder = StringBuilder()
    var hasDecimalPoint = false

    normalized.forEach { character ->
        when {
            character.isDigit() -> {
                builder.append(character)
            }

            character == '.' && !hasDecimalPoint -> {
                hasDecimalPoint = true
                builder.append(character)
            }
        }
    }

    return builder.toString()
}

private fun compactEditorNumber(
    value: Double,
): String {
    val asLong = value.toLong()

    return if (value == asLong.toDouble()) {
        asLong.toString()
    } else {
        value.toString()
    }
}
