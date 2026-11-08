package com.example.whatcanicook.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Sage = Color(0xFF4C6B4F)
private val SageLight = Color(0xFFA8C3A4)
private val Clay = Color(0xFFB4654A)
private val Cream = Color(0xFFF7F6F2)
private val Charcoal = Color(0xFF14110F)

private val LightColours = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    secondary = Clay,
    onSecondary = Color.White,
    background = Cream,
    onBackground = Charcoal,
    surface = Color.White,
    onSurface = Charcoal,
)

private val DarkColours = darkColorScheme(
    primary = SageLight,
    onPrimary = Charcoal,
    secondary = Color(0xFFE09078),
    onSecondary = Charcoal,
    background = Charcoal,
    onBackground = Cream,
    surface = Color(0xFF221E1B),
    onSurface = Cream,
)

@Composable
fun WhatCanICookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colours = if (darkTheme) DarkColours else LightColours
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colours.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colours, content = content)
}
