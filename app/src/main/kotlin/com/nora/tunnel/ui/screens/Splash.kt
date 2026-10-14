package com.nora.tunnel.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.ui.components.NoraLogo
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var target by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(target, tween(1600), label = "splash")
    LaunchedEffect(Unit) {
        target = 1f
        delay(1900)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NoraLogo(96.dp)
        Spacer(Modifier.height(20.dp))
        Text("NORA TUNNEL", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text("Secure. Private. Connected.", color = NoraColors.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(64.dp))
        Text("Initializing...", color = NoraColors.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.width(160.dp),
            color = NoraColors.Mint,
            trackColor = NoraColors.Border,
        )
    }
}
