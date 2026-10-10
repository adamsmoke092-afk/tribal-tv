package com.example.iptvapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.iptvapp.playlist.M3uChannel

/**
 * Channel list — the strict black/gold tile grid (professional redesign).
 * One list, no modes. Under the top bar: horizontally scrolling filter
 * chips (All / Favorites / each group), a live search field, full-span
 * gold group headers with counts, and 12dp-cornered tiles — logo on a
 * light backing square (a gray letter tile when there's no logo), 2-line
 * centered labels, a 40dp favourite star at the tile's top-right, 50%
 * dimming plus an "Offline" label for failed channels, and a 2dp gold
 * border on the playing channel.
 *
 * All list math (search, filters, chip filtering, offline sorting,
 * grouping) lives in ChannelDisplay.kt and stays JVM-tested; this file
 * is pure presentation.
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox is experimental in material3 1.3.0
@Composable
fun ChannelListScreen(
    channels: List<M3uChannel>,
    error: String?,
    isRefreshing: Boolean,
    lastRefreshText: String?,
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
    onOpenSettings: () -> Unit,
    onPlaySequenceChanged: (List<M3uChannel>) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var selectedChip by rememberSaveable { mutableStateOf(CHIP_ALL) }
    val focusManager = LocalFocusManager.current

    // The zap order the player's prev/next buttons move through: exactly
    // what the user sees, deduplicated by URL (a channel in several
    // groups is one entry). Offline skipping happens at zap time.
    LaunchedEffect(channels, searchQuery, selectedChip, hideGeoBlocked, hideNot24x7, hdOnly, favourites) {
        val filtered = applyChipFilter(
            selectedChip,
            applyFilters(channels, hideGeoBlocked, hideNot24x7, hdOnly),
            favourites
        )
        onPlaySequenceChanged(
            filterChannels(searchQuery, filtered).distinctBy { it.streamUrl }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PaletteBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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
                            color = PaletteTextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PaletteTextPrimary,
                        unfocusedTextColor = PaletteTextPrimary,
                        focusedBorderColor = PaletteGold,
                        unfocusedBorderColor = PaletteOutline,
                        cursorColor = PaletteGold,
                        focusedPlaceholderColor = PaletteTextSecondary,
                        unfocusedPlaceholderColor = PaletteTextSecondary,
                        focusedContainerColor = PaletteSurface,
                        unfocusedContainerColor = PaletteSurface
                    )
                )
                IconButton(onClick = {
                    // Close clears the query first, then exits search mode.
                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearching = false
                }) {
                    Icon(
                        imageVector = TribalIcons.Close,
                        contentDescription = if (searchQuery.isNotEmpty()) "Clear search" else "Close search",
                        tint = PaletteTextPrimary
                    )
                }
            } else {
                Text(
                    text = "Tribal TV",
                    style = MaterialTheme.typography.titleLarge,
                    color = PaletteTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isSearching = true }) {
                    Icon(
                        imageVector = TribalIcons.Search,
                        contentDescription = "Search",
                        tint = PaletteTextPrimary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = TribalIcons.Refresh,
                        contentDescription = "Refresh",
                        tint = PaletteTextPrimary
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = TribalIcons.Settings,
                        contentDescription = "Settings",
                        tint = PaletteTextPrimary
                    )
                }
            }
        }
        HorizontalDivider(color = PaletteOutline, thickness = 1.dp)

        if (lastRefreshText != null) {
            Text(
                text = lastRefreshText,
                fontSize = 11.sp,
                color = PaletteTextSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp)
            )
        }

        // ---- Filter chips: All, Favorites, then each group. Real groups
        // named like the synthetic chips are skipped so LazyRow keys
        // stay unique.
        val chipNames = remember(channels) {
            val groups = groupChannels(channels).map { it.first }
                .filterNot { it == CHIP_ALL || it == CHIP_FAVOURITES }
            listOf(CHIP_ALL, CHIP_FAVOURITES) + groups
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = chipNames, key = { it }) { chip ->
                FilterChipView(
                    label = chip,
                    selected = chip == selectedChip,
                    onClick = { selectedChip = chip }
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = PaletteTextPrimary,
                modifier = Modifier.padding(16.dp)
            )
            // Nothing cached and the playlist won't load: the only way
            // forward is another fetch, so offer it directly.
            if (channels.isEmpty()) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(24.dp),
                        color = PaletteGold
                    )
                } else {
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteGold,
                            contentColor = PaletteBackground
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        if (channels.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaletteGold)
            }
        } else {
            val visible = remember(
                channels, searchQuery, selectedChip,
                hideGeoBlocked, hideNot24x7, hdOnly, favourites
            ) {
                filterChannels(
                    searchQuery,
                    applyChipFilter(
                        selectedChip,
                        applyFilters(channels, hideGeoBlocked, hideNot24x7, hdOnly),
                        favourites
                    )
                )
            }

            if (channels.isNotEmpty() && visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No channels match",
                        color = PaletteTextSecondary
                    )
                }
            } else {
                val grouped = remember(visible, favourites, recents, deadChannels, selectedChip) {
                    when (selectedChip) {
                        CHIP_FAVOURITES -> {
                            val pinned = visible
                                .filter { it.streamUrl in favourites }
                                .distinctBy { it.streamUrl }
                            if (pinned.isEmpty()) emptyList()
                            else listOf(CHIP_FAVOURITES to pinned)
                        }
                        else -> {
                            val groups = groupChannels(visible).map { (name, list) ->
                                name to sortOfflineLast(list, deadChannels)
                            }
                            if (selectedChip == CHIP_ALL) {
                                withRecentsGroup(
                                    withFavouritesGroupFirst(groups, favourites),
                                    recents.filterNot { it in deadChannels }
                                )
                            } else {
                                groups
                            }
                        }
                    }
                }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        grouped.forEachIndexed { groupIndex, (group, groupChannels) ->
                            // Index-based keys: a synthetic group name can
                            // collide with a real category, and name keys
                            // would then crash the grid.
                            item(
                                key = "hdr:$groupIndex",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                GroupHeader(
                                    name = group,
                                    count = groupChannels.size,
                                    showDivider = groupIndex > 0
                                )
                            }
                            gridItems(
                                items = groupChannels,
                                key = { channel -> "ch:$groupIndex|${channel.streamUrl}" }
                            ) { channel ->
                                ChannelTile(
                                    channel = channel,
                                    showLogos = showLogos,
                                    isPlaying = channel.streamUrl == playingUrl,
                                    isFavourite = channel.streamUrl in favourites,
                                    isOffline = channel.streamUrl in deadChannels,
                                    onToggleFavourite = { onToggleFavourite(channel) },
                                    onClick = { onChannelSelected(channel) }
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
private fun GroupHeader(name: String, count: Int, showDivider: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = PaletteOutline, thickness = 1.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = PaletteGold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = PaletteTextSecondary
            )
        }
    }
}

/**
 * A channel tile: card background with a hairline outline border (2dp
 * gold when this is the playing channel), the logo on a light backing
 * square — ContentScale.Fit, never Crop, so dark and wide logos stay
 * visible — or a gray letter tile when there's no logo, a 2-line
 * centered label, an "Offline" label with 50% dimming for failed
 * channels, and a 40dp favourite star at the tile's top-right.
 */
@Composable
private fun ChannelTile(
    channel: M3uChannel,
    showLogos: Boolean,
    isPlaying: Boolean,
    isFavourite: Boolean,
    isOffline: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit
) {
    val display = remember(channel.name) { parseDisplay(channel.name) }
    val contentAlpha = if (isOffline) 0.5f else 1f
    val hasLogo = showLogos && !channel.logoUrl.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PaletteCard)
            .border(
                width = if (isPlaying) 2.dp else 1.dp,
                color = if (isPlaying) PaletteGold else PaletteOutline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasLogo) PaletteLogoBacking else PaletteSurface),
                contentAlignment = Alignment.Center
            ) {
                if (hasLogo) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .size(112, 112)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    Text(
                        text = display.cleanName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaletteTextSecondary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = display.cleanName,
                fontSize = 12.sp,
                color = PaletteTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha)
            )
            if (isOffline) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Offline",
                    fontSize = 10.sp,
                    color = PaletteOffline,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(
            onClick = onToggleFavourite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
        ) {
            Icon(
                imageVector = if (isFavourite) TribalIcons.Star else TribalIcons.StarOutlined,
                contentDescription = if (isFavourite) "Remove favourite" else "Add favourite",
                tint = if (isFavourite) PaletteGold else PaletteTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Selected = gold fill with background text; unselected = card fill with outline border. */
@Composable
private fun FilterChipView(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (selected) PaletteBackground else PaletteTextSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) PaletteGold else PaletteCard)
            .border(1.dp, if (selected) PaletteGold else PaletteOutline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
