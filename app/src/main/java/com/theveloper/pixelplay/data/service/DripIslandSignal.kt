package com.theveloper.pixelplay.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells Drip's island to step aside while a panel of ours is open. Drip only looks at what is on screen for a seek
 * bar, and a panel without one would bring the island back on top of it. It reads this from the session's extras.
 */
@Singleton
class DripIslandSignal @Inject constructor() {
    private val _hidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun setHidden(hidden: Boolean) {
        _hidden.value = hidden
    }
}
