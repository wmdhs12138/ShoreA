package com.wmdhs.shorea

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

internal data class ManualBackup(
    val exportedAtEpochMillis: Long,
    val manual: HardnessManual,
) {
    val compounds: List<RubberCompound>
        get() = manual.compounds

    val inspectionEntries: List<InspectionEntry>
        get() = manual.inspectionEntries

    val compoundCount: Int
        get() = manual.compounds.size

    val entryCount: Int
        get() = manual.inspectionEntries.size

    val partCount: Int
        get() = manual.totalPartAssociationCount
}

internal fun encodeManualBackup(
    manual: HardnessManual,
    exportedAtEpochMillis: Long = System.currentTimeMillis(),
): String = JSONObject()
    .put("backupFormat", BACKUP_FORMAT)
    .put("backupVersion", BACKUP_VERSION)
    .put("exportedAtEpochMillis", exportedAtEpochMillis)
    .put("manual", JSONObject(encodeManualData(manual)))
    .toString(2)

internal fun decodeManualBackup(
    rawValue: String,
): Result<ManualBackup> = runCatching {
    val root = JSONObject(rawValue)
    require(root.optString("backupFormat") == BACKUP_FORMAT) {
        "不是 ShoreA 硬度块手册备份"
    }
    val backupVersion = root.optInt("backupVersion", -1)
    require(backupVersion == 1 || backupVersion == 2) {
        "暂不支持此备份版本"
    }
    val manualObject = root.optJSONObject("manual")
        ?: error("备份中缺少手册数据")
    val schemaVersion = manualObject.optInt("schemaVersion", -1)
    if (backupVersion == 2) {
        require(schemaVersion == 2) {
            "备份中的手册数据版本不正确"
        }
    } else {
        require(schemaVersion == 1) {
            "备份中的旧手册数据版本不正确"
        }
    }
    val manual = decodeManualDataResult(manualObject.toString()).getOrThrow()
    require(manual.compounds.isNotEmpty()) {
        "备份中没有可导入的胶料资料"
    }
    ManualBackup(
        exportedAtEpochMillis = root.optLong("exportedAtEpochMillis", 0L),
        manual = manual,
    )
}

internal fun mergeManualBackup(
    current: HardnessManual,
    imported: HardnessManual,
): HardnessManual {
    validateManual(current).getOrThrow()
    validateManual(imported).getOrThrow()

    val resultCompounds = current.compounds.toMutableList()
    val resultEntries = current.inspectionEntries.toMutableList()
    val compoundIds = EntityIdAllocator(resultCompounds.map(RubberCompound::id))
    val entryIds = EntityIdAllocator(resultEntries.map(InspectionEntry::id))

    imported.compounds.forEach { importedCompound ->
        val existingIndex = resultCompounds.indexOfFirst { existing ->
            existing.compoundCode.trim().equals(
                importedCompound.compoundCode.trim(),
                ignoreCase = true,
            )
        }
        if (existingIndex < 0) {
            val targetCompoundId = compoundIds.allocate()
            val targetCompound = importedCompound.copy(
                id = targetCompoundId,
                compoundCode = importedCompound.compoundCode.trim(),
            )
            resultCompounds += targetCompound
            imported.entriesForCompound(importedCompound.id).forEach { entry ->
                resultEntries += entry.copy(
                    id = entryIds.allocate(),
                    compoundId = targetCompoundId,
                    standardNumber = entry.standardNumber.trim(),
                    partNumbers = normalizePartNumbers(entry.partNumbers),
                    hardness = entry.hardness.normalized(),
                )
            }
            return@forEach
        }

        val currentCompound = resultCompounds[existingIndex]
        val mergedCompound = currentCompound.copy(
            compoundCode = currentCompound.compoundCode.ifBlank {
                importedCompound.compoundCode.trim()
            },
            testPieceCureTemperatureC = currentCompound.testPieceCureTemperatureC
                .ifBlank { importedCompound.testPieceCureTemperatureC },
            testPieceCureTimeMinutes = currentCompound.testPieceCureTimeMinutes
                .ifBlank { importedCompound.testPieceCureTimeMinutes },
            customBlockCureTimeMinutes = currentCompound.customBlockCureTimeMinutes
                .ifBlank { importedCompound.customBlockCureTimeMinutes },
            notes = currentCompound.notes.ifBlank { importedCompound.notes },
        )
        resultCompounds[existingIndex] = mergedCompound

        imported.entriesForCompound(importedCompound.id).forEach { importedEntry ->
            val targetIndex = resultEntries.indexOfFirst { currentEntry ->
                currentEntry.compoundId == currentCompound.id &&
                    entriesRepresentSameRecord(currentEntry, importedEntry)
            }
            if (targetIndex < 0) {
                resultEntries += importedEntry.copy(
                    id = entryIds.allocate(),
                    compoundId = currentCompound.id,
                    standardNumber = importedEntry.standardNumber.trim(),
                    partNumbers = normalizePartNumbers(importedEntry.partNumbers),
                    hardness = importedEntry.hardness.normalized(),
                )
            } else {
                val currentEntry = resultEntries[targetIndex]
                resultEntries[targetIndex] = mergeEntries(
                    current = currentEntry,
                    imported = importedEntry,
                )
            }
        }
    }

    return validateManual(
        HardnessManual(
            compounds = resultCompounds,
            inspectionEntries = resultEntries,
        ),
    ).getOrThrow()
}

private fun entriesRepresentSameRecord(
    current: InspectionEntry,
    imported: InspectionEntry,
): Boolean {
    val currentStandard = current.standardNumber.trim()
    val importedStandard = imported.standardNumber.trim()
    if (currentStandard.isNotEmpty() && importedStandard.isNotEmpty()) {
        return currentStandard.equals(importedStandard, ignoreCase = true)
    }
    if (currentStandard.isNotEmpty() != importedStandard.isNotEmpty()) {
        return false
    }
    val currentParts = normalizePartNumbers(current.partNumbers)
        .map { it.uppercase(Locale.ROOT) }
        .toSet()
    val importedParts = normalizePartNumbers(imported.partNumbers)
        .map { it.uppercase(Locale.ROOT) }
        .toSet()
    return currentParts.isNotEmpty() && importedParts.isNotEmpty() &&
        (currentParts == importedParts || currentParts.intersect(importedParts).isNotEmpty())
}

private fun mergeEntries(
    current: InspectionEntry,
    imported: InspectionEntry,
): InspectionEntry = current.copy(
    standardNumber = current.standardNumber.ifBlank { imported.standardNumber },
    partNumbers = normalizePartNumbers(current.partNumbers + imported.partNumbers),
    hardness = HardnessSet(
        testPieceHardness = current.hardness.testPieceHardness.ifBlank {
            imported.hardness.testPieceHardness
        },
        blockHardness = current.hardness.blockHardness.ifBlank {
            imported.hardness.blockHardness
        },
        productHardness = current.hardness.productHardness.ifBlank {
            imported.hardness.productHardness
        },
    ).normalized(),
    productCategory = current.productCategory.ifBlank { imported.productCategory },
    color = current.color.ifBlank { imported.color },
    tensileStrength = current.tensileStrength.ifBlank { imported.tensileStrength },
    elongation = current.elongation.ifBlank { imported.elongation },
    notes = current.notes.ifBlank { imported.notes },
)

private class EntityIdAllocator(existingIds: Iterable<Long>) {
    private val usedIds = existingIds.toMutableSet()
    private var nextCandidate = nextEntityId(usedIds)

    fun allocate(): Long {
        while (!usedIds.add(nextCandidate)) {
            nextCandidate = nextEntityId(usedIds)
        }
        val allocated = nextCandidate
        nextCandidate = nextEntityId(usedIds)
        return allocated
    }
}

internal fun manualBackupFileName(
    now: Instant = Instant.now(),
): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
        .withZone(ZoneId.systemDefault())
    return "ShoreA-backup-${formatter.format(now)}.json"
}

internal fun formatBackupTime(epochMillis: Long): String {
    if (epochMillis <= 0L) {
        return "未记录"
    }
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

private const val BACKUP_FORMAT =
    "com.wmdhs.shorea.hardness-manual-backup"
private const val BACKUP_VERSION = 2
