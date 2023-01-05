package com.appliedrec.verid.ui2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColourScheme = darkColorScheme(
    primary = Color.White,
    secondary = Color.LightGray,
    background = Color.Black
)

private val LightColourScheme = lightColorScheme(
    primary = Color.Black,
    secondary = Color.DarkGray,
    background = Color.White
)

@Composable
fun VerIDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColourScheme else LightColourScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}