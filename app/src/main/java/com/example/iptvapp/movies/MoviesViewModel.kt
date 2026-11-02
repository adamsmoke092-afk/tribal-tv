package com.example.iptvapp.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UI state for the Movies screen. Holds movies, TV shows, loading,
 * pagination, and error state independently of IptvViewModel.
 */
data class MoviesUiState(
    val movies: List<VidSrcItem> = emptyList(),
    val tvShows: List<VidSrcItem> = emptyList(),
    val isLoadingMovies: Boolean = false,
    val isLoadingTvShows: Boolean = false,
    val isRefreshing: Boolean = false,
    val moviePage: Int = 1,
    val tvPage: Int = 1,
    val moviePages: Int = 1,
    val tvPages: Int = 1,
    val error: String? = null,
    val selectedCategory: MoviesCategory = MoviesCategory.MOVIES
) {
    fun copy(
        movies: List<VidSrcItem> = this.movies,
        tvShows: List<VidSrcItem> = this.tvShows,
        isLoadingMovies: Boolean = this.isLoadingMovies,
        isLoadingTvShows: Boolean = this.isLoadingTvShows,
        isRefreshing: Boolean = this.isRefreshing,
        moviePage: Int = this.moviePage,
        tvPage: Int = this.tvPage,
        moviePages: Int = this.moviePages,
        tvPages: Int = this.tvPages,
        error: String? = this.error,
        selectedCategory: MoviesCategory = this.selectedCategory
    ) = MoviesUiState(
        movies, tvShows, isLoadingMovies, isLoadingTvShows, isRefreshing,
        moviePage, tvPage, moviePages, tvPages, error, selectedCategory
    )
}

/** Which category is currently displayed. */
enum class MoviesCategory { MOVIES, TV_SHOWS }

/**
 * ViewModel for the Movies screen. Independent of IptvViewModel — no
 * movie state lives in the IPTV ViewModel.
 *
 * Network calls run on Dispatchers.IO via viewModelScope. Duplicate
 * entries across pages are avoided by deduping on IMDb/TMDB ID.
 */
class MoviesViewModel(private val repository: VidSrcRepository = VidSrcRepository()) : ViewModel() {

    private val _uiState = mutableStateOf(MoviesUiState())
    val uiState: MoviesUiState get() = _uiState.value

    private var movieIds = mutableSetOf<String>()
    private var tvIds = mutableSetOf<String>()
    private var movieJob: Job? = null
    private var tvJob: Job? = null

    private fun setUiState(block: MoviesUiState.() -> MoviesUiState) {
        _uiState.value = _uiState.value.copy(block)
    }

    /**
     * Loads page 1 of the latest movies feed. Replaces any existing list.
     */
    fun refreshMovies() {
        movieJob?.cancel()
        movieJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingMovies = true, error = null) }
                val result = repository.fetchLatestMovies(page = 1)
                val feed = result.getOrNull()
                if (feed == null) {
                    setUiState { it.copy(isLoadingMovies = false, error = "Failed to load movies") }
                    return@launch
                }
                val validMovies = feed.result.filter { VidSrcUrlBuilder.bestId(it) != null }
                movieIds = validMovies.mapNotNull { VidSrcUrlBuilder.bestId(it) }.toMutableSet()
                setUiState {
                    it.copy(
                        movies = validMovies,
                        isLoadingMovies = false,
                        moviePage = 1,
                        moviePages = feed.pages.coerceAtLeast(1),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUiState { it.copy(isLoadingMovies = false, error = e.message ?: "Failed to load movies") }
            }
        }
    }

    /**
     * Loads page 1 of the latest TV shows feed. Replaces any existing list.
     */
    fun refreshTvShows() {
        tvJob?.cancel()
        tvJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingTvShows = true, error = null) }
                val result = repository.fetchLatestTvShows(page = 1)
                val feed = result.getOrNull()
                if (feed == null) {
                    setUiState { it.copy(isLoadingTvShows = false, error = "Failed to load TV shows") }
                    return@launch
                }
                val validTvShows = feed.result.filter { VidSrcUrlBuilder.bestId(it) != null }
                tvIds = validTvShows.mapNotNull { VidSrcUrlBuilder.bestId(it) }.toMutableSet()
                setUiState {
                    it.copy(
                        tvShows = validTvShows,
                        isLoadingTvShows = false,
                        tvPage = 1,
                        tvPages = feed.pages.coerceAtLeast(1),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUiState { it.copy(isLoadingTvShows = false, error = e.message ?: "Failed to load TV shows") }
            }
        }
    }

    /**
     * Appends the next page of movies, skipping IDs already in the list.
     * Does nothing when already on the last page.
     */
    fun loadMoreMovies() {
        if (_uiState.value.moviePage >= _uiState.value.moviePages) return
        movieJob?.cancel()
        movieJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingMovies = true) }
                val nextPage = _uiState.value.moviePage + 1
                val result = repository.fetchLatestMovies(page = nextPage)
                val feed = result.getOrNull()
                if (feed == null) {
                    setUiState { it.copy(isLoadingMovies = false, error = "Failed to load more movies") }
                    return@launch
                }
                val newItems = feed.result
                    .filter { VidSrcUrlBuilder.bestId(it) != null }
                    .filterNot { VidSrcUrlBuilder.bestId(it) in movieIds }
                if (newItems.isNotEmpty()) {
                    newItems.mapNotNull { VidSrcUrlBuilder.bestId(it) }.forEach { movieIds.add(it) }
                }
                setUiState {
                    it.copy(
                        movies = it.movies + newItems,
                        isLoadingMovies = false,
                        moviePage = nextPage,
                        moviePages = feed.pages.coerceAtLeast(1),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUiState { it.copy(isLoadingMovies = false, error = e.message ?: "Failed to load more movies") }
            }
        }
    }

    /**
     * Appends the next page of TV shows, skipping IDs already in the list.
     * Does nothing when already on the last page.
     */
    fun loadMoreTvShows() {
        if (_uiState.value.tvPage >= _uiState.value.tvPages) return
        tvJob?.cancel()
        tvJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingTvShows = true) }
                val nextPage = _uiState.value.tvPage + 1
                val result = repository.fetchLatestTvShows(page = nextPage)
                val feed = result.getOrNull()
                if (feed == null) {
                    setUiState { it.copy(isLoadingTvShows = false, error = "Failed to load more TV shows") }
                    return@launch
                }
                val newItems = feed.result
                    .filter { VidSrcUrlBuilder.bestId(it) != null }
                    .filterNot { VidSrcUrlBuilder.bestId(it) in tvIds }
                if (newItems.isNotEmpty()) {
                    newItems.mapNotNull { VidSrcUrlBuilder.bestId(it) }.forEach { tvIds.add(it) }
                }
                setUiState {
                    it.copy(
                        tvShows = it.tvShows + newItems,
                        isLoadingTvShows = false,
                        tvPage = nextPage,
                        tvPages = feed.pages.coerceAtLeast(1),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUiState { it.copy(isLoadingTvShows = false, error = e.message ?: "Failed to load more TV shows") }
            }
        }
    }

    /**
     * Refreshes the currently selected category.
     */
    fun refresh() {
        when (_uiState.value.selectedCategory) {
            MoviesCategory.MOVIES -> refreshMovies()
            MoviesCategory.TV_SHOWS -> refreshTvShows()
        }
    }

    /**
     * Loads more for the currently selected category.
     */
    fun loadMore() {
        when (_uiState.value.selectedCategory) {
            MoviesCategory.MOVIES -> loadMoreMovies()
            MoviesCategory.TV_SHOWS -> loadMoreTvShows()
        }
    }

    fun selectCategory(category: MoviesCategory) {
        setUiState { it.copy(selectedCategory = category, error = null) }
        when (category) {
            MoviesCategory.MOVIES -> {
                if (_uiState.value.movies.isEmpty()) refreshMovies()
            }
            MoviesCategory.TV_SHOWS -> {
                if (_uiState.value.tvShows.isEmpty()) refreshTvShows()
            }
        }
    }

    fun clearError() {
        setUiState { it.copy(error = null) }
    }

    override fun onCleared() {
        movieJob?.cancel()
        tvJob?.cancel()
        super.onCleared()
    }
}
