package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.painterResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_apple
import tsiptv.composeapp.generated.resources.ic_google
import tsiptv.composeapp.generated.resources.ic_monitor
import tsiptv.composeapp.generated.resources.ic_sync
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.GradientButtonLight
import tss.t.tsiptv.ui.widgets.SocialButton
import tss.t.tsiptv.ui.widgets.SocialLoginButtonAccentBlue
import tss.t.tsiptv.ui.widgets.SyncDataButtonAccentBlue
import tss.t.tsiptv.utils.customShadow


@OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)
@Composable
fun LoginScreenDesktop2(onEvent: (LoginEvents) -> Unit = {}) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val accentGradient = Brush.horizontalGradient(
        colors = listOf(
            AccentGreen,
            AccentCyan
        ),
        startX = 0f,
        endX = Float.POSITIVE_INFINITY
    )

    LaunchedEffect(email) {
        onEvent(LoginEvents.EmailChanged(email))
    }
    LaunchedEffect(password) {
        onEvent(LoginEvents.PasswordChanged(password))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlue, DarkBlue900))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.85f)
                .customShadow(
                    borderRadius = 10.dp,
                    blurRadius = 20.dp,
                    offsetX = 0.dp,
                    offsetY = 0.dp,
                    color = Color(0xFF00F5FF).copy(alpha = 0.3f)
                )
                .clip(TSShapes.roundedShape16)
                .background(CardBackground, TSShapes.roundedShape16)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Image(
                painter = painterResource(Res.drawable.ic_monitor),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(60.dp)
                    .clip(TSShapes.roundedShape12)
                    .background(
                        brush = accentGradient,
                        shape = TSShapes.roundedShape12
                    )
                    .padding(12.dp),
                colorFilter = ColorFilter.tint(Color.Black)
            )

            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            // Email Field
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text("Email", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TSShapes.roundedShape8,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TextFieldBackground,
                        unfocusedContainerColor = TextFieldBackground,
                        disabledContainerColor = TextFieldBackground,
                        cursorColor = AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = TextStyle(color = TextPrimary)
                )
            }


            // Password Field
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Password",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TSShapes.roundedShape8,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TextFieldBackground,
                        unfocusedContainerColor = TextFieldBackground,
                        disabledContainerColor = TextFieldBackground,
                        cursorColor = AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = TextStyle(color = TextPrimary)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(accentGradient, TSShapes.roundedShape8)
                    .clickable {
                        onEvent(LoginEvents.OnSignInPressed)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Sign In", fontWeight = FontWeight.Bold, color = Color.Black)
            }

            // "Or continue with" Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = TextSecondary.copy(alpha = 0.3f)
                )
                Text(
                    "Or continue with",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = TextSecondary.copy(alpha = 0.3f)
                )
            }

            // Social Logins
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SocialButton(
                    iconRes = Res.drawable.ic_google,
                    contentDescription = "Google",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onEvent(LoginEvents.OnSignInWithGooglePressed)
                    }
                )
                SocialButton(
                    iconRes = Res.drawable.ic_apple,
                    contentDescription = "Apple",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onEvent(LoginEvents.OnSignInWithApplePressed)
                    }
                )
            }

            // Sync Data Button
            TextButton(
                onClick = { /* TODO: Handle sync */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_sync),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AccentGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text("Sync Data", color = AccentGreen)
            }
        }
    }
}

// --- The Main Login Screen Composable ---
@Composable
fun LoginScreenDesktop() {
    // State holders for the input fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // The main container with a radial gradient background to match the design's vignette effect
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1F242A), Color(0xFF101215)),
                    radius = 1200f // Large radius for a subtle effect
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // The central login card
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF1A1D21), shape = TSShapes.roundedShape16)
                .border(1.dp, Color(0xFF2D3238), TSShapes.roundedShape16)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = "StreamVault Logo",
                tint = Color(0xFF4DD0E1),
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Title and Subtitle
            Text("StreamVault", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Your IPTV Experience", fontSize = 14.sp, color = Color.Gray)

            Spacer(Modifier.height(40.dp))

            // Email Input Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = streamVaultTextFieldColors(),
                shape = TSShapes.roundedShape8
            )

            Spacer(Modifier.height(16.dp))

            // Password Input Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = streamVaultTextFieldColors(),
                shape = TSShapes.roundedShape8
            )

            Spacer(Modifier.height(24.dp))

            // Sign In Button
            GradientButtonLight(
                text = "Sign In",
                onClick = { /* TODO: Handle sign in logic */ },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )

            Spacer(Modifier.height(32.dp))

            // "or continue with" divider
            Text("or continue with", color = Color.Gray, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))

            // Social Login TSButtonDefaults
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                SocialLoginButtonAccentBlue(
                    text = "G",
                    onClick = { /* TODO: Google Login */ },
                    modifier = Modifier.weight(1f)
                )
                SocialLoginButtonAccentBlue(
                    text = "",
                    onClick = { /* TODO: Apple Login */ },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Sync Data Button
            SyncDataButtonAccentBlue(
                onClick = { /* TODO: Handle Sync Data */ },
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        }
    }
}

// --- Reusable Component: Custom TextField TSColors ---
@Composable
private fun streamVaultTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        disabledTextColor = Color.Gray,
        cursorColor = Color(0xFF4DD0E1),
        errorCursorColor = Color.Red,
        focusedBorderColor = Color(0xFF4DD0E1),
        unfocusedBorderColor = Color(0xFF2D3238),
        disabledBorderColor = Color.DarkGray,
        errorBorderColor = Color.Red,
        unfocusedLeadingIconColor = Color.Gray,
        disabledLabelColor = Color.Gray,
        focusedLabelColor = Color.Gray,
        unfocusedLabelColor = Color.Gray,
        unfocusedContainerColor = Color(0xFF2D3238)
    )
}
