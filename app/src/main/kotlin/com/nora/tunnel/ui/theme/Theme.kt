package com.nora.tunnel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NoraColors {
    val Bg = Color(0xFF0A1628)
    val Card = Color(0xFF111F36)
    val Border = Color(0xFF1E2F4A)
    val Mint = Color(0xFF2DD4A7)
    val OnMint = Color(0xFF04201A)
    val TextDim = Color(0xFF8FA3BF)
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
    val Orange = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
}

@Composable
fun NoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NoraColors.Mint,
            onPrimary = NoraColors.OnMint,
            background = NoraColors.Bg,
            surface = NoraColors.Card,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
