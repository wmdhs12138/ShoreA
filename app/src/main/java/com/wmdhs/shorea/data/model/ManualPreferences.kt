package com.wmdhs.shorea.data.model

import androidx.compose.runtime.Immutable

internal enum class ManualSortOrder(val label: String) {
    COMPOUND_CODE("胶料号"),
    PART_NUMBER("部品号"),
    HARDNESS("硬度"),
}

internal enum class ManualViewMode(val label: String) {
    LIST("列表"),
    COMPACT("紧凑"),
    DETAILED("详细"),
}

/** 首页显示偏好与主题偏好，跨启动保存。 */
@Immutable
internal data class ManualPreferences(
    val sortOrder: ManualSortOrder = ManualSortOrder.COMPOUND_CODE,
    val viewMode: ManualViewMode = ManualViewMode.LIST,
    val dynamicColor: Boolean = true,
)
