package com.example.iptvapp.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvapp.PaletteBackground
import com.example.iptvapp.PaletteCard
import com.example.iptvapp.PaletteGold
import com.example.iptvapp.PaletteOffline
import com.example.iptvapp.PaletteOutline
import com.example.iptvapp.PaletteTextPrimary
import com.example.iptvapp.PaletteTextSecondary
import com.example.iptvapp.TribalIcons

/**
 * Movies screen: scrollable listings of latest movies and TV shows from
 * VidSrc. Strict black/gold/gray palette matching the rest of the app.
 *
 * Two categories (Movies / TV Shows) switch instantly; each maintains its
 * own list, page, and loading state via [MoviesViewModel].
 */
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onMovieSelected: (VidSrcItem) -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.uiState

    var isMoviesSelected by remember { mutableStateOf(state.selectedCategory == MoviesCategory.MOVIES) }

    // Keep the local selection in sync with the ViewModel's category.
    if (isMoviesSelected != (state.selectedCategory == MoviesCategory.MOVIES)) {
        isMoviesSelected = state.selectedCategory == MoviesCategory.MOVIES
    }

    val items = if (isMoviesSelected) state.movies else state.tvShows
    val isLoading = if (isMoviesSelected) state.isLoadingMovies else state.isLoadingTvShows
    val hasMore = if (isMoviesSelected) state.moviePage < state.moviePages else state.tvPage < state.tvPages
    val pageLabel = if (isMoviesSelected) "page ${state.moviePage} / ${state.moviePages}" else "page ${state.tvPage} / ${state.tvPages}"

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CategoryChip(
                label = "Movies",
                selected = isMoviesSelected,
                onClick = {
                    isMoviesSelected = true
                    viewModel.selectCategory(MoviesCategory.MOVIES)
                }
            )
            CategoryChip(
                label = "TV Shows",
                selected = !isMoviesSelected,
                onClick = {
                    isMoviesSelected = false
                    viewModel.selectCategory(MoviesCategory.TV_SHOWS)
                }
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
                    .padding(horizontal = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = items,
                    key = { index, item ->
                        VidSrcUrlBuilder.bestId(item) ?: "no-id:$index"
                    }
                ) { item ->
                    MovieCard(item = item, onClick = { onMovieSelected(item) })
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

/** A single movie / TV show list row: title, quality, and a select action. */
@Composable
private fun MovieCard(item: VidSrcItem, onClick: () -> Unit) {
    val title = item.title ?: "Untitled"
    val quality = item.quality
    val id = VidSrcUrlBuilder.bestId(item)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteCard, RoundedCornerShape(8.dp))
            .border(1.dp, PaletteOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = PaletteTextPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (quality != null && quality.isNotEmpty()) {
                    Text(
                        text = quality,
                        fontSize = 12.sp,
                        color = PaletteTextSecondary
                    )
                } else if (id != null) {
                    Text(
                        text = id,
                        fontSize = 11.sp,
                        color = PaletteTextSecondary
                    )
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
}
