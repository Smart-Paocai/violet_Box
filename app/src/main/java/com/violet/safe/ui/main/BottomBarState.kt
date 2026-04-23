package com.violet.safe.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BottomBarState {
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun getSelectedTab(): Int = _selectedTab.value
}

