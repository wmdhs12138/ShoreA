package com.wmdhs.shorea

internal data class ShoreList(
    val id: Long,
    val name: String,
    val tags: List<String> = emptyList(),
)
