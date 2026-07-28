package com.wmdhs.shorea

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.shoreManualDataStore by preferencesDataStore(
    name = "shore_lists",
)

internal class HardnessManualStore(
    private val context: Context,
) {
    val compounds: Flow<List<RubberCompound>> = context.shoreManualDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val manualJson = preferences[MANUAL_JSON_KEY]

            if (manualJson.isNullOrBlank()) {
                decodeLegacyLists(preferences[LEGACY_LISTS_JSON_KEY])
            } else {
                decodeCompounds(manualJson)
            }
        }

    suspend fun saveCompounds(compounds: List<RubberCompound>) {
        context.shoreManualDataStore.edit { preferences ->
            preferences[MANUAL_JSON_KEY] = encodeCompounds(compounds)
        }
    }

    private companion object {
        val MANUAL_JSON_KEY = stringPreferencesKey("hardness_manual_json_v1")
        val LEGACY_LISTS_JSON_KEY = stringPreferencesKey("lists_json")
    }
}

private fun encodeCompounds(compounds: List<RubberCompound>): String {
    val root = JSONObject()
        .put("schemaVersion", 1)

    val compoundsArray = JSONArray()

    compounds.forEach { compound ->
        val groupsArray = JSONArray()

        compound.groups.forEach { group ->
            val partNumbersArray = JSONArray()
            group.partNumbers.forEach(partNumbersArray::put)

            groupsArray.put(
                JSONObject()
                    .put("id", group.id)
                    .put("partNumbers", partNumbersArray)
                    .put(
                        "hardness",
                        JSONObject()
                            .put(
                                "testPiece",
                                group.hardness.testPieceHardness,
                            )
                            .put(
                                "block",
                                group.hardness.blockHardness,
                            )
                            .put(
                                "product",
                                group.hardness.productHardness,
                            ),
                    )
                    .put("productCategory", group.productCategory)
                    .put("color", group.color)
                    .put("tensileStrength", group.tensileStrength)
                    .put("elongation", group.elongation)
                    .put("notes", group.notes),
            )
        }

        compoundsArray.put(
            JSONObject()
                .put("id", compound.id)
                .put("compoundCode", compound.compoundCode)
                .put(
                    "testPieceCureTemperatureC",
                    compound.testPieceCureTemperatureC,
                )
                .put(
                    "testPieceCureTimeMinutes",
                    compound.testPieceCureTimeMinutes,
                )
                .put(
                    "customBlockCureTimeMinutes",
                    compound.customBlockCureTimeMinutes,
                )
                .put("groups", groupsArray)
                .put("notes", compound.notes),
        )
    }

    root.put("compounds", compoundsArray)
    return root.toString()
}

private fun decodeCompounds(rawValue: String): List<RubberCompound> =
    runCatching {
        val root = JSONObject(rawValue)
        val compoundsArray = root.optJSONArray("compounds")
            ?: return@runCatching emptyList()

        buildList {
            for (index in 0 until compoundsArray.length()) {
                val jsonCompound = compoundsArray.optJSONObject(index)
                    ?: continue
                val id = jsonCompound.optLong("id", -1L)
                val compoundCode = jsonCompound
                    .optString("compoundCode", "")
                    .trim()

                if (id < 0L || compoundCode.isEmpty()) {
                    continue
                }

                val groupsArray = jsonCompound.optJSONArray("groups")
                val groups = buildList {
                    if (groupsArray != null) {
                        for (groupIndex in 0 until groupsArray.length()) {
                            val jsonGroup = groupsArray
                                .optJSONObject(groupIndex)
                                ?: continue
                            val groupId = jsonGroup.optLong("id", -1L)
                            val partNumbers = jsonGroup
                                .optJSONArray("partNumbers")
                                .toStringList()

                            if (groupId < 0L || partNumbers.isEmpty()) {
                                continue
                            }

                            val hardnessJson = jsonGroup
                                .optJSONObject("hardness")

                            add(
                                PartSpecificationGroup(
                                    id = groupId,
                                    partNumbers = partNumbers,
                                    hardness = HardnessSet(
                                        testPieceHardness = hardnessJson
                                            ?.optString("testPiece", "")
                                            .orEmpty()
                                            .trim(),
                                        blockHardness = hardnessJson
                                            ?.optString("block", "")
                                            .orEmpty()
                                            .trim(),
                                        productHardness = hardnessJson
                                            ?.optString("product", "")
                                            .orEmpty()
                                            .trim(),
                                    ),
                                    productCategory = jsonGroup
                                        .optString("productCategory", "")
                                        .trim(),
                                    color = jsonGroup
                                        .optString("color", "")
                                        .trim(),
                                    tensileStrength = jsonGroup
                                        .optString("tensileStrength", "")
                                        .trim(),
                                    elongation = jsonGroup
                                        .optString("elongation", "")
                                        .trim(),
                                    notes = jsonGroup
                                        .optString("notes", "")
                                        .trim(),
                                ),
                            )
                        }
                    }
                }.distinctBy(PartSpecificationGroup::id)

                add(
                    RubberCompound(
                        id = id,
                        compoundCode = compoundCode,
                        testPieceCureTemperatureC = jsonCompound
                            .optString(
                                "testPieceCureTemperatureC",
                                "",
                            )
                            .trim(),
                        testPieceCureTimeMinutes = jsonCompound
                            .optString(
                                "testPieceCureTimeMinutes",
                                "",
                            )
                            .trim(),
                        customBlockCureTimeMinutes = jsonCompound
                            .optString(
                                "customBlockCureTimeMinutes",
                                "",
                            )
                            .trim(),
                        groups = groups,
                        notes = jsonCompound
                            .optString("notes", "")
                            .trim(),
                    ),
                )
            }
        }.distinctBy(RubberCompound::id)
    }.getOrDefault(emptyList())

private fun decodeLegacyLists(rawValue: String?): List<RubberCompound> {
    if (rawValue.isNullOrBlank()) {
        return emptyList()
    }

    return runCatching {
        val legacyArray = JSONArray(rawValue)

        buildList {
            for (index in 0 until legacyArray.length()) {
                val jsonList = legacyArray.optJSONObject(index)
                    ?: continue
                val id = jsonList.optLong("id", -1L)
                val name = jsonList.optString("name", "").trim()

                if (id < 0L || name.isEmpty()) {
                    continue
                }

                val tags = jsonList.optJSONArray("tags").toStringList()
                val migrationNote = if (tags.isEmpty()) {
                    "由旧版列表自动迁移，请补充胶料资料。"
                } else {
                    "由旧版列表自动迁移。原标签：${tags.joinToString("、")}"
                }

                add(
                    RubberCompound(
                        id = id,
                        compoundCode = name,
                        notes = migrationNote,
                    ),
                )
            }
        }.distinctBy(RubberCompound::id)
    }.getOrDefault(emptyList())
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (index in 0 until length()) {
            val value = optString(index, "").trim()

            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }.distinct()
}
