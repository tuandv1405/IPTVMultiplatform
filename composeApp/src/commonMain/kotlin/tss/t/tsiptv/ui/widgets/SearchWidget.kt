package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles

@Composable
@Preview
fun SearchWidget(
    modifier: Modifier = Modifier,
    initText: String = "",
    onValueChange: (String) -> Unit = {},
    placeholder: String = "",
    onLeadingClick: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    var showClearIcon by remember {
        mutableStateOf(false)
    }
    var text by remember(initText) {
        mutableStateOf(initText)
    }

    LaunchedEffect(initText) {
        showClearIcon = initText.isNotEmpty()
    }
    val clearIcon: @Composable (() -> Unit)? = remember(showClearIcon) {
        if (showClearIcon) {
            @Composable {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    "Clear Search",
                    modifier = Modifier.size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear)
                        .padding(12.dp)
                )
            }
        } else null
    }

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        textStyle = TSTextStyles.normal15.copy(
            color = TSColors.TextPrimary,
            lineHeight = 15.sp
        ),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TSColors.TextPrimary,
            unfocusedTextColor = TSColors.TextSecondary,
            errorBorderColor = TSColors.ErrorRed,
            disabledBorderColor = Color(0xFF333333),
            focusedLabelColor = TSColors.TextPrimary,
            disabledContainerColor = TSColors.TextGray,
            focusedContainerColor = TSColors.BackgroundColor,
            unfocusedContainerColor = TSColors.BackgroundColor
        ),
        shape = TSShapes.roundedShape12,
        placeholder = {
            Column {
                Text(
                    text = placeholder,
                    style = TSTextStyles.normal15
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                "Search Home",
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLeadingClick)
                    .padding(12.dp)
            )
        },
        trailingIcon = clearIcon,
        maxLines = 1,
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
            }
        ),
        keyboardOptions = remember {
            KeyboardOptions(imeAction = ImeAction.Search)
        }
    )
}