package com.nora.tunnel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nora.tunnel.ui.theme.NoraColors

@Composable
fun NoraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(NoraColors.Card)
            .border(1.dp, NoraColors.Border, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NoraColors.Mint, contentColor = NoraColors.OnMint,
        ),
    ) { Text(text) }
}

@Composable
fun NoraLogo(logoSize: Dp) {
    Canvas(Modifier.size(logoSize)) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * 0.12f, h * 0.85f); lineTo(w * 0.5f, h * 0.15f); lineTo(w * 0.88f, h * 0.85f)
        }
        val inner = Path().apply {
            moveTo(w * 0.34f, h * 0.85f); lineTo(w * 0.5f, h * 0.55f); lineTo(w * 0.66f, h * 0.85f)
        }
        drawPath(outer, NoraColors.Mint, style = Stroke(w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(inner, NoraColors.Mint, style = Stroke(w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
