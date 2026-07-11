package org.cf0x.hma.helper

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for managing the total tap count and global app state.
 */
class MainViewModel : ViewModel() {
    private val _totalTapCount = MutableStateFlow(0)
    val totalTapCount: StateFlow<Int> = _totalTapCount.asStateFlow()

    /**
     * Increments the total tap count when any interactive block is clicked.
     */
    fun incrementTapCount() {
        _totalTapCount.value += 1
    }
}
