package com.wmdhs.shorea.data.model

import java.util.Locale

internal fun hasDuplicateCompoundCode(
    compounds: List<RubberCompound>,
    compoundCode: String,
    excludingId: Long? = null,
): Boolean {
    val normalized = compoundCode.trim()
    return normalized.isNotEmpty() && compounds.any { compound ->
        compound.id != excludingId &&
            compound.compoundCode.trim().equals(normalized, ignoreCase = true)
    }
}

internal fun hasDuplicateStandardNumber(
    entries: List<InspectionEntry>,
    compoundId: Long,
    standardNumber: String,
    excludingEntryId: Long? = null,
): Boolean {
    val normalized = standardNumber.trim()
    return normalized.isNotEmpty() && entries.any { entry ->
        entry.id != excludingEntryId &&
            entry.compoundId == compoundId &&
            entry.standardNumber.trim().equals(normalized, ignoreCase = true)
    }
}

/** 同一胶料下，部品号只能属于一个检测标准。返回 部品号 → 已占用它的标准号列表。 */
internal fun findPartNumberConflicts(
    entries: List<InspectionEntry>,
    compoundId: Long,
    partNumbers: List<String>,
    excludingEntryId: Long? = null,
): Map<String, List<String>> {
    val wanted = normalizePartNumbers(partNumbers)
        .associateBy { it.uppercase(Locale.ROOT) }
        .keys

    return entries
        .asSequence()
        .filter { it.id != excludingEntryId && it.compoundId == compoundId }
        .flatMap { entry ->
            entry.partNumbers.asSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && it.uppercase(Locale.ROOT) in wanted }
                .map { partNumber ->
                    partNumber.uppercase(Locale.ROOT) to
                        entry.standardNumber.ifBlank { UNNAMED_STANDARD }
                }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        .mapValues { (_, standards) -> standards.distinct() }
}

internal fun validateManual(manual: HardnessManual): Result<HardnessManual> =
    runCatching {
        val duplicateCompoundId = manual.compounds
            .groupBy(RubberCompound::id)
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicateCompoundId == null) {
            "手册中存在重复的胶料 ID：${duplicateCompoundId?.key}"
        }

        val duplicateCompoundCode = manual.compounds
            .groupBy { it.compoundCode.trim().uppercase(Locale.ROOT) }
            .entries
            .firstOrNull { it.key.isNotEmpty() && it.value.size > 1 }
        require(duplicateCompoundCode == null) {
            "手册中存在重复的胶料号：${duplicateCompoundCode?.value?.first()?.compoundCode}"
        }

        manual.compounds.forEach { compound ->
            require(compound.id >= 0L) {
                "胶料 ${compound.compoundCode} 的 ID 非法"
            }
            require(compound.compoundCode.trim().isNotEmpty()) {
                "存在空胶料号的胶料资料"
            }
        }

        val compoundsById = manual.compounds.associateBy(RubberCompound::id)
        val duplicateEntryId = manual.inspectionEntries
            .groupBy(InspectionEntry::id)
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicateEntryId == null) {
            "手册中存在重复的检测标准 ID：${duplicateEntryId?.key}"
        }

        val usedStandards = mutableSetOf<Pair<Long, String>>()
        val usedParts = mutableMapOf<Pair<Long, String>, InspectionEntry>()
        manual.inspectionEntries.forEach { entry ->
            val standardLabel = entry.standardNumber.ifBlank { UNNAMED_STANDARD }
            require(entry.id >= 0L) {
                "检测标准 $standardLabel 的 ID 非法"
            }
            val compound = compoundsById[entry.compoundId]
            require(compound != null) {
                "检测标准 $standardLabel 关联了不存在的胶料 ID ${entry.compoundId}"
            }
            val normalizedParts = normalizePartNumbers(entry.partNumbers)
            require(normalizedParts.isNotEmpty()) {
                "胶料 ${compound.compoundCode} 的检测标准 $standardLabel 至少需要一个部品号"
            }
            require(entry.partNumbers == normalizedParts) {
                "胶料 ${compound.compoundCode} 的检测标准 $standardLabel 的部品号未按统一规则规范化"
            }

            val normalizedStandard = entry.standardNumber.trim().uppercase(Locale.ROOT)
            if (normalizedStandard.isNotEmpty()) {
                require(usedStandards.add(entry.compoundId to normalizedStandard)) {
                    "胶料 ${compound.compoundCode} 中的标准号 ${entry.standardNumber} 重复"
                }
            }

            normalizedParts.forEach { partNumber ->
                val key = entry.compoundId to partNumber.uppercase(Locale.ROOT)
                val previous = usedParts.putIfAbsent(key, entry)
                require(previous == null) {
                    val previousStandard = previous?.standardNumber?.ifBlank { UNNAMED_STANDARD }
                    "胶料 ${compound.compoundCode} 中的部品号 $partNumber 同时属于检测标准 $previousStandard 和 $standardLabel"
                }
            }
        }

        manual
    }

internal const val UNNAMED_STANDARD = "未填写标准号"
