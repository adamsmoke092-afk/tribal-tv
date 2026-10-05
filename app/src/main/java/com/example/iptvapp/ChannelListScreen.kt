package com.example.iptvapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvapp.playlist.M3uChannel

/**
 * Dark cinema channel list (UI redesign step 2). Custom top bar instead of
 * TopAppBar to keep full control of colors; flattened LazyColumn with plain
 * header items (sticky headers skipped — needs an experimental opt-in and
 * adds nothing the plain row doesn't on this device).
 *
 * Same contract as before: loading spinner on first load, inline error on
 * failed fetch, refresh button calls IptvApp's onRefresh.
 */
@Composable
fun ChannelListScreen(
    channels: List<M3uChannel>,
    error: String?,
    onChannelSelected: (M3uChannel) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Channels",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (channels.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val grouped = remember(channels) { groupChannels(channels) }
            LazyColumn {
                grouped.forEach { (group, groupChannels) ->
                    // Prefixed keys so a header can never collide with a channel key.
                    item(key = "hdr:$group") {
                        GroupHeader(name = group, count = groupChannels.size)
                    }
                    items(
                        items = groupChannels,
                        key = { channel -> "ch:$group|${channel.streamUrl}" }
                    ) { channel ->
                        ChannelRow(
                            channel = channel,
                            onClick = { onChannelSelected(channel) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow — stable or experimental depending on foundation version; opt-in covers both
@Composable
private fun ChannelRow(channel: M3uChannel, onClick: () -> Unit) {
    val display = remember(channel.name) { parseDisplay(channel.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(display.cleanName)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = display.cleanName,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (display.quality != null || display.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                // FlowRow so several tags wrap on narrow phones instead of clipping.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    display.quality?.let { Chip(text = it, accent = false) }
                    display.tags.forEach { Chip(text = it, accent = true) }
                }
            }
        }
    }
}

@Composable
private fun LetterAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TribalAccentTint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TribalAccentTintText
        )
    }
}

@Composable
private fun Chip(text: String, accent: Boolean) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = if (accent) TribalAccentTintText else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) TribalAccentTint else TribalChipBackground)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
