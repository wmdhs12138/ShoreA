package com.wmdhs.shorea

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

internal data class ManualBackup(
    val exportedAtEpochMillis: Long,
    val compounds: List<RubberCompound>,
) {
    val groupCount: Int
        get() = compounds.sumOf { it.groups.size }

    val partCount: Int
        get() = compounds.sumOf { it.totalPartCount }
}

internal fun encodeManualBackup(
    compounds: List<RubberCompound>,
    exportedAtEpochMillis: Long = System.currentTimeMillis(),
): String = JSONObject()
    .put("backupFormat", BACKUP_FORMAT)
    .put("backupVersion", BACKUP_VERSION)
    .put("exportedAtEpochMillis", exportedAtEpochMillis)
    .put("manual", JSONObject(encodeCompounds(compounds)))
    .toString(2)

internal fun decodeManualBackup(
    rawValue: String,
): Result<ManualBackup> = runCatching {
    val root = JSONObject(rawValue)
    require(root.optString("backupFormat") == BACKUP_FORMAT) {
        "不是 ShoreA 硬度块手册备份"
    }
    require(root.optInt("backupVersion", -1) == BACKUP_VERSION) {
        "暂不支持此备份版本"
    }

    val manual = root.optJSONObject("manual")
        ?: error("备份中缺少手册数据")
    val compounds = decodeCompoundsResult(
        manual.toString(),
    ).getOrThrow()

    require(compounds.isNotEmpty()) {
        "备份中没有可导入的胶料资料"
    }

    ManualBackup(
        exportedAtEpochMillis = root.optLong(
            "exportedAtEpochMillis",
            0L,
        ),
        compounds = compounds,
    )
}

internal fun mergeManualBackup(
    current: List<RubberCompound>,
    imported: List<RubberCompound>,
): List<RubberCompound> {
    val result = current.toMutableList()
    var nextCompoundId = nextEntityId(
        result.map(RubberCompound::id),
    )

    imported.forEach { importedCompound ->
        val existingIndex = result.indexOfFirst { existing ->
            existing.compoundCode.equals(
                importedCompound.compoundCode,
                ignoreCase = true,
            )
        }

        if (existingIndex < 0) {
            val firstGroupId = nextEntityId(emptyList())
            result += importedCompound.copy(
                id = nextCompoundId++,
                groups = importedCompound.groups.mapIndexed {
                        index,
                        group,
                    -> group.copy(id = firstGroupId + index)
                },
            )
            return@forEach
        }

        val existing = result[existingIndex]
        val groups = existing.groups.toMutableList()
        var nextGroupId = nextEntityId(
            groups.map(PartSpecificationGroup::id),
        )

        importedCompound.groups.forEach { importedGroup ->
            val groupIndex = groups.indexOfFirst { currentGroup ->
                currentGroup.standardNumber.equals(
                    importedGroup.standardNumber,
                    ignoreCase = true,
                )
            }

            if (groupIndex < 0) {
                groups += importedGroup.copy(id = nextGroupId++)
            } else {
                val currentGroup = groups[groupIndex]
                groups[groupIndex] = currentGroup.copy(
                    partNumbers = (currentGroup.partNumbers + importedGroup.partNumbers).distinct(),
                    hardness = HardnessSet(
                        testPieceHardness = currentGroup.hardness.testPieceHardness.ifBlank {
                            importedGroup.hardness.testPieceHardness
                        },
                        blockHardness = currentGroup.hardness.blockHardness.ifBlank {
                            importedGroup.hardness.blockHardness
                        },
                        productHardness = currentGroup.hardness.productHardness.ifBlank {
                            importedGroup.hardness.productHardness
                        },
                    ),
                    productCategory = currentGroup.productCategory.ifBlank { importedGroup.productCategory },
                    color = currentGroup.color.ifBlank { importedGroup.color },
                    tensileStrength = currentGroup.tensileStrength.ifBlank { importedGroup.tensileStrength },
                    elongation = currentGroup.elongation.ifBlank { importedGroup.elongation },
                    notes = currentGroup.notes.ifBlank { importedGroup.notes },
                )
            }
        }

        result[existingIndex] = existing.copy(
            testPieceCureTemperatureC = existing.testPieceCureTemperatureC.ifBlank {
                importedCompound.testPieceCureTemperatureC
            },
            testPieceCureTimeMinutes = existing.testPieceCureTimeMinutes.ifBlank {
                importedCompound.testPieceCureTimeMinutes
            },
            customBlockCureTimeMinutes = existing.customBlockCureTimeMinutes.ifBlank {
                importedCompound.customBlockCureTimeMinutes
            },
            groups = groups,
            notes = existing.notes.ifBlank { importedCompound.notes },
        )
    }

    return result
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
private const val BACKUP_VERSION = 1
