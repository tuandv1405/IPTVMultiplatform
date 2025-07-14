package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_logout
import tss.t.tsiptv.ui.screens.addiptv.GlowBlue
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.customShadow

object TSButtonDefaults {

    val positiveGradient by lazy {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF22C55E),
                TSColors.GradientBlue
            )
        )
    }
    val defaultShadow = Color(0xFF22C55E).copy(alpha = 0.3f)
    val defaultGradientButton2 = TSColors.baseGradient
    val defaultShadowGradientButton2 = Color(0xFF3B82F6).copy(alpha = 0.3f)

    val mainGlowButtonColor = Color(0xFF2563EB)
    val shadowGlowButtonColor = TSColors.GradientBlue

    val grayContainerColor = Color(0xFFE5E7EB)
    val grayContainerShadowColor = grayContainerColor.copy(alpha = 0.1f)

    val negativeGradient by lazy {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFEF4444),
                Color(0xFFDC2626)
            )
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
) {
    val gradient = Brush.horizontalGradient(
        listOf(
            TSColors.GradientBlue,
            TSColors.GradientGreen
        )
    )
    Box(
        modifier = modifier
            .background(gradient, RoundedCornerShape(12.dp))
            .padding(paddingValues)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GradientButtonLight(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.horizontalGradient(listOf(Color(0xFF00BFA5), Color(0xFF4DD0E1))),
) {
    Box(
        modifier = modifier
            .background(brush = gradient, shape = TSShapes.roundedShape8)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GradientButton1(
    text: String,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    CommonButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        gradient = TSButtonDefaults.positiveGradient,
        shadowColor = TSButtonDefaults.defaultShadow,
        clickable = clickable,
        icon = icon
    )
}

@Composable
fun GradientButton2(
    text: String,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    CommonButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        gradient = TSButtonDefaults.defaultGradientButton2,
        shadowColor = TSButtonDefaults.defaultShadowGradientButton2,
        clickable = clickable,
        icon = icon
    )
}

@Composable
fun GlowingButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    mainColor: Color = TSButtonDefaults.mainGlowButtonColor,
    glowColor: Color = TSButtonDefaults.shadowGlowButtonColor,
) {
    CommonButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        gradient = Brush.horizontalGradient(listOf(mainColor, glowColor)),
        shadowColor = glowColor.copy(alpha = 0.5f)
    )
}

@Composable
fun CommonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.horizontalGradient(
        listOf(
            Color(0xFF00BFA5),
            Color(0xFF4DD0E1)
        )
    ),
    shadowColor: Color = Color(0xFF22C55E)
        .copy(alpha = 0.3f),
    clickable: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .customShadow(
                color = shadowColor,
                blurRadius = 10.dp,
                offsetY = 2.dp,
                borderRadius = 12.dp
            )
            .clip(TSShapes.roundedShape8)
            .clickable(
                enabled = clickable,
                onClick = onClick
            )
            .background(brush = gradient, shape = TSShapes.roundedShape8)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                it()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ColoredButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF2563EB),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .customShadow(
                color = color.copy(0.1f),
                blurRadius = 10.dp,
                offsetY = 2.dp,
                borderRadius = 12.dp
            )
            .clip(TSShapes.roundedShape8)
            .clickable(onClick = onClick)
            .background(color = color, shape = TSShapes.roundedShape8)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SocialLoginButtonAccentBlue(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = TSShapes.roundedShape8,
        colors = ButtonDefaults.buttonColors(contentColor = Color(0xFF2D3238))
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SocialLoginButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TSColors.ButtonBackground)
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = when (text) { // Simple color logic for demo
                "G" -> Color(0xFFDB4437)
                else -> Color.White
            }
        )
    }
}

@Composable
fun SyncDataButtonAccentBlue(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = TSShapes.roundedShape8,
        colors = ButtonDefaults.buttonColors(contentColor = Color(0xFF2D3238))
    ) {
        Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = "Sync Data",
            tint = Color(0xFF4DD0E1),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Sync Data", color = Color(0xFF4DD0E1))
    }
}


@Composable
fun SyncDataButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TSColors.ButtonBackground)
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload, // Icon matches the new image
            contentDescription = "Sync Data",
            tint = TSColors.GradientGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Sync Data",
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

@Composable
fun PositiveButton(
    text: String,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    icon: (@Composable (() -> Unit))? = null,
    onClick: () -> Unit,
) {
    GradientButton1(
        text = text,
        modifier = modifier,
        onClick = onClick,
        clickable = clickable,
        icon = icon,
    )
}

@Composable
fun NegativeButton(
    text: String,
    icon: DrawableResource? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TSShapes.roundedShape12,
) {
    Box(
        modifier = modifier
            .background(
                brush = TSButtonDefaults.negativeGradient,
                shape = shape
            )
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Image(
                    painter = painterResource(it),
                    contentDescription = "Download",
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun GrayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .customShadow(
                color = TSButtonDefaults.grayContainerShadowColor,
                blurRadius = 10.dp,
                offsetY = 2.dp,
                borderRadius = 12.dp
            )
            .clip(TSShapes.roundedShape8)
            .clickable(onClick = onClick)
            .background(color = TSButtonDefaults.grayContainerColor, shape = TSShapes.roundedShape8)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TSColors.TextTitlePrimaryDart,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}
