package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.widgets.AppLogo
import tss.t.tsiptv.ui.widgets.AppTextField
import tss.t.tsiptv.ui.widgets.BackgroundBox
import tss.t.tsiptv.ui.widgets.GradientButton
import tss.t.tsiptv.ui.widgets.SocialLoginButton
import tss.t.tsiptv.ui.widgets.SyncDataButton
import tss.t.tsiptv.ui.widgets.SyncDataButtonAccentBlue

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