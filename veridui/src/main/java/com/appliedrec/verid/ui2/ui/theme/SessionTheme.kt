package com.appliedrec.verid.ui2.ui.theme

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color

/**
 * Theme for a Ver-ID session
 *
 * @property textColorLightTheme Text color for light theme
 * @property textColorDarkTheme Text color for dark theme
 * @property backgroundColorLightTheme Background color for light theme
 * @property backgroundColorDarkTheme Background color for dark theme
 * @property accentColorLightTheme Accent color for light theme
 * @property accentColorDarkTheme Accent color for dark theme
 * @since 2.11.0
 */
data class SessionTheme(@ColorInt val textColorLightTheme: Int, @ColorInt val textColorDarkTheme: Int, @ColorInt val backgroundColorLightTheme: Int, @ColorInt val backgroundColorDarkTheme: Int, @ColorInt val accentColorLightTheme: Int, @ColorInt val accentColorDarkTheme: Int) {
    companion object {
        @JvmStatic
        val Default = SessionTheme(
            textColorLightTheme = 0xFF000000.toInt(),
            textColorDarkTheme = 0xFFFFFFFF.toInt(),
            backgroundColorLightTheme = 0xFFFFFFFF.toInt(),
            backgroundColorDarkTheme = 0xFF000000.toInt(),
            accentColorLightTheme = 0xFFFFFFFF.toInt(),
            accentColorDarkTheme = 0xFFFFFFFF.toInt()
        )
    }

    /**
     * Create a Material color scheme from this theme
     *
     * @param dark Whether the color scheme is for a dark theme
     * @return Material color scheme
     * @since 2.11.0
     */
    fun toMaterialColorScheme(dark: Boolean = false): androidx.compose.material3.ColorScheme {
        if (!dark) {
            return androidx.compose.material3.lightColorScheme(
                primary = Color(textColorLightTheme),
                secondary = Color(accentColorLightTheme),
                background = Color(backgroundColorLightTheme)
            )
        } else {
            return androidx.compose.material3.darkColorScheme(
                primary = Color(textColorDarkTheme),
                secondary = Color(accentColorDarkTheme),
                background = Color(backgroundColorDarkTheme)
            )
        }
    }
}