package tss.t.tsiptv.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.email
import tsiptv.composeapp.generated.resources.forgot_password_message
import tsiptv.composeapp.generated.resources.forgot_password_send
import tsiptv.composeapp.generated.resources.forgot_password_sending
import tsiptv.composeapp.generated.resources.forgot_password_sent_message
import tsiptv.composeapp.generated.resources.forgot_password_sent_title
import tsiptv.composeapp.generated.resources.forgot_password_title
import tsiptv.composeapp.generated.resources.ok
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.GradientButton2
import tss.t.tsiptv.ui.widgets.GrayButton

/**
 * Dialog for requesting a password reset link.
 *
 * Has two states: a form for entering the address, and a confirmation shown once
 * the request has gone through. The confirmation is deliberately worded as
 * "if an account exists" — the backend reports an unknown address as a success
 * so that this dialog cannot be used to find out who has an account.
 *
 * Hosted by [LoginScreenPhone], which serves both Android and iOS.
 *
 * @param email The address currently typed into the dialog
 * @param isLoading Whether the request is in flight
 * @param isEmailSent Whether to show the confirmation state
 * @param error The error to show under the field, or null
 * @param onEmailChange Called as the user types
 * @param onSend Called when the user submits
 * @param onDismiss Called when the dialog should close
 */
@Composable
fun ForgotPasswordDialog(
    email: String,
    isLoading: Boolean,
    isEmailSent: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Column(
            modifier = Modifier
                .clip(TSShapes.roundedShape20)
                .background(Color.White, TSShapes.roundedShape20)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEmailSent) {
                    stringResource(Res.string.forgot_password_sent_title)
                } else {
                    stringResource(Res.string.forgot_password_title)
                },
                color = TSColors.TextTitlePrimaryDart,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isEmailSent) {
                    stringResource(Res.string.forgot_password_sent_message, email.trim())
                } else {
                    stringResource(Res.string.forgot_password_message)
                },
                color = TSColors.TextBodyPrimaryDart,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            if (!isEmailSent) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = TSColors.SecondaryBackgroundColor.copy(alpha = 0.3f),
                        disabledLabelColor = TSColors.SecondaryBackgroundColor.copy(alpha = 0.3f)
                    ),
                    label = {
                        Text(
                            stringResource(Res.string.email),
                            style = TSTextStyles.semiBold13
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (!isLoading) onSend() }
                    ),
                    textStyle = TSTextStyles.normal15.copy(color = TSColors.DeepBlue)
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TSColors.DeepBlue,
                        strokeWidth = 2.5.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isEmailSent) {
                GradientButton2(
                    text = stringResource(Res.string.ok),
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GrayButton(
                        text = stringResource(Res.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                    )
                    GradientButton2(
                        text = if (isLoading) {
                            stringResource(Res.string.forgot_password_sending)
                        } else {
                            stringResource(Res.string.forgot_password_send)
                        },
                        onClick = if (isLoading) {
                            {}
                        } else {
                            onSend
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                    )
                }
            }
        }
    }
}
