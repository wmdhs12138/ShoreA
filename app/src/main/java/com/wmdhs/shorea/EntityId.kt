package com.wmdhs.shorea

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
