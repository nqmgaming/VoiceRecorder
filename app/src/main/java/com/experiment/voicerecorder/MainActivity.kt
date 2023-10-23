package com.experiment.voicerecorder

import android.Manifest
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.experiment.voicerecorder.ui.MainScreen
import com.experiment.voicerecorder.ui.VoiceRecorderNavigation
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.recorder.core.designsystem.theme.VoiceRecorderTheme
import com.recorder.service.PlayerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber

@ExperimentalPermissionsApi
@ExperimentalMaterialApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser
        get() = if (browserFuture.isDone) browserFuture.get() else null
    private val mediaItems = mutableListOf<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS),
            0
        )
        enableEdgeToEdge()
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ),
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(
                            Color.TRANSPARENT,
                        )
                    } else {
                        SystemBarStyle.light(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        )
                    }
                )
            }
            VoiceRecorderTheme {
                val navState = rememberNavController()
                MainScreen {
                    Box(modifier = Modifier) {
                        var isVoicePlaying by remember() {
                            mutableStateOf(false)
                        }
                        var progress by remember() {
                            mutableLongStateOf(0)
                        }
                        var voiceDuration by remember() {
                            mutableLongStateOf(0)
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event.targetState == Lifecycle.State.STARTED) {
                                    browserFuture.addListener(
                                        {
                                            browserFuture.get().apply {
                                                isVoicePlaying = isPlaying
                                                progress = currentPosition
                                                addListener(
                                                    object : Player.Listener {
                                                        override fun onEvents(
                                                            player: Player,
                                                            events: Player.Events,
                                                        ) {
                                                            super.onEvents(player, events)
                                                        }

                                                        override fun onPlaybackStateChanged(
                                                            playbackState: Int,
                                                        ) {
                                                            when (playbackState) {
                                                                Player.STATE_IDLE -> {
                                                                    isVoicePlaying = isPlaying
                                                                    voiceDuration = 0
                                                                }

                                                                Player.STATE_ENDED -> {
                                                                    isVoicePlaying = isPlaying
                                                                    progress = 0
                                                                    voiceDuration = 0
                                                                }

                                                                Player.STATE_BUFFERING -> {

                                                                }

                                                                Player.STATE_READY -> {
                                                                    voiceDuration = duration
                                                                    progress = currentPosition
                                                                }
                                                            }
                                                            super.onPlaybackStateChanged(
                                                                playbackState
                                                            )
                                                        }

                                                        override fun onPlayWhenReadyChanged(
                                                            playWhenReady: Boolean,
                                                            reason: Int,
                                                        ) {
                                                            Timber.e("play when ready:$playWhenReady")
                                                            isVoicePlaying = isPlaying
                                                            super.onPlayWhenReadyChanged(
                                                                playWhenReady,
                                                                reason
                                                            )
                                                        }

                                                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                                                            isVoicePlaying = isPlaying
                                                            super.onIsPlayingChanged(isPlaying)
                                                            Timber.e("is playing chnage:$isPlaying")
                                                        }


                                                        override fun onPlayerError(error: PlaybackException) {
                                                            super.onPlayerError(error)
                                                            Timber.e(error.message)
                                                            browser?.stop()
                                                        }

                                                    })
                                            }

                                        }, MoreExecutors.directExecutor()
                                    )
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }
                        LaunchedEffect(key1 = progress, isVoicePlaying) {
                            if (isVoicePlaying)
                                browser?.run {
                                    while (true) {
                                        delay(1_000)
                                        progress = currentPosition
                                    }
                                }
                        }
                        VoiceRecorderNavigation(
                            modifier = Modifier,
                            navController = navState,
                            isPlaying = isVoicePlaying,
                            onPlay = { index, voice ->
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(voice.title)
                                    .setIsPlayable(true).build()
                                val mediaitem = MediaItem.Builder()
                                    .setMediaMetadata(metadata)
                                    .setUri(voice.path)
                                    .setMediaId(voice.title)
                                    .build()
                                browser?.run {
                                    setMediaItem(mediaitem)
                                    play()
                                }
                            },
                            onStop = {
                                browser?.run {
                                    stop()
                                }
                            },
                            progress = progress.toFloat(),
                            duration = voiceDuration.toFloat(),
                            onProgressChange = { currentPosition ->
                                browser?.run {
                                    seekTo(currentPosition.toLong())
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlayerService::class.java)
        )
        browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()
    }

    override fun onStop() {
        super.onStop()
        MediaBrowser.releaseFuture(browserFuture)
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    VoiceRecorderTheme {

    }
}
