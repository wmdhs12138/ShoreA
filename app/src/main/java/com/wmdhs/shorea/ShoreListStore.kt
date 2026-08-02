package com.wmdhs.shorea

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.shoreManualDataStore by preferencesDataStore(
    name = "shore_lists",
)

internal data class HardnessManualState(
    val manual: HardnessManual,
    val loadError: String? = null,
)

internal class HardnessManualStore(
    private val context: Context,
) {
    val state: Flow<HardnessManualState> = context.shoreManualDataStore.data
        .map { preferences ->
            val manualJson = preferences[MANUAL_JSON_KEY]
            if (manualJson.isNullOrBlank()) {
                HardnessManualState(
                    manual = decodeLegacyLists(
                        preferences[LEGACY_LISTS_JSON_KEY],
                    ),
                )
            } else {
                decodeManualDataResult(manualJson).fold(
                    onSuccess = { manual -> HardnessManualState(manual) },
                    onFailure = { error ->
                        HardnessManualState(
                            manual = HardnessManual(),
                            loadError = error.message
                                ?: "本地手册数据无法读取",
                        )
                    },
                )
            }
        }
        .catch { exception ->
            if (exception is IOException) {
                emit(
                    HardnessManualState(
                        manual = HardnessManual(),
                        loadError = "无法读取本地存储：${exception.message ?: "I/O 错误"}",
                    ),
                )
            } else {
                throw exception
            }
        }

    suspend fun saveManual(manual: HardnessManual) {
        validateManual(manual).getOrThrow()
        context.shoreManualDataStore.edit { preferences ->
            preferences[MANUAL_JSON_KEY] = encodeManualData(manual)
        }
    }

    private companion object {
        // 名称属于历史版本；实际 JSON schemaVersion 已升级为 2，继续复用以避免搬迁偏好项。
        val MANUAL_JSON_KEY = stringPreferencesKey("hardness_manual_json_v1")
        val LEGACY_LISTS_JSON_KEY = stringPreferencesKey("lists_json")
    }
}

internal fun encodeManualData(manual: HardnessManual): String {
    validateManual(manual).getOrThrow()
    val root = JSONObject()
        .put("schemaVersion", 2)
        .put(
            "compounds",
            JSONArray().apply {
                manual.compounds.forEach { compound -> put(encodeCompound(compound)) }
            },
        )
        .put(
            "inspectionEntries",
            JSONArray().apply {
                manual.inspectionEntries.forEach { entry -> put(encodeInspectionEntry(entry)) }
            },
        )
    return root.toString()
}

internal fun decodeManualDataResult(
    rawValue: String,
): Result<HardnessManual> = runCatching {
    val root = JSONObject(rawValue)
    when (root.optInt("schemaVersion", -1)) {
        1 -> decodeV1AndMigrate(root)
        2 -> decodeV2(root)
        else -> error("不支持的手册数据版本")
    }
}

private fun decodeV2(root: JSONObject): HardnessManual {
    val compoundsArray = root.optJSONArray("compounds")
        ?: error("手册数据缺少胶料资料")
    val entriesArray = root.optJSONArray("inspectionEntries")
        ?: error("手册数据缺少检测标准资料")
    val compounds = buildList {
        for (index in 0 until compoundsArray.length()) {
            add(decodeCompound(compoundsArray.getJSONObject(index)))
        }
    }
    val entries = buildList {
        for (index in 0 until entriesArray.length()) {
            add(decodeInspectionEntry(entriesArray.getJSONObject(index)))
        }
    }
    return validateManual(
        HardnessManual(compounds = compounds, inspectionEntries = entries),
    ).getOrThrow()
}

private fun decodeV1AndMigrate(root: JSONObject): HardnessManual {
    val compoundsArray = root.optJSONArray("compounds")
        ?: error("手册数据缺少胶料资料")
    val legacyCompounds = buildList {
        for (index in 0 until compoundsArray.length()) {
            val jsonCompound = compoundsArray.getJSONObject(index)
            val groupsArray = jsonCompound.optJSONArray("groups")
                ?: error("旧版胶料资料缺少检测标准列表")
            val groups = buildList {
                for (groupIndex in 0 until groupsArray.length()) {
                    add(
                        decodeLegacyGroup(
                            groupsArray.getJSONObject(groupIndex),
                        ),
                    )
                }
            }
            add(
                LegacyCompoundV1(
                    id = requiredId(jsonCompound, "胶料"),
                    compoundCode = jsonCompound.optString("compoundCode", "").trim(),
                    testPieceCureTemperatureC = jsonCompound
                        .optString("testPieceCureTemperatureC", "")
                        .trim(),
                    testPieceCureTimeMinutes = jsonCompound
                        .optString("testPieceCureTimeMinutes", "")
                        .trim(),
                    customBlockCureTimeMinutes = jsonCompound
                        .optString("customBlockCureTimeMinutes", "")
                        .trim(),
                    groups = groups,
                    notes = jsonCompound.optString("notes", "").trim(),
                ),
            )
        }
    }
    return migrateV1(legacyCompounds)
}

private data class LegacyCompoundV1(
    val id: Long,
    val compoundCode: String,
    val testPieceCureTemperatureC: String,
    val testPieceCureTimeMinutes: String,
    val customBlockCureTimeMinutes: String,
    val groups: List<LegacyInspectionGroupV1>,
    val notes: String,
)

private data class LegacyInspectionGroupV1(
    val id: Long,
    val standardNumber: String,
    val partNumbers: List<String>,
    val hardness: HardnessSet,
    val productCategory: String,
    val color: String,
    val tensileStrength: String,
    val elongation: String,
    val notes: String,
)

private fun decodeLegacyGroup(json: JSONObject): LegacyInspectionGroupV1 {
    val parts = decodePartNumbers(json.optJSONArray("partNumbers"))
    return LegacyInspectionGroupV1(
        id = requiredId(json, "检测标准"),
        standardNumber = json.optString("standardNumber", "").trim(),
        partNumbers = parts,
        hardness = decodeHardness(json.optJSONObject("hardness")),
        productCategory = json.optString("productCategory", "").trim(),
        color = json.optString("color", "").trim(),
        tensileStrength = json.optString("tensileStrength", "").trim(),
        elongation = json.optString("elongation", "").trim(),
        notes = json.optString("notes", "").trim(),
    )
}

private fun migrateV1(legacyCompounds: List<LegacyCompoundV1>): HardnessManual {
    val entries = mutableListOf<InspectionEntry>()
    val usedEntryIds = mutableSetOf<Long>()
    val legacyGroupIds = legacyCompounds.flatMap { compound ->
        compound.groups.map(LegacyInspectionGroupV1::id)
    }
    var nextGeneratedId = nextEntityId(legacyGroupIds + legacyCompounds.map(LegacyCompoundV1::id))

    fun allocateEntryId(preferredId: Long): Long {
        if (preferredId >= 0L && usedEntryIds.add(preferredId)) {
            return preferredId
        }
        while (!usedEntryIds.add(nextGeneratedId)) {
            nextGeneratedId = nextEntityId(usedEntryIds)
        }
        val allocated = nextGeneratedId
        nextGeneratedId = nextEntityId(usedEntryIds)
        return allocated
    }

    val compounds = legacyCompounds.map { legacy ->
        require(legacy.id >= 0L) {
            "胶料 ${legacy.compoundCode} 的 ID 非法"
        }
        legacy.groups.forEach { group ->
            val parts = normalizePartNumbers(group.partNumbers)
            require(parts.isNotEmpty()) {
                "胶料 ${legacy.compoundCode} 的检测标准 ${group.standardNumber.ifBlank { "未填写标准号" }} 至少需要一个部品号"
            }
            entries += InspectionEntry(
                id = allocateEntryId(group.id),
                compoundId = legacy.id,
                standardNumber = group.standardNumber,
                partNumbers = parts,
                hardness = group.hardness.normalized(),
                productCategory = group.productCategory,
                color = group.color,
                tensileStrength = group.tensileStrength,
                elongation = group.elongation,
                notes = group.notes,
            )
        }
        RubberCompound(
            id = legacy.id,
            compoundCode = legacy.compoundCode,
            testPieceCureTemperatureC = legacy.testPieceCureTemperatureC,
            testPieceCureTimeMinutes = legacy.testPieceCureTimeMinutes,
            customBlockCureTimeMinutes = legacy.customBlockCureTimeMinutes,
            notes = legacy.notes,
        )
    }
    return validateManual(
        HardnessManual(compounds = compounds, inspectionEntries = entries),
    ).getOrThrow()
}

private fun encodeCompound(compound: RubberCompound): JSONObject = JSONObject()
    .put("id", compound.id)
    .put("compoundCode", compound.compoundCode)
    .put("testPieceCureTemperatureC", compound.testPieceCureTemperatureC)
    .put("testPieceCureTimeMinutes", compound.testPieceCureTimeMinutes)
    .put("customBlockCureTimeMinutes", compound.customBlockCureTimeMinutes)
    .put("notes", compound.notes)

private fun encodeInspectionEntry(entry: InspectionEntry): JSONObject = JSONObject()
    .put("id", entry.id)
    .put("compoundId", entry.compoundId)
    .put("standardNumber", entry.standardNumber)
    .put("partNumbers", JSONArray(entry.partNumbers))
    .put(
        "hardness",
        JSONObject()
            .put("testPiece", entry.hardness.testPieceHardness)
            .put("block", entry.hardness.blockHardness)
            .put("product", entry.hardness.productHardness),
    )
    .put("productCategory", entry.productCategory)
    .put("color", entry.color)
    .put("tensileStrength", entry.tensileStrength)
    .put("elongation", entry.elongation)
    .put("notes", entry.notes)

private fun decodeCompound(json: JSONObject): RubberCompound = RubberCompound(
    id = requiredId(json, "胶料"),
    compoundCode = json.optString("compoundCode", "").trim(),
    testPieceCureTemperatureC = json.optString("testPieceCureTemperatureC", "").trim(),
    testPieceCureTimeMinutes = json.optString("testPieceCureTimeMinutes", "").trim(),
    customBlockCureTimeMinutes = json.optString("customBlockCureTimeMinutes", "").trim(),
    notes = json.optString("notes", "").trim(),
)

private fun decodeInspectionEntry(
    json: JSONObject,
): InspectionEntry {
    val rawParts = decodePartNumbers(json.optJSONArray("partNumbers"))
    return InspectionEntry(
        id = requiredId(json, "检测标准"),
        compoundId = json.optLong("compoundId", Long.MIN_VALUE).also {
            require(it != Long.MIN_VALUE) { "检测标准缺少所属胶料 ID" }
        },
        standardNumber = json.optString("standardNumber", "").trim(),
        partNumbers = rawParts,
        hardness = decodeHardness(json.optJSONObject("hardness")),
        productCategory = json.optString("productCategory", "").trim(),
        color = json.optString("color", "").trim(),
        tensileStrength = json.optString("tensileStrength", "").trim(),
        elongation = json.optString("elongation", "").trim(),
        notes = json.optString("notes", "").trim(),
    )
}

private fun decodeHardness(json: JSONObject?): HardnessSet = HardnessSet(
    testPieceHardness = json?.optString("testPiece", "").orEmpty().trim(),
    blockHardness = json?.optString("block", "").orEmpty().trim(),
    productHardness = json?.optString("product", "").orEmpty().trim(),
)

private fun decodePartNumbers(array: JSONArray?): List<String> {
    if (array == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getString(index))
        }
    }.let(::normalizePartNumbers)
}

private fun requiredId(json: JSONObject, kind: String): Long =
    json.optLong("id", Long.MIN_VALUE).also { id ->
        require(id != Long.MIN_VALUE) { "$kind 资料缺少 ID" }
        require(id >= 0L) { "$kind ID 非法：$id" }
    }

private fun decodeLegacyLists(rawValue: String?): HardnessManual {
    if (rawValue.isNullOrBlank()) {
        return HardnessManual()
    }
    return runCatching {
        val legacyArray = JSONArray(rawValue)
        val compounds = buildList {
            for (index in 0 until legacyArray.length()) {
                val jsonList = legacyArray.optJSONObject(index) ?: continue
                val id = jsonList.optLong("id", -1L)
                val name = jsonList.optString("name", "").trim()
                if (id < 0L || name.isEmpty()) continue
                val tags = jsonList.optJSONArray("tags")?.let(::decodePartNumbers).orEmpty()
                val migrationNote = if (tags.isEmpty()) {
                    "由旧版列表自动迁移，请补充胶料资料。"
                } else {
                    "由旧版列表自动迁移。原标签：${tags.joinToString("、")}"
                }
                add(RubberCompound(id = id, compoundCode = name, notes = migrationNote))
            }
        }.distinctBy(RubberCompound::id)
        HardnessManual(compounds = compounds)
    }.getOrDefault(HardnessManual())
}
