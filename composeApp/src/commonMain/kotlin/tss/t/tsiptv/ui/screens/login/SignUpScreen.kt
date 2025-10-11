package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.back_to_login_account
import tsiptv.composeapp.generated.resources.email
import tsiptv.composeapp.generated.resources.email_invalid
import tsiptv.composeapp.generated.resources.ic_apple
import tsiptv.composeapp.generated.resources.ic_google
import tsiptv.composeapp.generated.resources.ic_monitor
import tsiptv.composeapp.generated.resources.or_continue_with
import tsiptv.composeapp.generated.resources.password
import tsiptv.composeapp.generated.resources.sign_up
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.ErrorDialog
import tss.t.tsiptv.ui.widgets.SocialButton
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.utils.customShadow

@OptIn(
    ExperimentalResourceApi::class,
    InternalResourceApi::class
)
@Composable
fun SignUpScreen(
    authState: AuthUiState,
    onEvent: (LoginEvents) -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordsMatch by remember { mutableStateOf(true) }

    val accentGradient = Brush.horizontalGradient(
        colors = listOf(
            TSColors.AccentGreen,
            TSColors.AccentCyan
        ),
        startX = 0f,
        endX = Float.POSITIVE_INFINITY
    )
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(email) {
        onEvent(LoginEvents.EmailChanged(email))
    }
    LaunchedEffect(password) {
        onEvent(LoginEvents.PasswordChanged(password))
    }
    LaunchedEffect(confirmPassword) {
        passwordsMatch = password == confirmPassword
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    keyboardController?.hide()
                }
            )
            .background(
                Brush.horizontalGradient(
                    colors = TSColors.loginBackgroundGradientColors,
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .customShadow(
                    borderRadius = 10.dp,
                    blurRadius = 20.dp,
                    offsetX = 0.dp,
                    offsetY = 0.dp,
                    color = Color(0xFF00F5FF).copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Image(
                painter = painterResource(Res.drawable.ic_monitor),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = accentGradient,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )

            Text(
                text = stringResource(Res.string.sign_up),
                style = MaterialTheme.typography.titleMedium,
                color = TSColors.TextSecondary
            )

            // Email Field
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(Res.string.email),
                    color = TSColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    isError = !authState.isEmailValid,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TSColors.TextFieldBackground,
                        unfocusedContainerColor = TSColors.TextFieldBackground,
                        disabledContainerColor = TSColors.TextFieldBackground,
                        cursorColor = TSColors.AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    textStyle = TextStyle(color = TSColors.TextPrimary),
                    supportingText = if (!authState.isEmailValid) {
                        @Composable {
                            Text(
                                stringResource(Res.string.email_invalid),
                                color = Color.Red
                            )
                        }
                    } else null
                )
            }

            // Password Field
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.password),
                    color = TSColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TSColors.TextFieldBackground,
                        unfocusedContainerColor = TSColors.TextFieldBackground,
                        disabledContainerColor = TSColors.TextFieldBackground,
                        cursorColor = TSColors.AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = TextStyle(color = TSColors.TextPrimary)
                )
            }

            // Confirm Password Field
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Confirm Password",
                    color = TSColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = !passwordsMatch,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TSColors.TextFieldBackground,
                        unfocusedContainerColor = TSColors.TextFieldBackground,
                        disabledContainerColor = TSColors.TextFieldBackground,
                        cursorColor = TSColors.AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    textStyle = TextStyle(color = TSColors.TextPrimary),
                    supportingText = if (!passwordsMatch) {
                        @Composable {
                            Text(
                                "Passwords do not match",
                                color = Color.Red
                            )
                        }
                    } else null
                )
            }

            // Sign Up Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(
                        enabled = authState.isEmailValid && passwordsMatch && password.isNotEmpty() && confirmPassword.isNotEmpty(),
                        onClick = {
                            onEvent(LoginEvents.OnSignUpPressed)
                        }
                    )
                    .background(
                        brush = accentGradient,
                        shape = RoundedCornerShape(8.dp),
                        alpha = if (authState.isEmailValid && passwordsMatch && password.isNotEmpty() && confirmPassword.isNotEmpty()) 1f else 0.5f
                    )
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.sign_up),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Back to Login Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(onClick = {
                        onEvent(LoginEvents.ShowLoginScreen)
                    })
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.back_to_login_account),
                    style = TSTextStyles.secondaryBody.copy(
                        color = TSColors.TextSecondary,
                        textDecoration = TextDecoration.Underline,
                        lineBreak = LineBreak.Simple,
                        lineHeight = 16.sp,
                        baselineShift = BaselineShift.Superscript
                    )
                )
            }

//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                HorizontalDivider(
//                    modifier = Modifier.weight(1f),
//                    color = TSColors.TextSecondary.copy(alpha = 0.3f)
//                )
//                Text(
//                    stringResource(Res.string.or_continue_with),
//                    color = TSColors.TextSecondary,
//                    fontSize = 12.sp,
//                    textAlign = TextAlign.Center
//                )
//                HorizontalDivider(
//                    modifier = Modifier.weight(1f),
//                    color = TSColors.TextSecondary.copy(alpha = 0.3f)
//                )
//            }

            // Social Logins
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                SocialButton(
//                    iconRes = Res.drawable.ic_google,
//                    contentDescription = "Google",
//                    modifier = Modifier.weight(1f),
//                    onClick = { onEvent(LoginEvents.OnSignInWithGooglePressed) }
//                )
//                if (remember { PlatformUtils.platform.isIOS }) {
//                    SocialButton(
//                        iconRes = Res.drawable.ic_apple,
//                        contentDescription = "Apple",
//                        modifier = Modifier.weight(1f),
//                        onClick = { onEvent(LoginEvents.OnSignInWithApplePressed) }
//                    )
//                }
//            }
        }
    }

    // Error Dialog for Registration Failures
    if (!authState.error.isNullOrEmpty() && !authState.isAuthenticated) {
        ErrorDialog(
            errorMessage = authState.error,
            onDismiss = {
                onEvent(LoginEvents.OnDismissErrorDialog)
            }
        )
    }
}
