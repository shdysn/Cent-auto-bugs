package com.ct.explorer.features.media

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.viewmodel.AudioPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayerViewModel(application: Application) : BaseFeatureViewModel(application) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioProgressJob: Job? = null
    private val _audioPlayerState = MutableStateFlow(AudioPlayerState())
    val audioPlayerState: StateFlow<AudioPlayerState> = _audioPlayerState.asStateFlow()

    fun playAudio(item: FileItem, playlist: List<FileItem> = emptyList()) {
        viewModelScope.launch {
            try {
                audioProgressJob?.cancel()
                runCatching { mediaPlayer?.stop() }
                runCatching { mediaPlayer?.release() }
                mediaPlayer = null

                val player = MediaPlayer()
                player.setDataSource(item.file.absolutePath)
                withContext(Dispatchers.IO) {
                    player.prepare()
                }

                var title = item.name
                var artist = "Unknown Artist"
                val duration = player.duration

                var mmr: MediaMetadataRetriever? = null
                try {
                    mmr = MediaMetadataRetriever()
                    mmr.setDataSource(item.file.absolutePath)
                    title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: item.name
                    artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                } catch (_: Exception) {
                    // Fallback to filename
                } finally {
                    runCatching { mmr?.release() }
                }

                val fullList = if (playlist.isNotEmpty()) playlist else listOf(item)
                val idx = fullList.indexOfFirst { it.path == item.path }.coerceAtLeast(0)

                val targetSpeed = _audioPlayerState.value.playbackSpeed
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && targetSpeed != 1.0f) {
                    try {
                        val params = player.playbackParams ?: android.media.PlaybackParams()
                        params.speed = targetSpeed
                        player.playbackParams = params
                    } catch (_: Exception) {}
                }

                player.start()
                mediaPlayer = player

                _audioPlayerState.value = AudioPlayerState(
                    currentFile = item.file,
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    currentPositionMs = 0,
                    isPlaying = true,
                    isVisible = true,
                    isExpanded = true,
                    playlist = fullList,
                    currentIndex = idx,
                    isShuffle = _audioPlayerState.value.isShuffle,
                    isRepeat = _audioPlayerState.value.isRepeat,
                    playbackSpeed = targetSpeed
                )

                player.setOnCompletionListener {
                    val state = _audioPlayerState.value
                    if (state.isRepeat) {
                        player.seekTo(0)
                        player.start()
                    } else {
                        playNextAudio()
                    }
                }

                startAudioProgressTicker()
            } catch (e: Exception) {
                showMessage("Cannot play audio: ${e.localizedMessage}")
            }
        }
    }

    fun pauseAudio() {
        val player = mediaPlayer ?: return
        if (runCatching { player.isPlaying }.getOrDefault(false)) {
            runCatching { player.pause() }
            _audioPlayerState.update { it.copy(isPlaying = false) }
        }
    }

    fun toggleAudioPlayPause() {
        val player = mediaPlayer ?: return
        val isCurrentlyPlaying = runCatching { player.isPlaying }.getOrDefault(false)
        if (isCurrentlyPlaying) {
            runCatching { player.pause() }
            _audioPlayerState.update { it.copy(isPlaying = false) }
        } else {
            runCatching { player.start() }
            _audioPlayerState.update { it.copy(isPlaying = true) }
        }
    }

    fun seekAudioTo(positionMs: Int) {
        runCatching { mediaPlayer?.seekTo(positionMs) }
        _audioPlayerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun playNextAudio() {
        val state = _audioPlayerState.value
        if (state.playlist.isEmpty()) return
        val nextIdx = if (state.isShuffle) {
            state.playlist.indices.random()
        } else {
            (state.currentIndex + 1) % state.playlist.size
        }
        val nextItem = state.playlist[nextIdx]
        playAudio(nextItem, state.playlist)
    }

    fun playPreviousAudio() {
        val state = _audioPlayerState.value
        if (state.playlist.isEmpty()) return
        val prevIdx = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.lastIndex
        val prevItem = state.playlist[prevIdx]
        playAudio(prevItem, state.playlist)
    }

    fun toggleAudioExpanded() {
        _audioPlayerState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun toggleAudioShuffle() {
        _audioPlayerState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleAudioRepeat() {
        _audioPlayerState.update { it.copy(isRepeat = !it.isRepeat) }
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        _audioPlayerState.update { it.copy(playbackSpeed = speed) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val player = mediaPlayer ?: return
                val params = player.playbackParams ?: android.media.PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (_: Exception) {}
        }
    }

    fun closeAudioPlayer() {
        audioProgressJob?.cancel()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _audioPlayerState.update { it.copy(isPlaying = false, isVisible = false, isExpanded = false) }
    }

    private fun startAudioProgressTicker() {
        audioProgressJob?.cancel()
        audioProgressJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null) {
                    val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
                    val currentPos = runCatching { player.currentPosition }.getOrDefault(0)
                    if (isPlaying) {
                        _audioPlayerState.update {
                            it.copy(currentPositionMs = currentPos, isPlaying = true)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioProgressJob?.cancel()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
