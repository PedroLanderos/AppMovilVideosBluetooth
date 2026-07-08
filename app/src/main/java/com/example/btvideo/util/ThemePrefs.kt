package com.example.btvideo.util

import android.app.Activity
import android.content.Context
import com.example.btvideo.R

object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_THEME = "theme"
    const val GUINDA = "guinda"
    const val AZUL = "azul"

    fun apply(activity: Activity) {
        when (activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, GUINDA)) {
            AZUL -> activity.setTheme(R.style.Theme_BtVideo_Azul)
            else -> activity.setTheme(R.style.Theme_BtVideo_Guinda)
        }
    }

    fun toggle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = if (prefs.getString(KEY_THEME, GUINDA) == GUINDA) AZUL else GUINDA
        prefs.edit().putString(KEY_THEME, next).apply()
        return next
    }
}
