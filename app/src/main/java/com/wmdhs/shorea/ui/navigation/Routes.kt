package com.wmdhs.shorea.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal data object HomeRoute : NavKey

/** 胶料详情；[highlightEntryId] 为从首页点进来的检测标准，打开后滚动并高亮。 */
@Serializable
internal data class CompoundDetailRoute(
    val compoundId: Long,
    val highlightEntryId: Long? = null,
) : NavKey

/** [compoundId] 为 null 时新建胶料。 */
@Serializable
internal data class CompoundEditorRoute(val compoundId: Long? = null) : NavKey

/** [entryId] 为 null 时为胶料新建检测标准。 */
@Serializable
internal data class InspectionEditorRoute(
    val compoundId: Long,
    val entryId: Long? = null,
) : NavKey
