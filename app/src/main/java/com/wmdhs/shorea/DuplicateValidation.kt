package com.wmdhs.shorea

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
                        entry.standardNumber.ifBlank { "未填写标准号" }
                }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        .mapValues { (_, standards) -> standards.distinct() }
}
