package com.wmdhs.shorea

import java.util.Locale

internal fun hasDuplicateCompoundCode(
    compounds: List<RubberCompound>,
    compoundCode: String,
    excludingId: Long? = null,
): Boolean = compounds.any { compound ->
    compound.id != excludingId &&
        compound.compoundCode.equals(
            compoundCode,
            ignoreCase = true,
        )
}

internal fun hasDuplicateStandardNumber(
    compound: RubberCompound,
    standardNumber: String,
    excludingGroupId: Long? = null,
): Boolean = compound.groups.any { group ->
    group.id != excludingGroupId &&
        group.standardNumber.equals(
            standardNumber,
            ignoreCase = true,
        )
}

internal fun findPartNumberConflicts(
    compound: RubberCompound,
    partNumbers: List<String>,
    excludingGroupId: Long? = null,
): Map<String, List<String>> {
    val wanted = partNumbers
        .map { it.uppercase(Locale.ROOT) }
        .toSet()

    return compound.groups
        .asSequence()
        .filter { it.id != excludingGroupId }
        .flatMap { group ->
            group.partNumbers.asSequence()
                .filter { it.uppercase(Locale.ROOT) in wanted }
                .map { partNumber ->
                    partNumber.uppercase(Locale.ROOT) to
                        group.standardNumber.ifBlank { "未填写标准号" }
                }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        .mapValues { (_, standards) -> standards.distinct() }
}
