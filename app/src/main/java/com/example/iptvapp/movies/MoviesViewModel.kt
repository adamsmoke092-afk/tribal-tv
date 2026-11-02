package com.example.iptvapp.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UI state for the Movies screen: TMDB movie / TV lists, pagination,
 * error state, the TV season/episode picker, and a one-shot play request.
 */
data class MoviesUiState(
    val movies: List<MediaSummary> = emptyList(),
    val tvShows: List<MediaSummary> = emptyList(),
    val isLoadingMovies: Boolean = false,
    val isLoadingTvShows: Boolean = false,
    val moviePage: Int = 1,
    val tvPage: Int = 1,
    val moviePages: Int = 1,
    val tvPages: Int = 1,
    val error: String? = null,
    val selectedCategory: MoviesCategory = MoviesCategory.MOVIES,
    val picker: EpisodePickerState? = null,
    val playRequest: PlayRequest? = null
)

/** Which category is currently displayed. */
enum class MoviesCategory { MOVIES, TV_SHOWS }

/** State of the TV season/episode picker dialog. */
data class EpisodePickerState(
    val show: MediaSummary,
    val seasons: List<TvSeason> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<TvEpisode> = emptyList(),
    val selectedEpisode: Int? = null,
    val isLoadingSeasons: Boolean = false,
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null
)

/** A one-shot request for the host to open the embed player. */
sealed interface PlayRequest {
    data class Movie(val tmdbId: Int) : PlayRequest
    data class TvEpisode(val tmdbId: Int, val season: Int, val episode: Int) : PlayRequest
}

/**
 * ViewModel for the Movies screen. Independent of IptvViewModel.
 *
 * Listings and details come from TMDB via [TmdbRepository]; entries are
 * deduped by TMDB id both within a page and across pages. Tapping a movie
 * emits a [PlayRequest.Movie]; tapping a TV show opens the picker, which
 * loads seasons and episodes and emits [PlayRequest.TvEpisode].
 */
class MoviesViewModel(private val repository: TmdbRepository = TmdbRepository()) : ViewModel() {

    private val _uiState = mutableStateOf(MoviesUiState())
    val uiState: MoviesUiState get() = _uiState.value

    private var movieIds = mutableSetOf<Int>()
    private var tvIds = mutableSetOf<Int>()
    private var movieJob: Job? = null
    private var tvJob: Job? = null
    private var pickerJob: Job? = null

    private fun setUiState(block: (MoviesUiState) -> MoviesUiState) {
        _uiState.value = block(_uiState.value)
    }

    /** Loads the selected category the first time the screen appears. */
    fun ensureLoaded() {
        when (_uiState.value.selectedCategory) {
            MoviesCategory.MOVIES ->
                if (_uiState.value.movies.isEmpty() && !_uiState.value.isLoadingMovies) refreshMovies()
            MoviesCategory.TV_SHOWS ->
                if (_uiState.value.tvShows.isEmpty() && !_uiState.value.isLoadingTvShows) refreshTvShows()
        }
    }

    /** Loads page 1 of trending movies, replacing the current list. */
    fun refreshMovies() {
        movieJob?.cancel()
        movieJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingMovies = true, error = null) }
                val page = repository.fetchTrendingMovies(page = 1).getOrNull()
                if (page == null) {
                    setUiState { it.copy(isLoadingMovies = false, error = "Failed to load movies") }
                    return@launch
                }
                val items = page.items.distinctBy { it.tmdbId }
                movieIds = items.map { it.tmdbId }.toMutableSet()
                setUiState {
                    it.copy(
                        movies = items,
                        isLoadingMovies = false,
                        moviePage = 1,
                        moviePages = page.totalPages,
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

    /** Loads page 1 of trending TV shows, replacing the current list. */
    fun refreshTvShows() {
        tvJob?.cancel()
        tvJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingTvShows = true, error = null) }
                val page = repository.fetchTrendingTv(page = 1).getOrNull()
                if (page == null) {
                    setUiState { it.copy(isLoadingTvShows = false, error = "Failed to load TV shows") }
                    return@launch
                }
                val items = page.items.distinctBy { it.tmdbId }
                tvIds = items.map { it.tmdbId }.toMutableSet()
                setUiState {
                    it.copy(
                        tvShows = items,
                        isLoadingTvShows = false,
                        tvPage = 1,
                        tvPages = page.totalPages,
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

    /** Appends the next page of movies, skipping ids already shown. */
    fun loadMoreMovies() {
        if (_uiState.value.moviePage >= _uiState.value.moviePages) return
        movieJob?.cancel()
        movieJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingMovies = true) }
                val nextPage = _uiState.value.moviePage + 1
                val page = repository.fetchTrendingMovies(page = nextPage).getOrNull()
                if (page == null) {
                    setUiState { it.copy(isLoadingMovies = false, error = "Failed to load more movies") }
                    return@launch
                }
                val fresh = page.items
                    .distinctBy { it.tmdbId }
                    .filterNot { it.tmdbId in movieIds }
                fresh.forEach { movieIds.add(it.tmdbId) }
                setUiState {
                    it.copy(
                        movies = it.movies + fresh,
                        isLoadingMovies = false,
                        moviePage = nextPage,
                        moviePages = page.totalPages,
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

    /** Appends the next page of TV shows, skipping ids already shown. */
    fun loadMoreTvShows() {
        if (_uiState.value.tvPage >= _uiState.value.tvPages) return
        tvJob?.cancel()
        tvJob = viewModelScope.launch {
            try {
                setUiState { it.copy(isLoadingTvShows = true) }
                val nextPage = _uiState.value.tvPage + 1
                val page = repository.fetchTrendingTv(page = nextPage).getOrNull()
                if (page == null) {
                    setUiState { it.copy(isLoadingTvShows = false, error = "Failed to load more TV shows") }
                    return@launch
                }
                val fresh = page.items
                    .distinctBy { it.tmdbId }
                    .filterNot { it.tmdbId in tvIds }
                fresh.forEach { tvIds.add(it.tmdbId) }
                setUiState {
                    it.copy(
                        tvShows = it.tvShows + fresh,
                        isLoadingTvShows = false,
                        tvPage = nextPage,
                        tvPages = page.totalPages,
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

    fun refresh() {
        when (_uiState.value.selectedCategory) {
            MoviesCategory.MOVIES -> refreshMovies()
            MoviesCategory.TV_SHOWS -> refreshTvShows()
        }
    }

    fun loadMore() {
        when (_uiState.value.selectedCategory) {
            MoviesCategory.MOVIES -> loadMoreMovies()
            MoviesCategory.TV_SHOWS -> loadMoreTvShows()
        }
    }

    fun selectCategory(category: MoviesCategory) {
        setUiState { it.copy(selectedCategory = category, error = null) }
        when (category) {
            MoviesCategory.MOVIES -> if (_uiState.value.movies.isEmpty()) refreshMovies()
            MoviesCategory.TV_SHOWS -> if (_uiState.value.tvShows.isEmpty()) refreshTvShows()
        }
    }

    fun clearError() {
        setUiState { it.copy(error = null) }
    }

    /** Handles a tap: movies play immediately, TV shows open the picker. */
    fun onMediaClicked(item: MediaSummary) {
        when (item.mediaType) {
            MediaType.MOVIE -> setUiState { it.copy(playRequest = PlayRequest.Movie(item.tmdbId)) }
            MediaType.TV -> openPicker(item)
        }
    }

    /** Clears the one-shot play request after the host consumed it. */
    fun consumePlayRequest() {
        setUiState { it.copy(playRequest = null) }
    }

    private fun openPicker(show: MediaSummary) {
        pickerJob?.cancel()
        setUiState { it.copy(picker = EpisodePickerState(show = show, isLoadingSeasons = true)) }
        pickerJob = viewModelScope.launch {
            try {
                val seasons = repository.fetchSeasons(show.tmdbId).getOrNull()
                if (seasons == null) {
                    setPickerError("Failed to load seasons")
                    return@launch
                }
                val defaultSeason = seasons.firstOrNull { it.seasonNumber >= 1 } ?: seasons.firstOrNull()
                if (defaultSeason == null) {
                    setUiState {
                        it.copy(picker = it.picker?.copy(
                            seasons = seasons,
                            isLoadingSeasons = false,
                            error = "No seasons available"
                        ))
                    }
                    return@launch
                }
                setUiState {
                    it.copy(picker = it.picker?.copy(
                        seasons = seasons,
                        isLoadingSeasons = false,
                        selectedSeason = defaultSeason.seasonNumber,
                        episodes = emptyList(),
                        selectedEpisode = null,
                        isLoadingEpisodes = true,
                        error = null
                    ))
                }
                val episodes = repository.fetchEpisodes(show.tmdbId, defaultSeason.seasonNumber).getOrNull()
                if (episodes == null) {
                    setUiState {
                        it.copy(picker = it.picker?.copy(
                            isLoadingEpisodes = false,
                            error = "Failed to load episodes"
                        ))
                    }
                    return@launch
                }
                setUiState {
                    it.copy(picker = it.picker?.copy(
                        episodes = episodes,
                        isLoadingEpisodes = false
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setPickerError(e.message ?: "Failed to load episodes")
            }
        }
    }

    /** Switches season, reloading its episode list. */
    fun selectSeason(season: Int) {
        val picker = _uiState.value.picker ?: return
        if (picker.selectedSeason == season && picker.episodes.isNotEmpty()) return
        pickerJob?.cancel()
        setUiState {
            it.copy(picker = it.picker?.copy(
                selectedSeason = season,
                episodes = emptyList(),
                selectedEpisode = null,
                isLoadingEpisodes = true,
                error = null
            ))
        }
        pickerJob = viewModelScope.launch {
            try {
                val episodes = repository.fetchEpisodes(picker.show.tmdbId, season).getOrNull()
                if (episodes == null) {
                    setUiState {
                        it.copy(picker = it.picker?.copy(
                            isLoadingEpisodes = false,
                            error = "Failed to load episodes"
                        ))
                    }
                    return@launch
                }
                setUiState {
                    it.copy(picker = it.picker?.copy(
                        episodes = episodes,
                        isLoadingEpisodes = false
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setPickerError(e.message ?: "Failed to load episodes")
            }
        }
    }

    fun selectEpisode(episode: Int) {
        setUiState { state ->
            val picker = state.picker ?: return@setUiState state
            state.copy(picker = picker.copy(selectedEpisode = episode))
        }
    }

    /** Emits a play request for the selected TV episode and closes the picker. */
    fun playSelectedEpisode() {
        val picker = _uiState.value.picker ?: return
        val season = picker.selectedSeason ?: return
        val episode = picker.selectedEpisode ?: return
        setUiState {
            it.copy(
                playRequest = PlayRequest.TvEpisode(picker.show.tmdbId, season, episode),
                picker = null
            )
        }
    }

    fun dismissPicker() {
        pickerJob?.cancel()
        setUiState { it.copy(picker = null) }
    }

    private fun setPickerError(message: String) {
        setUiState {
            it.copy(picker = it.picker?.copy(
                isLoadingSeasons = false,
                isLoadingEpisodes = false,
                error = message
            ))
        }
    }

    override fun onCleared() {
        movieJob?.cancel()
        tvJob?.cancel()
        pickerJob?.cancel()
        super.onCleared()
    }
}