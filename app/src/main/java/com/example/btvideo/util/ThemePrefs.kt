package com.example.btvideo.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.example.btvideo.R

object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val KEY_MODE = "mode"

    const val GUINDA = "guinda"
    const val AZUL = "azul"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    fun apply(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val colorTheme = prefs.getString(KEY_COLOR_THEME, GUINDA) ?: GUINDA
        val mode = prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
        val isDark = when (mode) {
            MODE_LIGHT -> false
            MODE_DARK -> true
            else -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        val style = when {
            colorTheme == AZUL && isDark -> R.style.Theme_BtVideo_Azul_Dark
            colorTheme == AZUL && !isDark -> R.style.Theme_BtVideo_Azul_Light
            colorTheme == GUINDA && isDark -> R.style.Theme_BtVideo_Guinda_Dark
            else -> R.style.Theme_BtVideo_Guinda_Light
        }
        activity.setTheme(style)
    }

    fun toggleColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_COLOR_THEME, GUINDA) ?: GUINDA
        val next = if (current == GUINDA) AZUL else GUINDA
        prefs.edit().putString(KEY_COLOR_THEME, next).apply()
        return next
    }

    fun toggleMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
        val next = when (current) {
            MODE_SYSTEM -> MODE_LIGHT
            MODE_LIGHT -> MODE_DARK
            else -> MODE_SYSTEM
        }
        prefs.edit().putString(KEY_MODE, next).apply()
        return next
    }

    fun colorLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_COLOR_THEME, GUINDA) ?: GUINDA) {
            AZUL -> "Azul ESCOM"
            else -> "Guinda IPN"
        }
    }

    fun modeLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM) {
            MODE_LIGHT -> "Claro"
            MODE_DARK -> "Oscuro"
            else -> "Sistema"
        }
    }
}
