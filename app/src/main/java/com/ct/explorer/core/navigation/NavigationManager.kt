package com.ct.explorer.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized, decoupled navigation manager.
 * Allows any ViewModel or screen to navigate and pop the back stack safely.
 */
object NavigationManager {
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val backStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            backStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun popBackStack(): Boolean {
        if (backStack.isNotEmpty()) {
            _currentScreen.value = backStack.removeAt(backStack.lastIndex)
            return true
        }
        if (_currentScreen.value != Screen.MAIN) {
            _currentScreen.value = Screen.MAIN
            return true
        }
        return false
    }

    fun resetToMain() {
        backStack.clear()
        _currentScreen.value = Screen.MAIN
    }

    fun getCurrentScreen(): Screen = _currentScreen.value
}
