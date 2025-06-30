package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.Platform
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.widgets.AppLogo
import tss.t.tsiptv.ui.widgets.BackgroundBox

/**
 * Login screen for the application.
 * Design based on Figma prototype.
 */
@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Root container with the dark background
    BackgroundBox(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // Use fillMaxSize for portrait orientation
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            AppLogo()

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Your IPTV Experience",
                color = TSColors.TextGray,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(48.dp))

            // --- Login Form Card ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = TSColors.CardBackground,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = TSColors.CardBorderBlue,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    keyboardType = KeyboardType.Email
                )

                Spacer(Modifier.height(16.dp))

                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true
                )

                Spacer(Modifier.height(24.dp))

                // Sign In Button
                GradientButton(
                    text = "Sign In",
                    onClick = { /* TODO: Sign in logic */ },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                Spacer(Modifier.height(24.dp))

                // "or continue with"
                Text(
                    text = "or continue with",
                    color = TSColors.TextGray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(16.dp))

                // Social Logins
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SocialLoginButton(
                        text = "G",
                        onClick = { /* TODO: Google login */ },
                        modifier = Modifier.weight(1f)
                    )
                    SocialLoginButton(
                        text = "",
                        onClick = { /* TODO: Apple login */ },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Sync Data Button
                SyncDataButton(
                    onClick = { /* TODO: Sync data logic */ },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )
            }
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = TSColors.TextGray) },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = TSColors.ButtonBackground,
            unfocusedContainerColor = TSColors.ButtonBackground,
            cursorColor = TSColors.GradientGreen,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = TSColors.TextGray,
            unfocusedLabelColor = TSColors.TextGray
        )
    )
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gradient = Brush.horizontalGradient(
        listOf(
            TSColors.GradientBlue,
            TSColors.GradientGreen
        )
    )
    Box(
        modifier = modifier
            .background(gradient, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
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
