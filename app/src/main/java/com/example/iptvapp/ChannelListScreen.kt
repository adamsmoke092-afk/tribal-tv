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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.iptvapp.playlist.M3uChannel

/**
 * Dark cinema channel list. Custom top bar instead of TopAppBar to keep
 * full control of colors; flattened LazyColumn with plain header items
 * (sticky headers skipped — needs an experimental opt-in and adds nothing
 * the plain row doesn't on this device).
 *
 * Feature batch: a search icon swaps the title for an inline search field;
 * filtering is live, group counts follow the filtered results, and empty
 * groups drop out. Query and search-mode survive rotation
 * (rememberSaveable + configChanges). Batch B: the list is wrapped in
 * material3's PullToRefreshBox — drag down on the list to refresh.
 * Batch D: the row the session is currently playing gets a chip.
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox is experimental in material3 1.3.0
@Composable
fun ChannelListScreen(
    channels: List<M3uChannel>,
    error: String?,
    isRefreshing: Boolean,
    playingUrl: String?,
    showLogos: Boolean,
    hideGeoBlocked: Boolean,
    hideNot24x7: Boolean,
    hdOnly: Boolean,
    favourites: Set<String>,
    recents: List<String>,
    deadChannels: Set<String>,
    onToggleFavourite: (M3uChannel) -> Unit,
    onChannelSelected: (M3uChannel) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Search channels",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                IconButton(onClick = {
                    // Close clears the query first, then exits search mode.
                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearching = false
                }) {
                    Icon(
                        imageVector = TribalIcons.Close,
                        contentDescription = if (searchQuery.isNotEmpty()) "Clear search" else "Close search",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Text(
                    text = "Channels",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isSearching = true }) {
                    Icon(
                        imageVector = TribalIcons.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = TribalIcons.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = TribalIcons.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
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
            val visible = remember(channels, searchQuery, hideGeoBlocked, hideNot24x7, hdOnly) {
                filterChannels(
                    searchQuery,
                    applyFilters(channels, hideGeoBlocked, hideNot24x7, hdOnly)
                )
            }

            if (channels.isNotEmpty() && visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "No channels match your filters"
                        else "No channels match",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val grouped = remember(visible, favourites, recents) {
                    withRecentsGroup(
                        withFavouritesGroupFirst(groupChannels(visible), favourites),
                        recents
                    )
                }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn {
                        grouped.forEachIndexed { groupIndex, (group, groupChannels) ->
                            // Index-based keys: the synthetic Favourites group could
                            // share a name with a real category, and name-based keys
                            // would then collide and crash the list.
                            item(key = "hdr:$groupIndex") {
                                GroupHeader(name = group, count = groupChannels.size)
                            }
                            items(
                                items = groupChannels,
                                key = { channel -> "ch:$groupIndex|${channel.streamUrl}" }
                            ) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    showLogos = showLogos,
                                    isFavourite = channel.streamUrl in favourites,
                                    isDead = channel.streamUrl in deadChannels,
                                    isPlaying = channel.streamUrl == playingUrl,
                                    onToggleFavourite = { onToggleFavourite(channel) },
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
private fun ChannelRow(
    channel: M3uChannel,
    showLogos: Boolean,
    isFavourite: Boolean,
    isDead: Boolean,
    isPlaying: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit
) {
    val display = remember(channel.name) { parseDisplay(channel.name) }
    // A channel that failed before is dimmed, so retapping a dead link is
    // a deliberate choice, not a surprise. Still fully playable.
    val deadAlpha = if (isDead) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.alpha(deadAlpha)) {
            ChannelAvatar(
                logoUrl = channel.logoUrl,
                showLogos = showLogos,
                fallbackName = display.cleanName
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(deadAlpha)) {
            Text(
                text = display.cleanName,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isPlaying || display.quality != null || display.tags.isNotEmpty() || isDead) {
                Spacer(Modifier.height(6.dp))
                // FlowRow so several tags wrap on narrow phones instead of clipping.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPlaying) Chip(text = "Playing", accent = true)
                    display.quality?.let { Chip(text = it, accent = false) }
                    display.tags.forEach { Chip(text = it, accent = true) }
                    if (isDead) Chip(text = "May be dead", accent = false)
                }
            }
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = TribalIcons.Star,
                contentDescription = if (isFavourite) "Remove favourite" else "Add favourite",
                tint = if (isFavourite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
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

/**
 * 40dp slot: the letter avatar is always the base layer, so it shows while
 * a logo loads, and remains if the load fails. The logo is drawn on top
 * only when logos are enabled and the channel has one. Sizing the request
 * to 80dp keeps the bitmap crisp on 2x screens and tiny on the wire.
 */
@Composable
private fun ChannelAvatar(logoUrl: String?, showLogos: Boolean, fallbackName: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        LetterAvatar(fallbackName)
        if (showLogos && !logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .size(80, 80)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
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
