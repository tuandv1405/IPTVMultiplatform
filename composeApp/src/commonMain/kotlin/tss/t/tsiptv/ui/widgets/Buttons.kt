package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

class Buttons {
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
