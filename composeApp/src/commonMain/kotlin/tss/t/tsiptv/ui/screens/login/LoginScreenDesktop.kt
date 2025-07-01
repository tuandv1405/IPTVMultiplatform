package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import tss.t.tsiptv.ui.widgets.GradientButton
import tss.t.tsiptv.ui.widgets.GradientButtonLight
import tss.t.tsiptv.ui.widgets.SocialLoginButtonAccentBlue
import tss.t.tsiptv.ui.widgets.SyncDataButtonAccentBlue

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
                .background(Color(0xFF1A1D21), shape = RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2D3238), RoundedCornerShape(16.dp))
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
                shape = RoundedCornerShape(8.dp)
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
                shape = RoundedCornerShape(8.dp)
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

            // Social Login Buttons
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