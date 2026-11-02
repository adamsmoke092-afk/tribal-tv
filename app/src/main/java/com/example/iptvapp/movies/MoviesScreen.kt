package com.example.iptvapp.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.iptvapp.PaletteBackground
import com.example.iptvapp.PaletteCard
import com.example.iptvapp.PaletteDisabled
import com.example.iptvapp.PaletteGold
import com.example.iptvapp.PaletteOffline
import com.example.iptvapp.PaletteOutline
import com.example.iptvapp.PaletteSurface
import com.example.iptvapp.PaletteTextPrimary
import com.example.iptvapp.PaletteTextSecondary
import com.example.iptvapp.TribalIcons
import java.util.Locale

/**
 * Movies screen: trending movies and TV shows from TMDB, with poster
 * thumbnails. Tapping a movie plays it; tapping a TV show opens a
 * season/episode picker before playing.
 *
 * The selected category and all list/picker state live in
 * [MoviesViewModel] — this screen holds no duplicate state.
 */
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onPlay: (PlayRequest) -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.uiState

    // Single source of truth: the ViewModel owns the selected category.
    val isMoviesSelected = state.selectedCategory == MoviesCategory.MOVIES

    // Load the first category once when the screen first appears.
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    // Forward a one-shot play request to the host, then clear it.
    LaunchedEffect(state.playRequest) {
        val request = state.playRequest ?: return@LaunchedEffect
        onPlay(request)
        viewModel.consumePlayRequest()
    }

    val items = if (isMoviesSelected) state.movies else state.tvShows
    val isLoading = if (isMoviesSelected) state.isLoadingMovies else state.isLoadingTvShows
    val hasMore = if (isMoviesSelected) state.moviePage < state.moviePages else state.tvPage < state.tvPages
    val pageLabel = if (isMoviesSelected) {
        "page ${state.moviePage} / ${state.moviePages}"
    } else {
        "page ${state.tvPage} / ${state.tvPages}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaletteBackground)
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .statusBarsPadding(),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = TribalIcons.Back,
                    contentDescription = "Back to channels",
                    tint = PaletteTextPrimary
                )
            }
            Text(
                text = "Movies",
                style = MaterialTheme.typography.titleLarge,
                color = PaletteTextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        androidx.compose.material3.HorizontalDivider(color = PaletteOutline, thickness = 1.dp)

        // ---- Category tabs ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CategoryChip(
                label = "Movies",
                selected = isMoviesSelected,
                onClick = { viewModel.selectCategory(MoviesCategory.MOVIES) }
            )
            CategoryChip(
                label = "TV Shows",
                selected = !isMoviesSelected,
                onClick = { viewModel.selectCategory(MoviesCategory.TV_SHOWS) }
            )
        }

        // ---- Error state ----
        if (state.error != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.error,
                    color = PaletteOffline,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.clearError(); viewModel.refresh() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteGold,
                        contentColor = PaletteBackground
                    )
                ) {
                    Text("Retry")
                }
            }
        }

        // ---- Empty state ----
        else if (items.isEmpty() && !isLoading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isMoviesSelected) "No movies available" else "No TV shows available",
                    color = PaletteTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ---- List ----
        else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.tmdbId }
                ) { _, item ->
                    MediaRow(item = item, onClick = { viewModel.onMediaClicked(item) })
                }

                // Load more row
                if (hasMore && !isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load more ($pageLabel)")
                            }
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = PaletteGold)
                        }
                    }
                }
            }
        }

        // ---- Refresh action ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = TribalIcons.Refresh,
                    contentDescription = "Refresh",
                    tint = PaletteTextPrimary
                )
            }
        }
    }

    // ---- Season / episode picker ----
    state.picker?.let { picker ->
        EpisodePickerDialog(
            picker = picker,
            onSelectSeason = viewModel::selectSeason,
            onSelectEpisode = viewModel::selectEpisode,
            onPlay = { viewModel.playSelectedEpisode() },
            onDismiss = { viewModel.dismissPicker() }
        )
    }
}

/** Category toggle chip: gold when selected, card + outline when not. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
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

/** A single movie / TV row: poster thumbnail, title, year and rating. */
@Composable
private fun MediaRow(item: MediaSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteCard, RoundedCornerShape(8.dp))
            .border(1.dp, PaletteOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 96.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PaletteSurface),
            contentAlignment = Alignment.Center
        ) {
            val poster = tmdbPosterUrl(item.posterPath)
            if (poster != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poster)
                        .size(128, 192)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.title.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaletteTextSecondary
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                color = PaletteTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildMeta(item)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = meta, fontSize = 12.sp, color = PaletteTextSecondary)
            }
        }

        Icon(
            imageVector = TribalIcons.Next,
            contentDescription = "Open",
            tint = PaletteTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** "2024  ·  ★ 8.1" — omits missing pieces. */
private fun buildMeta(item: MediaSummary): String {
    val parts = mutableListOf<String>()
    item.year?.let { parts.add(it) }
    item.rating?.let { parts.add("★ " + String.format(Locale.US, "%.1f", it)) }
    return parts.joinToString("  ·  ")
}

/** TV season / episode chooser with TMDB-backed dropdowns. */
@Composable
private fun EpisodePickerDialog(
    picker: EpisodePickerState,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val canPlay = picker.selectedSeason != null && picker.selectedEpisode != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = picker.show.title,
                style = MaterialTheme.typography.titleMedium,
                color = PaletteTextPrimary
            )
        },
        text = {
            Column {
                if (picker.isLoadingSeasons) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = PaletteGold)
                    }
                } else {
                    DropdownSelector(
                        label = "Season",
                        value = picker.selectedSeason?.let { number ->
                            picker.seasons.firstOrNull { it.seasonNumber == number }?.name
                                ?: "Season $number"
                        } ?: "Select a season",
                        options = picker.seasons.map { it.seasonNumber },
                        optionLabel = { number ->
                            picker.seasons.firstOrNull { it.seasonNumber == number }?.name
                                ?: "Season $number"
                        },
                        onSelect = onSelectSeason
                    )
                    Spacer(Modifier.height(10.dp))
                    if (picker.isLoadingEpisodes) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = PaletteGold)
                        }
                    } else {
                        DropdownSelector(
                            label = "Episode",
                            value = picker.selectedEpisode?.let { number ->
                                picker.episodes.firstOrNull { it.episodeNumber == number }
                                    ?.let { "E${it.episodeNumber} · ${it.name}" } ?: "Episode $number"
                            } ?: "Select an episode",
                            options = picker.episodes.map { it.episodeNumber },
                            optionLabel = { number ->
                                picker.episodes.firstOrNull { it.episodeNumber == number }
                                    ?.let { "E${it.episodeNumber} · ${it.name}" } ?: "Episode $number"
                            },
                            onSelect = onSelectEpisode
                        )
                    }
                }

                picker.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, color = PaletteOffline, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPlay, enabled = canPlay) {
                Text("Play", color = if (canPlay) PaletteGold else PaletteDisabled)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PaletteTextSecondary)
            }
        }
    )
}

/** A labelled button that opens a dropdown menu of options. */
@Composable
private fun <T> DropdownSelector(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, fontSize = 12.sp, color = PaletteTextSecondary)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(text = value, color = PaletteTextPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = optionLabel(option), color = PaletteTextPrimary) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}