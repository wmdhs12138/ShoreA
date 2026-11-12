package com.wmdhs.shorea.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wmdhs.shorea.data.model.HardnessManual
import com.wmdhs.shorea.data.model.ManualPreferences
import com.wmdhs.shorea.data.model.ManualSortOrder
import com.wmdhs.shorea.data.model.ManualViewMode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// 文件名沿用 1.x 版本，保证升级后直接读取原有资料。
private val Context.shoreManualDataStore by preferencesDataStore(name = "shore_lists")

internal data class ManualLoadResult(
    val manual: HardnessManual,
    val loadError: String? = null,
)

internal class ManualRepository(context: Context) {
    private val dataStore = context.applicationContext.shoreManualDataStore

    /** 手册资料。只有手册 JSON 变化时才重新解析，偏好项变更不会触发。 */
    val manual: Flow<ManualLoadResult> = dataStore.data
        .map { preferences -> preferences[MANUAL_JSON_KEY] to preferences[LEGACY_LISTS_JSON_KEY] }
        .distinctUntilChanged()
        .map { (manualJson, legacyListsJson) -> decode(manualJson, legacyListsJson) }
        .flowOn(Dispatchers.Default)
        .catch { exception ->
            if (exception !is IOException) throw exception
            emit(
                ManualLoadResult(
                    manual = HardnessManual(),
                    loadError = "无法读取本地存储：${exception.message ?: "I/O 错误"}",
                ),
            )
        }

    val preferences: Flow<ManualPreferences> = dataStore.data
        .catch { exception ->
            if (exception !is IOException) throw exception
            emit(emptyPreferences())
        }
        .map(::readPreferences)
        .distinctUntilChanged()

    /** 校验并写入；校验失败或 I/O 失败时抛出异常，由调用方回滚界面状态。 */
    suspend fun saveManual(manual: HardnessManual) {
        val json = withContext(Dispatchers.Default) { encodeManualData(manual) }
        dataStore.edit { preferences -> preferences[MANUAL_JSON_KEY] = json }
    }

    suspend fun setSortOrder(order: ManualSortOrder) {
        dataStore.edit { it[SORT_ORDER_KEY] = order.name }
    }

    suspend fun setViewMode(mode: ManualViewMode) {
        dataStore.edit { it[VIEW_MODE_KEY] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    private fun decode(manualJson: String?, legacyListsJson: String?): ManualLoadResult {
        if (manualJson.isNullOrBlank()) {
            return ManualLoadResult(manual = decodeLegacyLists(legacyListsJson))
        }
        return decodeManualDataResult(manualJson).fold(
            onSuccess = { ManualLoadResult(it) },
            onFailure = { error ->
                ManualLoadResult(
                    manual = HardnessManual(),
                    loadError = error.message ?: "本地手册数据无法读取",
                )
            },
        )
    }

    private fun readPreferences(preferences: Preferences): ManualPreferences {
        val defaults = ManualPreferences()
        return ManualPreferences(
            sortOrder = ManualSortOrder.entries
                .firstOrNull { it.name == preferences[SORT_ORDER_KEY] }
                ?: defaults.sortOrder,
            viewMode = ManualViewMode.entries
                .firstOrNull { it.name == preferences[VIEW_MODE_KEY] }
                ?: defaults.viewMode,
            dynamicColor = preferences[DYNAMIC_COLOR_KEY] ?: defaults.dynamicColor,
        )
    }

    private companion object {
        // 名称属于历史版本；实际 JSON schemaVersion 已升级为 2，继续复用以避免搬迁偏好项。
        val MANUAL_JSON_KEY = stringPreferencesKey("hardness_manual_json_v1")
        val LEGACY_LISTS_JSON_KEY = stringPreferencesKey("lists_json")
        val SORT_ORDER_KEY = stringPreferencesKey("home_sort_order")
        val VIEW_MODE_KEY = stringPreferencesKey("home_view_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    }
}
