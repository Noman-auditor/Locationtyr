package com.nora.tunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.ui.components.PrimaryButton
import com.nora.tunnel.ui.theme.NoraColors

private data class Feature(val icon: ImageVector, val color: Color, val title: String, val sub: String)

private val features = listOf(
    Feature(Icons.Filled.Lock, NoraColors.Mint, "Secure Connection", "Encrypt your data"),
    Feature(Icons.Filled.VisibilityOff, NoraColors.Blue, "No Logs", "Your activity stays private"),
    Feature(Icons.Filled.Public, NoraColors.Purple, "Global Access", "Freedom to the internet"),
)

private val headlines = listOf("Your Privacy, Our Priority", "Built for Speed", "Freedom Everywhere")

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { headlines.size }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(headlines[page], color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                Icon(Icons.Filled.Shield, null, tint = NoraColors.Mint, modifier = Modifier.size(150.dp))
                Spacer(Modifier.height(32.dp))
                features.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(f.color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(f.icon, null, tint = f.color) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(f.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(f.sub, color = NoraColors.TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(headlines.size) { i ->
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) NoraColors.Mint else NoraColors.Border)
                )
            }
        }
        PrimaryButton("Get Started  →", onClick = onFinish)
    }
}
