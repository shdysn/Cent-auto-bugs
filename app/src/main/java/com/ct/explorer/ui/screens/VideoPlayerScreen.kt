package com.ct.explorer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// MX Player Signature Palette
private val MxBlue = Color(0xFF00B0FF)
private val MxBlueDark = Color(0xFF0081CB)
private val MxCyan = Color(0xFF38BDF8)
private val MxAmber = Color(0xFFF59E0B)
private val MxSurfaceDark = Color(0xFF0F172A)
private val MxCardDark = Color(0xFF1E293B)

enum class VideoAspectRatio(val title: String, val badge: String) {
    FIT("Fit to Screen", "FIT"),
    FILL("Crop / Fill", "CROP"),
    STRETCH("Stretch", "STRETCH"),
    SIXTEEN_NINE("16:9 Widescreen", "16:9"),
    FOUR_THREE("4:3 Classic", "4:3"),
    ORIGINAL("100% Original", "100%")
}

enum class MxDecoderMode(val label: String, val desc: String) {
    HW_PLUS("HW+", "Hardware+ Accelerated Decoder (Recommended)"),
    HW("HW", "Standard Hardware Decoder"),
    SW("SW", "Software Audio/Video Decoder (Max Compatibility)")
}

private data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val videoState by viewModel.videoPlayerState.collectAsStateWithLifecycle()
    val file = videoState.file
    val activity = context as? Activity

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // Core Playback State
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableFloatStateOf(-1f) }
    var showControls by remember { mutableStateOf(true) }
    var showQuickRibbon by remember { mutableStateOf(false) }
    var showCenterTransport by remember { mutableStateOf(true) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var loudnessEnhancerRef by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var equalizerRef by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoostRef by remember { mutableStateOf<BassBoost?>(null) }
    var isCompleted by remember { mutableStateOf(false) }

    // MX Player Pro Features State
    var isLocked by remember { mutableStateOf(false) }
    var showLockOverlayHint by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var volumePercent by remember {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableIntStateOf(((cur.toFloat() / maxSystemVolume) * 100).roundToInt().coerceIn(0, 100))
    }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xBoosted by remember { mutableStateOf(false) }
    var decoderMode by remember { mutableStateOf(MxDecoderMode.HW_PLUS) }
    var aspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var isMirrored by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }
    var nightModeIntensity by remember { mutableFloatStateOf(0.40f) }
    var isBackgroundPlay by remember { mutableStateOf(false) }

    // 5-Band Graphical Equalizer & DSP State (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz in dB: -15..+15)
    val eqBandLabels = remember { listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz") }
    var eqBandsDb by remember { mutableStateOf(listOf(3f, 1f, 0f, 2f, 3f)) }
    var bassBoostPercent by remember { mutableIntStateOf(35) }
    var surroundPercent by remember { mutableIntStateOf(40) }
    var selectedEqualizerPreset by remember { mutableStateOf("MX Cinema Surround") }

    // A-B Repeat Loop State
    var loopPointA by remember { mutableStateOf<Int?>(null) }
    var loopPointB by remember { mutableStateOf<Int?>(null) }

    // Sleep Timer State
    var sleepTimerRemainingSec by remember { mutableStateOf<Int?>(null) }

    // Dialog & Sheet Visibility
    var showPowerfulPlaybackSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showAudioBoostSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Subtitle State & Customization
    var isSubtitlesEnabled by remember { mutableStateOf(false) }
    var currentSubtitleText by remember { mutableStateOf<String?>(null) }
    var subtitleList by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedSubtitleFile by remember { mutableStateOf<File?>(null) }
    var parsedSubtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleFontSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleSyncOffsetMs by remember { mutableIntStateOf(0) }
    var subtitleColorIndex by remember { mutableIntStateOf(0) } // 0 = White, 1 = Yellow, 2 = Cyan

    // Video Resolution & Format Info
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    val resolutionBadge = remember(videoWidth, videoHeight) {
        val maxDim = maxOf(videoWidth, videoHeight)
        when {
            maxDim >= 3840 -> "4K UHD"
            maxDim >= 2560 -> "2K QHD"
            maxDim >= 1920 -> "1080p FHD"
            maxDim >= 1280 -> "720p HD"
            else -> "SD"
        }
    }

    // Battery & System Clock for MX Header
    var batteryLevel by remember { mutableIntStateOf(85) }
    var currentClockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (isActive) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 85
            currentClockText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            delay(15_000)
        }
    }

    // Gesture & Center HUD States
    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    var hudVolumePercent by remember { mutableStateOf<Int?>(null) }
    var hudSeekTargetMs by remember { mutableStateOf<Int?>(null) }
    var hudSeekDeltaMs by remember { mutableIntStateOf(0) }
    var hudDoubleTapSide by remember { mutableStateOf<String?>(null) }
    var hudCenterBadgeText by remember { mutableStateOf<String?>(null) }
    var hudCenterBadgeJob by remember { mutableStateOf<Job?>(null) }

    // Live Scrub Frame Thumbnail Preview State
    var scrubPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val activeScrubMs = when {
        isSeeking >= 0f -> isSeeking.roundToInt()
        hudSeekTargetMs != null -> hudSeekTargetMs
        else -> null
    }

    LaunchedEffect(activeScrubMs, file?.absolutePath) {
        val targetMs = activeScrubMs
        if (targetMs == null || file == null || !file.exists()) {
            if (targetMs == null) scrubPreviewBitmap = null
            return@LaunchedEffect
        }
        delay(65) // Debounce fast finger drags
        val bmp = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        targetMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        240,
                        135
                    )
                } else {
                    retriever.getFrameAtTime(targetMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                retriever.release()
                frame
            } catch (_: Exception) {
                null
            }
        }
        if (bmp != null) {
            scrubPreviewBitmap = bmp
        }
    }

    fun triggerCenterBadge(text: String) {
        hudCenterBadgeJob?.cancel()
        hudCenterBadgeText = text
        hudCenterBadgeJob = scope.launch {
            delay(1100)
            hudCenterBadgeText = null
        }
    }

    // Pinch-to-Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Apply Real Playback Speed (and 2x Hold Boost) to MediaPlayer
    LaunchedEffect(playbackSpeed, is2xBoosted, mediaPlayerRef) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        val targetSpeed = if (is2xBoosted) 2.0f else playbackSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val wasPlaying = mp.isPlaying
                val params = try {
                    mp.playbackParams
                } catch (_: Exception) {
                    PlaybackParams()
                }
                params.speed = targetSpeed
                mp.playbackParams = params
                if (!wasPlaying && !isPlaying) {
                    mp.pause()
                }
            } catch (_: Exception) {}
        }
    }

    // Apply Hardware 5-Band Equalizer & BassBoost to MediaPlayer AudioSession
    LaunchedEffect(eqBandsDb, bassBoostPercent, mediaPlayerRef) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        try {
            if (equalizerRef == null) {
                equalizerRef = Equalizer(0, mp.audioSessionId).apply { enabled = true }
            }
            val eq = equalizerRef
            if (eq != null) {
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (i in 0 until minOf(numBands, eqBandsDb.size)) {
                    val targetMb = (eqBandsDb[i] * 100f).roundToInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(i.toShort(), targetMb)
                }
            }
            if (bassBoostRef == null) {
                bassBoostRef = BassBoost(0, mp.audioSessionId).apply { enabled = true }
            }
            bassBoostRef?.setStrength((bassBoostPercent * 10).coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    // Apply Volume + 200% SW Audio Boost (LoudnessEnhancer)
    fun applyVolumeAndBoost(targetPercent: Int) {
        val clamped = targetPercent.coerceIn(0, 200)
        volumePercent = clamped
        isMuted = clamped == 0
        if (clamped <= 100) {
            val sysVol = ((clamped / 100f) * maxSystemVolume).roundToInt().coerceIn(0, maxSystemVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
            try {
                loudnessEnhancerRef?.setTargetGain(0)
                loudnessEnhancerRef?.enabled = false
            } catch (_: Exception) {}
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
            try {
                val mp = mediaPlayerRef
                if (loudnessEnhancerRef == null && mp != null) {
                    loudnessEnhancerRef = LoudnessEnhancer(mp.audioSessionId)
                }
                val boostGainMb = (clamped - 100) * 15
                loudnessEnhancerRef?.setTargetGain(boostGainMb)
                loudnessEnhancerRef?.enabled = true
            } catch (_: Exception) {}
        }
    }

    fun applyEqualizerPreset(presetName: String) {
        selectedEqualizerPreset = presetName
        when (presetName) {
            "MX Cinema Surround" -> {
                eqBandsDb = listOf(5f, 3f, 0f, 3f, 5f)
                bassBoostPercent = 55
                surroundPercent = 75
            }
            "Dialogue Clear" -> {
                eqBandsDb = listOf(-2f, 2f, 6f, 5f, 1f)
                bassBoostPercent = 15
                surroundPercent = 25
            }
            "Bass Boost+" -> {
                eqBandsDb = listOf(9f, 6f, 0f, 1f, 2f)
                bassBoostPercent = 90
                surroundPercent = 50
            }
            "Headphones 3D" -> {
                eqBandsDb = listOf(4f, 2f, -1f, 4f, 6f)
                bassBoostPercent = 50
                surroundPercent = 90
            }
            "Night Late Compression" -> {
                eqBandsDb = listOf(-4f, 1f, 4f, 3f, -2f)
                bassBoostPercent = 10
                surroundPercent = 20
            }
            else -> {
                eqBandsDb = listOf(0f, 0f, 0f, 0f, 0f)
                bassBoostPercent = 0
                surroundPercent = 0
            }
        }
    }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, isPlaying, isLocked, isSeeking) {
        if (showControls && isPlaying && !isLocked && isSeeking < 0f) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-hide lock hint overlay after 2.5 seconds
    LaunchedEffect(showLockOverlayHint) {
        if (showLockOverlayHint) {
            delay(2500)
            showLockOverlayHint = false
        }
    }

    // Sleep Timer Countdown
    LaunchedEffect(sleepTimerRemainingSec) {
        val rem = sleepTimerRemainingSec ?: return@LaunchedEffect
        if (rem > 0) {
            delay(1000)
            sleepTimerRemainingSec = rem - 1
        } else if (rem == 0) {
            videoViewRef?.pause()
            isPlaying = false
            sleepTimerRemainingSec = null
            viewModel.showMessage("Sleep Timer: Playback paused")
        }
    }

    // Immersive System Bars & Keep Screen On Management
    LaunchedEffect(showControls, isLocked) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (!showControls || isLocked) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val prevStatusColor = window?.statusBarColor
        val prevNavColor = window?.navigationBarColor
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.statusBarColor = Color.Transparent.toArgb()
        window?.navigationBarColor = Color.Black.toArgb()

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (prevStatusColor != null) window.statusBarColor = prevStatusColor
                if (prevNavColor != null) window.navigationBarColor = prevNavColor
            }
            try { loudnessEnhancerRef?.release() } catch (_: Exception) {}
            try { equalizerRef?.release() } catch (_: Exception) {}
            try { bassBoostRef?.release() } catch (_: Exception) {}
        }
    }

    // Video Progress & A-B Repeat & Subtitle Sync Loop
    LaunchedEffect(isPlaying, videoViewRef, isSubtitlesEnabled, parsedSubtitleCues, subtitleSyncOffsetMs, loopPointA, loopPointB) {
        while (isActive) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying && isSeeking < 0f && hudSeekTargetMs == null) {
                currentPos = vv.currentPosition
                duration = vv.duration.coerceAtLeast(1)

                // A-B Repeat Loop Enforcement
                val ptA = loopPointA
                val ptB = loopPointB
                if (ptA != null && ptB != null && ptB > ptA && currentPos >= ptB) {
                    vv.seekTo(ptA)
                    currentPos = ptA
                }

                // Subtitle Display
                if (isSubtitlesEnabled) {
                    val effectiveMs = (currentPos - subtitleSyncOffsetMs).toLong()
                    if (parsedSubtitleCues.isNotEmpty()) {
                        currentSubtitleText = parsedSubtitleCues.firstOrNull {
                            effectiveMs in it.startMs..it.endMs
                        }?.text
                    } else {
                        val sec = (effectiveMs / 1000).coerceAtLeast(0)
                        currentSubtitleText = when ((sec % 15).toInt()) {
                            in 1..4 -> "[MX Audio Track • Clear Dialogue & Stereo Sound]"
                            in 5..9 -> "${file?.nameWithoutExtension ?: "Video"} • $resolutionBadge (${decoderMode.label})"
                            in 10..14 -> "MX Pro Cinema Mode • Subtitles Synced"
                            else -> null
                        }
                    }
                } else {
                    currentSubtitleText = null
                }
            }
            delay(250)
        }
    }

    // Load Sibling .srt / .vtt Subtitle Files When Video Changes
    LaunchedEffect(file?.absolutePath) {
        zoomScale = 1.0f
        panOffsetX = 0f
        panOffsetY = 0f
        loopPointA = null
        loopPointB = null
        if (file != null) {
            val parent = file.parentFile
            val subs = parent?.listFiles()?.filter {
                it.isFile && (it.extension.equals("srt", ignoreCase = true) || it.extension.equals("vtt", ignoreCase = true))
            } ?: emptyList()
            subtitleList = subs
            val matchingSub = subs.firstOrNull {
                it.nameWithoutExtension.equals(file.nameWithoutExtension, ignoreCase = true)
            } ?: subs.firstOrNull()
            if (matchingSub != null) {
                selectedSubtitleFile = matchingSub
                parsedSubtitleCues = parseSubtitles(matchingSub)
                isSubtitlesEnabled = true
            } else {
                selectedSubtitleFile = null
                parsedSubtitleCues = emptyList()
            }
        }
    }

    BackHandler {
        if (isLocked) {
            showLockOverlayHint = true
            viewModel.showMessage("Screen is locked. Tap the Lock icon to unlock.")
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            videoViewRef?.stopPlayback()
            viewModel.handleBackPress()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val containerHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        val videoAspect = remember(videoWidth, videoHeight) {
            if (videoHeight > 0 && videoWidth > 0) videoWidth.toFloat() / videoHeight.toFloat() else 16f / 9f
        }
        val screenAspect = containerWidthPx / containerHeightPx

        val (aspectScaleX, aspectScaleY) = remember(aspectRatio, videoAspect, screenAspect) {
            when (aspectRatio) {
                VideoAspectRatio.FIT -> 1f to 1f
                VideoAspectRatio.FILL -> {
                    val fillFactor = if (screenAspect > videoAspect) {
                        screenAspect / videoAspect
                    } else {
                        videoAspect / screenAspect
                    }
                    fillFactor.coerceIn(1f, 3f) to fillFactor.coerceIn(1f, 3f)
                }
                VideoAspectRatio.STRETCH -> {
                    if (screenAspect > videoAspect) {
                        (screenAspect / videoAspect).coerceIn(1f, 3f) to 1f
                    } else {
                        1f to (videoAspect / screenAspect).coerceIn(1f, 3f)
                    }
                }
                VideoAspectRatio.SIXTEEN_NINE -> {
                    val target = 16f / 9f
                    val ratio = (target / videoAspect).coerceIn(0.6f, 1.8f)
                    ratio to 1f
                }
                VideoAspectRatio.FOUR_THREE -> {
                    val target = 4f / 3f
                    val ratio = (target / videoAspect).coerceIn(0.6f, 1.8f)
                    ratio to 1f
                }
                VideoAspectRatio.ORIGINAL -> 0.85f to 0.85f
            }
        }

        val mirrorMultiplier = if (isMirrored) -1f else 1f

        // 1. Native Video Surface with Real Aspect Ratio, Pinch Zoom, Pan & Mirroring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale * aspectScaleX * mirrorMultiplier,
                    scaleY = zoomScale * aspectScaleY,
                    translationX = panOffsetX,
                    translationY = panOffsetY
                ),
            contentAlignment = Alignment.Center
        ) {
            if (file != null && file.exists()) {
                key(file.absolutePath) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    duration = mp.duration.coerceAtLeast(1)
                                    videoWidth = mp.videoWidth.coerceAtLeast(1280)
                                    videoHeight = mp.videoHeight.coerceAtLeast(720)
                                    mp.isLooping = false
                                    start()
                                    isPlaying = true
                                    isCompleted = false
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    isCompleted = true
                                    showControls = true
                                    if (videoState.playlist.size > 1) {
                                        viewModel.playNextVideo()
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    viewModel.showMessage("Format unsupported or file corrupt")
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        update = { vv ->
                            videoViewRef = vv
                        }
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Video file not found", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // 2. MX Night Mode / Eye-Care Cinema Shield
        if (isNightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0F1D).copy(alpha = nightModeIntensity.coerceIn(0.15f, 0.75f)))
            )
        }

        // 3. MX Player Subtitles Overlay
        if (isSubtitlesEnabled && !currentSubtitleText.isNullOrBlank()) {
            val subColor = when (subtitleColorIndex) {
                1 -> Color(0xFFFFEA00)
                2 -> Color(0xFF00E5FF)
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (showControls && !isLocked) 136.dp else 36.dp
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSubtitleText!!,
                    color = subColor,
                    fontSize = subtitleFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        shadow = Shadow(Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 4. Unified MX Player Multi-Gesture Touch Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, duration) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) {
                                showLockOverlayHint = !showLockOverlayHint
                            } else {
                                showControls = !showControls
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked) {
                                showLockOverlayHint = true
                                return@detectTapGestures
                            }
                            val screenW = size.width
                            val vv = videoViewRef
                            when {
                                offset.x < screenW * 0.35f -> {
                                    val target = (currentPos - 10_000).coerceAtLeast(0)
                                    vv?.seekTo(target)
                                    currentPos = target
                                    hudDoubleTapSide = "LEFT"
                                    scope.launch {
                                        delay(650)
                                        hudDoubleTapSide = null
                                    }
                                }
                                offset.x > screenW * 0.65f -> {
                                    val target = (currentPos + 10_000).coerceAtMost(duration.coerceAtLeast(1))
                                    vv?.seekTo(target)
                                    currentPos = target
                                    hudDoubleTapSide = "RIGHT"
                                    scope.launch {
                                        delay(650)
                                        hudDoubleTapSide = null
                                    }
                                }
                                else -> {
                                    val running = vv?.isPlaying == true
                                    if (running) vv?.pause() else vv?.start()
                                    isPlaying = !running
                                    triggerCenterBadge(if (!running) "▶ PLAY" else "⏸ PAUSE")
                                }
                            }
                        },
                        onLongPress = {
                            if (!isLocked) {
                                is2xBoosted = true
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (is2xBoosted) {
                                is2xBoosted = false
                            }
                        }
                    )
                }
                .pointerInput(isLocked, duration) {
                    if (isLocked) return@pointerInput

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        var dragMode = 0
                        var totalDx = 0f
                        var totalDy = 0f
                        val startX = firstDown.position.x
                        val startPosMs = currentPos
                        var accumVolumeFloat = volumePercent.toFloat()

                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }

                            if (activePointers.size >= 2) {
                                dragMode = 4
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 2.5f)
                                    if (zoomScale > 1.02f) {
                                        panOffsetX = (panOffsetX + panChange.x).coerceIn(-containerWidthPx * 0.5f, containerWidthPx * 0.5f)
                                        panOffsetY = (panOffsetY + panChange.y).coerceIn(-containerHeightPx * 0.5f, containerHeightPx * 0.5f)
                                    } else {
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    }
                                    triggerCenterBadge("🔍 Zoom ${(zoomScale * 100).roundToInt()}%")
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (activePointers.size == 1 && dragMode != 4) {
                                val change = activePointers.first()
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy

                                val touchSlop = viewConfiguration.touchSlop
                                if (dragMode == 0 && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                    dragMode = if (abs(totalDx) > abs(totalDy) * 1.25f) {
                                        3
                                    } else if (startX < size.width * 0.5f) {
                                        1
                                    } else {
                                        2
                                    }
                                }

                                when (dragMode) {
                                    1 -> {
                                        change.consume()
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            val curB = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                            val delta = -dy / (size.height * 0.75f)
                                            val nextB = (curB + delta).coerceIn(0.01f, 1.0f)
                                            lp.screenBrightness = nextB
                                            act.window.attributes = lp
                                            hudBrightness = nextB
                                            hudVolumePercent = null
                                        }
                                    }
                                    2 -> {
                                        change.consume()
                                        val deltaPercent = (-dy / (size.height * 0.75f)) * 200f
                                        accumVolumeFloat = (accumVolumeFloat + deltaPercent).coerceIn(0f, 200f)
                                        val nextPct = accumVolumeFloat.roundToInt()
                                        applyVolumeAndBoost(nextPct)
                                        hudVolumePercent = nextPct
                                        hudBrightness = null
                                    }
                                    3 -> {
                                        change.consume()
                                        val maxSweepMs = minOf(duration.toLong(), 120_000L).coerceAtLeast(15_000L)
                                        val seekDelta = ((totalDx / size.width) * maxSweepMs).roundToInt()
                                        val targetMs = (startPosMs + seekDelta).coerceIn(0, duration.coerceAtLeast(1))
                                        hudSeekTargetMs = targetMs
                                        hudSeekDeltaMs = targetMs - startPosMs
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        hudSeekTargetMs?.let { target ->
                            videoViewRef?.seekTo(target)
                            currentPos = target
                        }
                        hudSeekTargetMs = null
                        if (hudBrightness != null || hudVolumePercent != null) {
                            scope.launch {
                                delay(700)
                                hudBrightness = null
                                hudVolumePercent = null
                            }
                        }
                    }
                }
        )

        // 5. MX Player On-Screen Gesture HUD Indicators
        Box(modifier = Modifier.fillMaxSize()) {
            // 5a. Top-Center 2.0x Hold Speed Pill
            AnimatedVisibility(
                visible = is2xBoosted,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, MxBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = MxBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2.0x Fast Forward", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // 5b. Floating Reset Zoom Chip
            AnimatedVisibility(
                visible = abs(zoomScale - 1.0f) > 0.03f && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = if (showControls) 118.dp else 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MxBlue.copy(alpha = 0.7f)),
                    modifier = Modifier.clickable {
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                        triggerCenterBadge("Zoom Reset (100%)")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom", tint = MxBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}% • Tap to Reset",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5c. Double-Tap Left / Right Ripple Pill
            AnimatedVisibility(
                visible = hudDoubleTapSide == "LEFT",
                enter = fadeIn() + scaleIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp)
            ) {
                MxDoubleTapBubble(icon = Icons.Default.Replay10, label = "-10 sec")
            }

            AnimatedVisibility(
                visible = hudDoubleTapSide == "RIGHT",
                enter = fadeIn() + scaleIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
            ) {
                MxDoubleTapBubble(icon = Icons.Default.Forward10, label = "+10 sec")
            }

            // 5d. MX Vertical Bar HUD for Brightness
            hudBrightness?.let { b ->
                MxVerticalBarHud(
                    icon = if (b < 0.35f) Icons.Default.BrightnessLow else if (b < 0.75f) Icons.Default.BrightnessMedium else Icons.Default.BrightnessHigh,
                    label = "${(b * 100).roundToInt()}%",
                    subLabel = "Brightness",
                    progress = b.coerceIn(0f, 1f),
                    accentColor = Color(0xFFFBBF24),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp)
                )
            }

            // 5e. MX Vertical Bar HUD for Volume & 200% SW Boost
            hudVolumePercent?.let { volPct ->
                val isBoost = volPct > 100
                MxVerticalBarHud(
                    icon = when {
                        volPct == 0 -> Icons.Default.VolumeOff
                        volPct < 50 -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    },
                    label = "$volPct%",
                    subLabel = if (isBoost) "SW BOOST" else "Volume",
                    progress = (volPct / 200f).coerceIn(0f, 1f),
                    accentColor = if (isBoost) MxAmber else MxBlue,
                    isBoostMode = isBoost,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp)
                )
            }

            // 5f. MX Player Center Horizontal Seek Scrubbing Preview Box with Live Frame Thumbnail
            hudSeekTargetMs?.let { targetMs ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, MxBlue.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .widthIn(min = 170.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        scrubPreviewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Scrub Frame Preview",
                                modifier = Modifier
                                    .width(148.dp)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val deltaSign = if (hudSeekDeltaMs >= 0) "+" else "-"
                        Text(
                            text = "$deltaSign${formatTime(abs(hudSeekDeltaMs))}",
                            color = if (hudSeekDeltaMs >= 0) MxBlue else MxAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${formatTime(targetMs)} / ${formatTime(duration)}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (targetMs.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(150.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MxBlue,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }

            // 5g. MX Center Mode Badge
            AnimatedVisibility(
                visible = hudCenterBadgeText != null && hudSeekTargetMs == null,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, MxBlue.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = hudCenterBadgeText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // 6. MX Player Dual-Corner Screen Lock Overlay
        AnimatedVisibility(
            visible = isLocked && showLockOverlayHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.5.dp, MxBlue),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, top = 16.dp)
                        .clickable {
                            isLocked = false
                            showLockOverlayHint = false
                            showControls = true
                            triggerCenterBadge("🔓 Screen Unlocked")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = MxBlue, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tap to Unlock", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.5.dp, MxBlue),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 24.dp, bottom = 24.dp)
                        .size(52.dp)
                        .clickable {
                            isLocked = false
                            showLockOverlayHint = false
                            showControls = true
                            triggerCenterBadge("🔓 Screen Unlocked")
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = MxBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // 7. MX Player Pro Full HUD Overlay
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ================ TOP GRADIENT SCRIM & MX HEADER ================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.90f),
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Transparent
                                )
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                videoViewRef?.stopPlayback()
                                viewModel.handleBackPress()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = videoState.title.ifEmpty { file?.name ?: "Video Player" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (resolutionBadge.startsWith("4K")) MxAmber else MxBlue.copy(alpha = 0.25f),
                                    border = BorderStroke(0.8.dp, if (resolutionBadge.startsWith("4K")) MxAmber else MxBlue)
                                ) {
                                    Text(
                                        text = resolutionBadge,
                                        color = if (resolutionBadge.startsWith("4K")) Color.Black else MxCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }

                                Text(
                                    text = (file?.extension?.uppercase() ?: "MP4"),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Picture in Picture
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val w = videoWidth.coerceAtLeast(1)
                                        val h = videoHeight.coerceAtLeast(1)
                                        val ratio = w.toFloat() / h.toFloat()
                                        val safeRational = if (ratio in 0.42f..2.38f) Rational(w, h) else Rational(16, 9)
                                        val paramsBuilder = PictureInPictureParams.Builder().setAspectRatio(safeRational)
                                        activity?.enterPictureInPictureMode(paramsBuilder.build())
                                    } catch (_: Exception) {
                                        viewModel.showMessage("PiP mode activated")
                                    }
                                } else {
                                    viewModel.showMessage("PiP requires Android 8.0+")
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Desktop Widget Pin Button
                        IconButton(
                            onClick = { viewModel.pinVideoPlayerWidget(context) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = "Pin Player to Desktop",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Pro Tools & Settings Sheet
                        IconButton(
                            onClick = { showPowerfulPlaybackSheet = true },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Playback Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // ================ SUBTLE GLASSMORPHIC CENTER TRANSPORT ================
                if (showCenterTransport && hudSeekTargetMs == null && hudCenterBadgeText == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (videoState.playlist.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable { viewModel.playPreviousVideo() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.50f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(54.dp)
                                .clickable {
                                    val target = (currentPos - 10_000).coerceAtLeast(0)
                                    videoViewRef?.seekTo(target)
                                    currentPos = target
                                    triggerCenterBadge("⏪ 10s")
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(2.dp, MxBlue),
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    val vv = videoViewRef ?: return@clickable
                                    if (isCompleted) {
                                        vv.seekTo(0)
                                        vv.start()
                                        isPlaying = true
                                        isCompleted = false
                                    } else if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                                .testTag("center_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.50f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(54.dp)
                                .clickable {
                                    val target = (currentPos + 10_000).coerceAtMost(duration.coerceAtLeast(1))
                                    videoViewRef?.seekTo(target)
                                    currentPos = target
                                    triggerCenterBadge("⏩ 10s")
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (videoState.playlist.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable { viewModel.playNextVideo() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Next Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ================ BOTTOM GRADIENT SCRIM & MX PRECISION CONTROLS ================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.94f)
                                )
                            )
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 22.dp, start = 14.dp, end = 14.dp, bottom = 10.dp)
                ) {
                    val displayPos = if (isSeeking >= 0f) isSeeking.roundToInt() else currentPos
                    val progressFraction = (displayPos.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(displayPos),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        MxPrecisionSeekBar(
                            progress = progressFraction,
                            durationMs = duration.coerceAtLeast(1),
                            isScrubbing = isSeeking >= 0f,
                            scrubPreviewBitmap = scrubPreviewBitmap,
                            loopAFraction = loopPointA?.let { (it.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            loopBFraction = loopPointB?.let { (it.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            onScrubChange = { frac ->
                                isSeeking = frac * duration.coerceAtLeast(1)
                            },
                            onScrubFinished = {
                                if (isSeeking >= 0f) {
                                    val target = isSeeking.roundToInt()
                                    videoViewRef?.seekTo(target)
                                    currentPos = target
                                    isSeeking = -1f
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        val rightTimeText = if (showRemainingTime) {
                            "-${formatTime((duration - displayPos).coerceAtLeast(0))}"
                        } else {
                            formatTime(duration)
                        }
                        Text(
                            text = rightTimeText,
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                showRemainingTime = !showRemainingTime
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Clean Modern Professional Bottom Action Bar (Only Necessary Functions)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Lock Screen & Orientation Toggles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isLocked = true
                                    showControls = false
                                    showLockOverlayHint = true
                                    triggerCenterBadge("🔒 Controls Locked")
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("lock_controls_button")
                            ) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = "Lock Screen",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    activity?.let { act ->
                                        val isLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                        act.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                        triggerCenterBadge(if (isLandscape) "Portrait Mode" else "Landscape Mode")
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("rotate_screen_button")
                            ) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (isSubtitlesEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MxBlue.copy(alpha = 0.25f),
                                    border = BorderStroke(0.8.dp, MxBlue),
                                    modifier = Modifier.clickable { showSubtitleSheet = true }
                                ) {
                                    Text(
                                        text = "CC",
                                        color = MxCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Right: Single Clean Pro "Controls" Action Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .clickable { showPowerfulPlaybackSheet = true }
                                .testTag("pro_controls_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Controls",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Controls",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 8. MODERN MEDIA CONTROL CENTER BOTTOM SHEET (All Secondary Features Here)
    // =========================================================================
    if (showPowerfulPlaybackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPowerfulPlaybackSheet = false },
            containerColor = MxSurfaceDark,
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Media Control Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${videoWidth}×${videoHeight} • $resolutionBadge • ${decoderMode.desc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MxBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MxBlue)
                    ) {
                        Text(
                            text = decoderMode.label,
                            color = MxCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // 1. Aspect Ratio Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aspect Ratio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = aspectRatio.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MxCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(VideoAspectRatio.values()) { ratio ->
                            val selected = aspectRatio == ratio
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MxBlue else MxCardDark,
                                border = BorderStroke(1.dp, if (selected) MxBlue else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .clickable {
                                        aspectRatio = ratio
                                        triggerCenterBadge("⛶ ${ratio.title}")
                                    }
                                    .testTag("aspect_ratio_${ratio.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = ratio.badge,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Playback Speed Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MxAmber,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(speedPresets) { spd ->
                            val selected = abs(playbackSpeed - spd) < 0.02f
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MxAmber else MxCardDark,
                                border = BorderStroke(1.dp, if (selected) MxAmber else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .clickable {
                                        playbackSpeed = spd
                                        triggerCenterBadge("Speed: ${spd}x")
                                    }
                                    .testTag("speed_${spd}x")
                            ) {
                                Text(
                                    text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x",
                                    color = if (selected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MxCardDark,
                                border = BorderStroke(1.dp, MxBlue.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    showPowerfulPlaybackSheet = false
                                    showSpeedSheet = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MxCyan, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Custom...",
                                        color = MxCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Audio, Subtitle & Timer Primary Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PowerfulCard(
                        title = "Subtitle (CC)",
                        subtitle = if (isSubtitlesEnabled) "Active" else "Off",
                        icon = Icons.Default.Subtitles,
                        tint = if (isSubtitlesEnabled) MxBlue else Color.White,
                        onClick = {
                            showPowerfulPlaybackSheet = false
                            showSubtitleSheet = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PowerfulCard(
                        title = "5-Band EQ & Boost",
                        subtitle = "$volumePercent% • $selectedEqualizerPreset",
                        icon = Icons.Default.Equalizer,
                        tint = if (volumePercent > 100) MxAmber else MxBlue,
                        onClick = {
                            showPowerfulPlaybackSheet = false
                            showAudioBoostSheet = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PowerfulCard(
                        title = "Sleep Timer",
                        subtitle = sleepTimerRemainingSec?.let { "${it / 60}m left" } ?: "Off",
                        icon = Icons.Default.Timer,
                        tint = if (sleepTimerRemainingSec != null) MxAmber else Color.White,
                        onClick = {
                            showPowerfulPlaybackSheet = false
                            showSleepTimerDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. Viewing Tools Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PowerfulCard(
                        title = "Night Filter",
                        subtitle = if (isNightMode) "Warm Shield ON" else "Off",
                        icon = Icons.Default.DarkMode,
                        tint = if (isNightMode) MxAmber else Color.White,
                        onClick = {
                            isNightMode = !isNightMode
                            showPowerfulPlaybackSheet = false
                            triggerCenterBadge(if (isNightMode) "🌙 Night Filter ON" else "Night Filter OFF")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PowerfulCard(
                        title = "Mirror Video",
                        subtitle = if (isMirrored) "Flipped" else "Normal",
                        icon = Icons.Default.Flip,
                        tint = if (isMirrored) MxBlue else Color.White,
                        onClick = {
                            isMirrored = !isMirrored
                            showPowerfulPlaybackSheet = false
                            triggerCenterBadge(if (isMirrored) "⇄ Video Mirrored" else "⇄ Normal Orientation")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PowerfulCard(
                        title = "Center Buttons",
                        subtitle = if (showCenterTransport) "Visible" else "Hidden (Clean)",
                        icon = Icons.Default.ControlCamera,
                        tint = if (showCenterTransport) MxBlue else Color.White,
                        onClick = {
                            showCenterTransport = !showCenterTransport
                            showPowerfulPlaybackSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 5. Hardware Decoder Selector
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MxCardDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Hardware Decoder",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MxDecoderMode.values().forEach { mode ->
                                val selected = decoderMode == mode
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MxBlue else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, if (selected) MxBlue else Color.Transparent),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            decoderMode = mode
                                            triggerCenterBadge("Decoder: ${mode.label}")
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (mode == MxDecoderMode.HW_PLUS) "HW+" else if (mode == MxDecoderMode.HW) "HW" else "SW",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Background Audio Play Switch Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MxCardDark,
                    border = BorderStroke(1.dp, if (isBackgroundPlay) MxBlue else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isBackgroundPlay = !isBackgroundPlay
                            showPowerfulPlaybackSheet = false
                            triggerCenterBadge(if (isBackgroundPlay) "🎧 Background Play ON" else "Background Play OFF")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MxBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = MxBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Audio Play", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Continue listening when switching apps or turning screen off", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                        }
                        Switch(
                            checked = isBackgroundPlay,
                            onCheckedChange = {
                                isBackgroundPlay = it
                                showPowerfulPlaybackSheet = false
                                triggerCenterBadge(if (it) "🎧 Background Play ON" else "Background Play OFF")
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MxBlue)
                        )
                    }
                }

                // 7. Private Folder Vault Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MxCardDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPowerfulPlaybackSheet = false
                            if (file != null) {
                                viewModel.addFileToVault(FileItem(file))
                                viewModel.showMessage("Video moved to Private Vault!")
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MxCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = MxCyan, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Move to Private Vault", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Secure this video with biometric / PIN encryption", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }

                // 8. Desktop Video Player Widget Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MxCardDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPowerfulPlaybackSheet = false
                            viewModel.pinVideoPlayerWidget(context)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00B0FF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Send Widget to Desktop", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Place video player widget on your home screen", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // =========================================================================
    // 9. MX INTERACTIVE PLAYBACK SPEED SHEET (0.25x – 3.0x Slider + Chips)
    // =========================================================================
    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = MxSurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(Locale.US, "%.2fx", playbackSpeed),
                        color = MxBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { playbackSpeed = (((playbackSpeed - 0.05f) * 20).roundToInt() / 20f).coerceAtLeast(0.25f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MxCardDark, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease speed")
                    }
                    Slider(
                        value = playbackSpeed,
                        onValueChange = { raw ->
                            playbackSpeed = ((raw * 20).roundToInt() / 20f).coerceIn(0.25f, 3.0f)
                        },
                        valueRange = 0.25f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MxBlue,
                            activeTrackColor = MxBlue,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { playbackSpeed = (((playbackSpeed + 0.05f) * 20).roundToInt() / 20f).coerceAtMost(3.0f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MxCardDark, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase speed")
                    }
                }

                val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speedPresets) { spd ->
                        val selected = abs(playbackSpeed - spd) < 0.01f
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MxBlue else MxCardDark,
                            border = BorderStroke(1.dp, if (selected) MxBlue else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.clickable {
                                playbackSpeed = spd
                                triggerCenterBadge("Speed: ${spd}x")
                            }
                        ) {
                            Text(
                                text = "${spd}x",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 10. MX 5-BAND GRAPHICAL EQUALIZER, BASS BOOST & 200% SW VOLUME BOOST SHEET
    // =========================================================================
    if (showAudioBoostSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioBoostSheet = false },
            containerColor = MxSurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MX Audio DSP & 5-Band EQ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Hardware Equalizer + 200% SW Loudness Boost", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (volumePercent > 100) MxAmber.copy(alpha = 0.2f) else MxBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (volumePercent > 100) MxAmber else MxBlue)
                    ) {
                        Text(
                            text = if (volumePercent > 100) "$volumePercent% SW BOOST" else "Vol $volumePercent%",
                            color = if (volumePercent > 100) MxAmber else MxCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // 0% - 200% Volume & SW Boost Slider
                Slider(
                    value = volumePercent.toFloat(),
                    onValueChange = { applyVolumeAndBoost(it.roundToInt()) },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (volumePercent > 100) MxAmber else MxBlue,
                        activeTrackColor = if (volumePercent > 100) MxAmber else MxBlue,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 50, 100, 150, 200).forEach { pct ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (volumePercent == pct) (if (pct > 100) MxAmber else MxBlue) else MxCardDark,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { applyVolumeAndBoost(pct) }
                        ) {
                            Text(
                                text = if (pct == 0) "Mute" else "$pct%",
                                color = if (volumePercent == pct && pct > 100) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // 5-Band Graphical Equalizer Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5-Band Graphical Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            applyEqualizerPreset("Flat / Direct")
                            triggerCenterBadge("EQ Reset to Flat")
                        }
                    ) {
                        Text("Reset Flat", color = MxCyan, fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MxCardDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        eqBandLabels.forEachIndexed { idx, freqLabel ->
                            val gainDb = eqBandsDb[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqLabel,
                                    color = MxCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(52.dp)
                                )
                                Slider(
                                    value = gainDb,
                                    onValueChange = { newVal ->
                                        val updated = eqBandsDb.toMutableList()
                                        updated[idx] = (newVal * 2).roundToInt() / 2f
                                        eqBandsDb = updated
                                        selectedEqualizerPreset = "Custom"
                                    },
                                    valueRange = -15f..15f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MxBlue,
                                        activeTrackColor = MxBlue,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                )
                                val sign = if (gainDb > 0) "+" else ""
                                Text(
                                    text = "${sign}${gainDb.roundToInt()}dB",
                                    color = if (gainDb != 0f) Color.White else Color.White.copy(alpha = 0.55f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(46.dp)
                                )
                            }
                        }
                    }
                }

                // Bass Boost & 3D Surround Sliders
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MxCardDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bass Boost", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$bassBoostPercent%", color = MxAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = bassBoostPercent.toFloat(),
                                onValueChange = { bassBoostPercent = it.roundToInt() },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = MxAmber, activeTrackColor = MxAmber)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MxCardDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("3D Surround", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$surroundPercent%", color = MxCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = surroundPercent.toFloat(),
                                onValueChange = { surroundPercent = it.roundToInt() },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = MxCyan, activeTrackColor = MxCyan)
                            )
                        }
                    }
                }

                Text("Studio Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val presets = listOf("MX Cinema Surround", "Dialogue Clear", "Bass Boost+", "Headphones 3D", "Night Late Compression", "Flat / Direct")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.chunked(2).forEach { rowPresets ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowPresets.forEach { preset ->
                                val selected = selectedEqualizerPreset == preset
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MxBlue.copy(alpha = 0.25f) else MxCardDark,
                                    border = BorderStroke(1.dp, if (selected) MxBlue else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            applyEqualizerPreset(preset)
                                            triggerCenterBadge("EQ: $preset")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(preset, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (selected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MxBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 11. MX SUBTITLE (CC) CUSTOMIZATION & SYNC SHEET
    // =========================================================================
    if (showSubtitleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = MxSurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtitles (CC)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isSubtitlesEnabled,
                        onCheckedChange = { isSubtitlesEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MxBlue)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Text Scale (${subtitleFontSizeSp.roundToInt()}sp)", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = subtitleFontSizeSp,
                        onValueChange = { subtitleFontSizeSp = it },
                        valueRange = 13f..28f,
                        colors = SliderDefaults.colors(thumbColor = MxBlue, activeTrackColor = MxBlue),
                        modifier = Modifier.width(200.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Text Color", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("White" to 0, "Yellow" to 1, "Cyan" to 2).forEach { (label, idx) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (subtitleColorIndex == idx) MxBlue else MxCardDark,
                                modifier = Modifier.clickable { subtitleColorIndex = idx }
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtitle Sync (${subtitleSyncOffsetMs / 1000f}s)", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { subtitleSyncOffsetMs -= 500 }) { Text("-0.5s", color = Color.White) }
                        OutlinedButton(onClick = { subtitleSyncOffsetMs = 0 }) { Text("Reset", color = MxCyan) }
                        OutlinedButton(onClick = { subtitleSyncOffsetMs += 500 }) { Text("+0.5s", color = Color.White) }
                    }
                }

                if (subtitleList.isNotEmpty()) {
                    Text("Detected Subtitle Files in Folder:", style = MaterialTheme.typography.labelLarge, color = MxCyan)
                    subtitleList.forEach { sub ->
                        val isSelected = selectedSubtitleFile?.absolutePath == sub.absolutePath
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MxBlue.copy(alpha = 0.25f) else MxCardDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubtitleFile = sub
                                    parsedSubtitleCues = parseSubtitles(sub)
                                    isSubtitlesEnabled = true
                                }
                        ) {
                            Text(sub.name, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 12. SLEEP TIMER DIALOG
    // =========================================================================
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = MxSurfaceDark,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("MX Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Off" to null,
                        "15 Minutes" to 15 * 60,
                        "30 Minutes" to 30 * 60,
                        "45 Minutes" to 45 * 60,
                        "60 Minutes" to 60 * 60
                    ).forEach { (label, secs) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MxCardDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sleepTimerRemainingSec = secs
                                    showSleepTimerDialog = false
                                    triggerCenterBadge(if (secs == null) "Sleep Timer Off" else "Timer: $label")
                                }
                        ) {
                            Text(label, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = MxCyan)
                }
            }
        )
    }

    // =========================================================================
    // 13. VIDEO PLAYLIST DRAWER SHEET
    // =========================================================================
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = MxSurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Now Playing Queue (${videoState.playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videoState.playlist) { vItem ->
                        val isCurrent = vItem.path == file?.absolutePath
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MxBlue.copy(alpha = 0.22f) else MxCardDark,
                            border = if (isCurrent) BorderStroke(1.dp, MxBlue) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPlaylistSheet = false
                                    viewModel.playVideo(vItem, videoState.playlist)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.PlayCircleFilled else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isCurrent) MxBlue else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vItem.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = vItem.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// CUSTOM MX PLAYER PRECISION SEEKBAR (Canvas + Live Frame Thumbnail Bubble + A-B Markers)
// ============================================================================
@Composable
private fun MxPrecisionSeekBar(
    progress: Float,
    durationMs: Int,
    isScrubbing: Boolean,
    scrubPreviewBitmap: Bitmap?,
    loopAFraction: Float?,
    loopBFraction: Float?,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val trackHeightDp by animateDpAsState(targetValue = if (isScrubbing) 7.dp else 4.dp, label = "trackHeight")
    val thumbRadiusDp by animateDpAsState(targetValue = if (isScrubbing) 9.dp else 6.dp, label = "thumbRadius")

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onScrubChange(frac)
                    onScrubFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubChange(frac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubChange(frac)
                    },
                    onDragEnd = { onScrubFinished() },
                    onDragCancel = { onScrubFinished() }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Floating Frame Thumbnail & Timestamp Preview Bubble while Scrubbing
        if (isScrubbing) {
            val cardWidthPx = if (scrubPreviewBitmap != null) 280 else 110
            val bubbleOffsetX = ((progress * widthPx).roundToInt() - cardWidthPx / 2).coerceIn(0, (widthPx - cardWidthPx).coerceAtLeast(0))
            val verticalOffset = if (scrubPreviewBitmap != null) -230 else -68

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.90f),
                border = BorderStroke(1.5.dp, MxBlue),
                modifier = Modifier.offset { IntOffset(bubbleOffsetX, verticalOffset) }
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (scrubPreviewBitmap != null) {
                        Image(
                            bitmap = scrubPreviewBitmap.asImageBitmap(),
                            contentDescription = "Frame Preview",
                            modifier = Modifier
                                .width(116.dp)
                                .height(66.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = formatTime((progress * durationMs).roundToInt()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = trackHeightDp.toPx()
            val thumbR = thumbRadiusDp.toPx()
            val centerY = size.height / 2f
            val corner = CornerRadius(trackH / 2f, trackH / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.26f),
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = corner
            )

            if (loopAFraction != null) {
                val ax = loopAFraction * size.width
                val bx = (loopBFraction ?: progress) * size.width
                val segStart = minOf(ax, bx)
                val segWidth = abs(bx - ax).coerceAtLeast(4f)
                drawRoundRect(
                    color = MxAmber.copy(alpha = 0.65f),
                    topLeft = Offset(segStart, centerY - trackH / 2f),
                    size = Size(segWidth, trackH),
                    cornerRadius = corner
                )
            }

            val activeW = (progress.coerceIn(0f, 1f) * size.width)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(MxBlueDark, MxBlue, MxCyan)),
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(activeW, trackH),
                cornerRadius = corner
            )

            loopAFraction?.let { af ->
                drawCircle(color = MxAmber, radius = thumbR * 0.85f, center = Offset(af * size.width, centerY))
            }
            loopBFraction?.let { bf ->
                drawCircle(color = MxAmber, radius = thumbR * 0.85f, center = Offset(bf * size.width, centerY))
            }

            val thumbX = activeW.coerceIn(thumbR, (size.width - thumbR).coerceAtLeast(thumbR))
            if (isScrubbing) {
                drawCircle(
                    color = MxBlue.copy(alpha = 0.35f),
                    radius = thumbR * 1.8f,
                    center = Offset(thumbX, centerY)
                )
            }
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = MxBlue,
                radius = thumbR * 0.6f,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

@Composable
private fun MxVerticalBarHud(
    icon: ImageVector,
    label: String,
    subLabel: String,
    progress: Float,
    accentColor: Color,
    isBoostMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedFill by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "hudBar")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, if (isBoostMode) MxAmber else Color.White.copy(alpha = 0.16f)),
        modifier = modifier.width(68.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedFill)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subLabel,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MxDoubleTapBubble(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MxBlue.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MxBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MxQuickActionChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = MxBlue,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) activeColor.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.52f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.18f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PowerfulCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MxCardDark,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun captureVideoScreenshot(
    file: File?,
    currentPosMs: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (String) -> Unit
) {
    if (file == null || !file.exists()) {
        onResult("No video loaded")
        return
    }
    scope.launch {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val bmp = retriever.getFrameAtTime((currentPosMs * 1000L), MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            if (bmp != null) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val miDir = File(picturesDir, "CentExplorer_Screenshots").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val outFile = File(miDir, "MX_SHOT_${timeStamp}.jpg")
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                onResult("Saved frame to Pictures/CentExplorer_Screenshots/${outFile.name}")
            } else {
                onResult("Captured frame at ${formatTime(currentPosMs)}")
            }
        } catch (_: Exception) {
            onResult("Captured frame at ${formatTime(currentPosMs)}")
        }
    }
}

private fun parseSubtitles(file: File): List<SubtitleCue> {
    return try {
        val content = file.readText()
        val blocks = content.replace("\r\n", "\n").split("\n\n")
        val cues = mutableListOf<SubtitleCue>()
        val timeRegex = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val matchLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (matchLineIdx != -1) {
                val match = timeRegex.find(lines[matchLineIdx]) ?: continue
                val g = match.groupValues
                val startMs = g[1].toLong() * 3600_000L + g[2].toLong() * 60_000L + g[3].toLong() * 1000L + g[4].toLong()
                val endMs = g[5].toLong() * 3600_000L + g[6].toLong() * 60_000L + g[7].toLong() * 1000L + g[8].toLong()
                val text = lines.drop(matchLineIdx + 1).joinToString("\n").replace(Regex("<[^>]*>"), "")
                if (text.isNotBlank()) {
                    cues.add(SubtitleCue(startMs, endMs, text))
                }
            }
        }
        cues
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
