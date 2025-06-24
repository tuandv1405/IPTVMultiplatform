package tss.t.tsiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.Playlist
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.IPTVParserFactory

/**
 * Generates a random UUID string.
 * This is a simple implementation that doesn't use platform-specific APIs.
 */
private fun randomUUID(): String {
    val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..20)
        .map { allowedChars.random() }
        .joinToString("")
}

@Composable
fun AddIPTVScreen(
    database: IPTVDatabase,
    networkClient: NetworkClient,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add IPTV Playlist",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Playlist Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("IPTV URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    if (url.isBlank()) {
                        errorMessage = "URL cannot be empty"
                        return@Button
                    }

                    if (name.isBlank()) {
                        errorMessage = "Name cannot be empty"
                        return@Button
                    }

                    coroutineScope.launch {
                        try {
                            isLoading = true
                            errorMessage = null

                            // Fetch the IPTV content
                            val content = networkClient.get(url)

                            println("Response: $content")

                            // Detect format and parse content
                            val parser = IPTVParserFactory.createParserForContent(content)
                            val playlist = parser.parse(content)

                            // Save playlist to database
                            val playlistId = randomUUID()
                            database.insertPlaylist(
                                Playlist(
                                    id = playlistId,
                                    name = name,
                                    url = url,
                                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                                )
                            )

                            // Save categories
                            val categories = playlist.groups.map { group ->
                                tss.t.tsiptv.core.database.Category(
                                    id = group.id,
                                    name = group.title,
                                    playlistId = playlistId
                                )
                            }
                            database.insertCategories(categories)

                            // Save channels
                            val channels = playlist.channels.map { channel ->
                                tss.t.tsiptv.core.database.Channel(
                                    id = channel.id,
                                    name = channel.name,
                                    url = channel.url,
                                    logoUrl = channel.logoUrl,
                                    categoryId = channel.groupTitle?.let { title ->
                                        playlist.groups.find { it.title == title }?.id
                                    },
                                    playlistId = playlistId,
                                    isFavorite = false,
                                    lastWatched = null
                                )
                            }
                            database.insertChannels(channels)

                            onSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                        println(errorMessage)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add")
                }
            }
        }
    }
}
