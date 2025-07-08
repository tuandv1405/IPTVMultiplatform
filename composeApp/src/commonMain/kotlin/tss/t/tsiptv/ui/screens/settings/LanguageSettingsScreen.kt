package tss.t.tsiptv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.language_settings
import tss.t.tsiptv.core.language.LocaleManager
import tss.t.tsiptv.core.language.SupportedLanguage
import tss.t.tsiptv.ui.screens.login.AccentCyan
import tss.t.tsiptv.ui.screens.login.AccentGreen
import tss.t.tsiptv.ui.screens.login.CardBackground
import tss.t.tsiptv.ui.screens.login.DarkBlue900
import tss.t.tsiptv.ui.screens.login.DeepBlue
import tss.t.tsiptv.ui.screens.login.TextPrimary
import tss.t.tsiptv.ui.screens.login.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun LanguageSettingsScreen(
    onBackPressed: () -> Unit,
    localeManager: LocaleManager = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf<SupportedLanguage?>(null) }
    
    // Get the current language from the locale manager
    LaunchedEffect(Unit) {
        selectedLanguage = localeManager.getCurrentLanguage()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.language_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0A0A0A),
                            DeepBlue,
                            DarkBlue900
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                LazyColumn {
                    items(SupportedLanguage.values()) { language ->
                        LanguageItem(
                            language = language,
                            isSelected = language == selectedLanguage,
                            onClick = {
                                selectedLanguage = language
                                coroutineScope.launch {
                                    localeManager.setLanguage(language)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageItem(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = AccentCyan
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}