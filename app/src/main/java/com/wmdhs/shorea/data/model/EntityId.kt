package com.wmdhs.shorea.data.model

/** 新实体 ID：取当前时间戳与 (最大已有 ID + 1) 中较大者，保证单调递增。 */
internal fun nextEntityId(
    existingIds: Iterable<Long>,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val maxExisting = existingIds.maxOrNull() ?: 0L
    val nextSequential = if (maxExisting == Long.MAX_VALUE) {
        error("内部编号已达到上限")
    } else {
        maxExisting + 1L
    }

    return maxOf(
        nowMillis.coerceAtLeast(1L),
        nextSequential,
    )
}

/** 批量分配互不冲突的 ID（导入、合并、迁移时使用）。 */
internal class EntityIdAllocator(existingIds: Iterable<Long> = emptyList()) {
    private val usedIds = existingIds.toMutableSet()
    private var nextCandidate = nextEntityId(usedIds)

    /** 优先保留 [preferredId]；已被占用或非法时分配新 ID。 */
    fun allocate(preferredId: Long? = null): Long {
        if (preferredId != null && preferredId >= 0L && usedIds.add(preferredId)) {
            return preferredId
        }
        while (!usedIds.add(nextCandidate)) {
            nextCandidate = nextEntityId(usedIds)
        }
        val allocated = nextCandidate
        nextCandidate = nextEntityId(usedIds)
        return allocated
    }
}
