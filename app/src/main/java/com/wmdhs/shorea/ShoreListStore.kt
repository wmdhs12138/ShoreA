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

private val Context.shoreListDataStore by preferencesDataStore(
    name = "shore_lists",
)

internal class ShoreListStore(
    private val context: Context,
) {
    val lists: Flow<List<ShoreList>> = context.shoreListDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            decodeLists(preferences[LISTS_JSON_KEY])
        }

    suspend fun saveLists(lists: List<ShoreList>) {
        context.shoreListDataStore.edit { preferences ->
            preferences[LISTS_JSON_KEY] = encodeLists(lists)
        }
    }

    private companion object {
        val LISTS_JSON_KEY = stringPreferencesKey("lists_json")

        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_TAGS = "tags"
    }
}

private fun encodeLists(lists: List<ShoreList>): String {
    val array = JSONArray()

    lists.forEach { list ->
        val tags = JSONArray()
        list.tags.forEach(tags::put)

        array.put(
            JSONObject()
                .put("id", list.id)
                .put("name", list.name)
                .put("tags", tags),
        )
    }

    return array.toString()
}

private fun decodeLists(rawValue: String?): List<ShoreList> {
    if (rawValue.isNullOrBlank()) {
        return emptyList()
    }

    return runCatching {
        val array = JSONArray(rawValue)

        buildList {
            for (index in 0 until array.length()) {
                val jsonList = array.optJSONObject(index) ?: continue
                val id = jsonList.optLong("id", -1L)
                val name = jsonList.optString("name", "").trim()

                if (id < 0L || name.isEmpty()) {
                    continue
                }

                val jsonTags = jsonList.optJSONArray("tags")
                val tags = buildList {
                    if (jsonTags != null) {
                        for (tagIndex in 0 until jsonTags.length()) {
                            val tag = jsonTags.optString(tagIndex, "").trim()
                            if (tag.isNotEmpty()) {
                                add(tag)
                            }
                        }
                    }
                }.distinct()

                add(
                    ShoreList(
                        id = id,
                        name = name,
                        tags = tags,
                    ),
                )
            }
        }.distinctBy(ShoreList::id)
    }.getOrDefault(emptyList())
}
