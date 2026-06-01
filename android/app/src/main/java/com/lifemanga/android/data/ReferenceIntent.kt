package com.lifemanga.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨屏"待载入参考图"意图。
 *
 * 用法：角色详情 / 历史 / 任何想给创作页"塞图"的屏，可以
 *   `ReferenceIntent.offer(paths)`
 * 然后跳转到 CreateScreen；CreateViewModel.init 会在挂载时把
 * 它消费掉并塞进 pickedImagePaths。
 *
 * 这是"一次性"语义：消费完就清空，避免下次启动还残留。
 */
object ReferenceIntent {
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    fun offer(paths: List<String>) {
        if (paths.isEmpty()) return
        _pending.value = paths
    }

    /** 取出并清空。返回一次性 batch。 */
    fun consume(): List<String> {
        val v = _pending.value
        _pending.value = emptyList()
        return v
    }
}
