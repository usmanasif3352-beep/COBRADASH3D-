package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.SnakeSkin
import com.example.monetization.AdManager
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.BorderEmerald
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.SolarGold
import com.example.ui.theme.VoidObsidian
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun CobraBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A221A), ArenaSlate, VoidObsidian),
                    radius = 1200f
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 56.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color(0x1400E676),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color(0x1400E676),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        content()
    }
}

@Composable
fun CobraTopBar(
    title: String,
    level: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_bar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = EmeraldCore
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            color = EmeraldDeep.copy(alpha = 0.7f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, EmeraldCore.copy(alpha = 0.6f))
        ) {
            Text(
                text = "LVL $level",
                style = MaterialTheme.typography.labelLarge,
                color = SolarGold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun CobraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "primary_action_button",
    enabled: Boolean = true,
    containerColor: Color = EmeraldCore,
    contentColor: Color = VoidObsidian
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = CardSurface,
            disabledContentColor = Color.Gray
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SkinPreviewCanvas(
    skin: SnakeSkin,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val primary = Color(skin.primaryColor)
    val secondary = Color(skin.secondaryColor)
    val glow = Color(skin.glowColor)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cy = h * 0.56f

        // Glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * 0.5f, cy),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.5f, cy)
        )

        // Coiled 3D-shaded segments
        for (i in 4 downTo 1) {
            val segX = w * (0.5f - i * 0.11f)
            val segY = cy + (if (i % 2 == 0) h * 0.08f else -h * 0.05f)
            val r = w * (0.19f - i * 0.02f)
            val c = if (i % 2 == 0) secondary else primary
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), c, Color.Black.copy(alpha = 0.5f)),
                    center = Offset(segX - r * 0.28f, segY - r * 0.28f),
                    radius = r * 1.3f
                ),
                radius = r,
                center = Offset(segX, segY)
            )
        }

        // Cobra head
        val headCenter = Offset(w * 0.62f, cy)
        val headR = w * 0.24f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.5f), primary, Color.Black.copy(alpha = 0.4f)),
                center = Offset(headCenter.x - headR * 0.25f, headCenter.y - headR * 0.3f),
                radius = headR * 1.4f
            ),
            radius = headR,
            center = headCenter
        )
        drawCircle(
            color = glow.copy(alpha = 0.7f),
            radius = headR,
            center = headCenter,
            style = Stroke(width = 2.dp.toPx())
        )
        // Eyes
        drawCircle(
            color = Color(0xFFFFF59D),
            radius = headR * 0.20f,
            center = Offset(headCenter.x + headR * 0.32f, headCenter.y - headR * 0.22f)
        )
        drawCircle(
            color = Color(0xFFFFF59D),
            radius = headR * 0.20f,
            center = Offset(headCenter.x + headR * 0.32f, headCenter.y + headR * 0.22f)
        )

        // Optional royal crown
        if (skin.hasCrown) {
            val crownPath = Path().apply {
                val baseY = headCenter.y - headR * 0.92f
                moveTo(headCenter.x - headR * 0.55f, baseY)
                lineTo(headCenter.x - headR * 0.45f, baseY - headR * 0.55f)
                lineTo(headCenter.x - headR * 0.15f, baseY - headR * 0.22f)
                lineTo(headCenter.x, baseY - headR * 0.65f)
                lineTo(headCenter.x + headR * 0.15f, baseY - headR * 0.22f)
                lineTo(headCenter.x + headR * 0.45f, baseY - headR * 0.55f)
                lineTo(headCenter.x + headR * 0.55f, baseY)
                close()
            }
            drawPath(crownPath, color = SolarGold)
        }
    }
}

@Composable
fun AdBannerFooter(
    adsRemoved: Boolean,
    modifier: Modifier = Modifier
) {
    if (adsRemoved) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardSurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, BorderEmerald.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdManager.TEST_BANNER_AD_UNIT_ID
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (_: Exception) {
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}
