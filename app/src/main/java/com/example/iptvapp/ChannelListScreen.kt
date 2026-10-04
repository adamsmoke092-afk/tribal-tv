package com.example.iptvapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.iptvapp.playlist.M3uChannel

/**
 * Channel list grouped by group-title (SPEC §2.4). Shows a loading spinner
 * on first load, an inline error message if the fetch fails, and a manual
 * refresh action (calls PlaylistRepository.refreshChannels via IptvApp.kt)
 * since there's no automatic dead-link probing yet (§4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    channels: List<M3uChannel>,
    error: String?,
    onChannelSelected: (M3uChannel) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Channels") },
            actions = {
                IconButton(onClick = onRefresh) {
                    Text("⟳")
                }
            }
        )

        if (error != null) {
            Text(text = error, modifier = Modifier.padding(16.dp))
        }

        if (channels.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val grouped = channels.groupBy { it.groupTitle ?: "Ungrouped" }.toList()
            LazyColumn {
                items(grouped) { (group, groupChannels) ->
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    groupChannels.forEach { channel ->
                        ListItem(
                            headlineContent = { Text(channel.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChannelSelected(channel) }
                        )
                    }
                }
            }
        }
    }
}
