package com.recorder.feature.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Environment.DIRECTORY_RECORDINGS
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

const val DIRECTORY_NAME = "VoiceRecorder"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor() : ViewModel() {

    private lateinit var mediaRecorder: MediaRecorder
    private var fileName = mutableStateOf("")
    private var canAccessAppFolder = false
    private var _isRecording = MutableStateFlow(false)
    private var directoryName = ""
    private var _timerMillis = MutableStateFlow(0L)

    val timePattern = DateTimeFormatter.ofPattern("mm:ss")

    private val _formattedTimer = MutableStateFlow("")
    val formattedTimer = _timerMillis.map { elapsedTime ->
        LocalTime.ofNanoOfDay(elapsedTime*1000000).format(timePattern)

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1000L),
        initialValue = "00:00:00"
    )


    init {
        initializeAppSettings()
        _isRecording.flatMapLatest {
            startTimer(it)
        }.onEach { time ->
            _timerMillis.update { it + time }
            Timber.e(_timerMillis.value.toString())
        }.launchIn(viewModelScope)
    }

    fun onRecord(context: Context) {
        if (_isRecording.value) {
            stopRecordingAudio(
                onStopRecording = {
                    _isRecording.update { false }
                    _timerMillis.update { 0 }
                    Timber.e(_isRecording.value.toString())
                })
        } else {
            startRecordingAudio(
                context = context,
                onRecord = {
                    _isRecording.update { true }
                    Timber.e(_isRecording.value.toString())
                }
            )
        }
    }

    private fun startTimer(isRecording: Boolean): Flow<Long> = flow {
        var startMillis = System.currentTimeMillis()
        Timber.e(isRecording.toString())
        while (isRecording) {
            val currentMillis = System.currentTimeMillis()
            val elapsedTimeSinceStart =
                if (currentMillis > startMillis)
                    currentMillis - startMillis
                else
                    0L
            emit(elapsedTimeSinceStart)
            startMillis = System.currentTimeMillis()
            delay(100L)
        }
    }

    private fun resetTimer() {
        viewModelScope.launch {
            _timerMillis.update { 0 }
        }
    }

    private fun startRecordingAudio(context: Context, onRecord: () -> Unit) {
        val name = generateFileName()
        if (canAccessAppFolder) {
            val file = File(directoryName, name)
            fileName.value = file.path
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(fileName.value)
                try {
                    prepare()
                } catch (e: Exception) {
                    Timber.e("recorder can`t be prepared")
                }
                start()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = MediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(fileName.value)
                    try {
                        prepare()
                    } catch (e: Exception) {
                        Timber.e("recorder on android(S) can`t be prepared")
                    }
                    start()
                }
            }
            onRecord()
//            updateAppState(AppSate.Recording)
//            appState.value = VoiceRecorderState.STATE_RECORDING
//            recordingAllowed.value = false
//            playbackAllowed.value = false
//            isRecording.value = true
//            Timber.e("is recording: " + isRecording.value)
        } else {
            Timber.e("cannot access app dir")
        }
    }

    private fun stopRecordingAudio(onStopRecording: () -> Unit) {
//        timer.value = DEFAULT_RECORD_TIMER_VALUE
        mediaRecorder.apply {
            stop()
            release()
            onStopRecording()
            Timber.e("is recording: stop")
        }
    }

    private fun generateFileName(
        pattern: String = "yyMMdd_HHmmss",
        fileExt: String = ".m4a",
        local: Locale = Locale.getDefault(),
    ): String {
        val sdf = SimpleDateFormat(pattern, local)
        val date = sdf.format(Date())
        return "$date$fileExt"
    }

    private fun createStorageFolder() {
        val path = storagePath()
        val folderExists = File(path).exists()
        canAccessAppFolder = when {
            folderExists -> {
                Timber.e("$DIRECTORY_NAME exists")
                true
            }

            File(path, "/$DIRECTORY_NAME").mkdirs() -> {
                Timber.e("file created")
                true
            }

            else -> {
                Timber.e("something went wrong, no folder")
                false
            }
        }
    }

    private fun storagePath(): String {
        val path = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Environment.getExternalStoragePublicDirectory(DIRECTORY_RECORDINGS).path
            }

            else -> {
                Environment.getExternalStorageDirectory().path
            }
        }
        Timber.e(Environment.getExternalStorageDirectory().path)
        return path
    }

    private fun initializeAppSettings() {
        createStorageFolder()
        val rootPath = storagePath()
        directoryName = "$rootPath/$DIRECTORY_NAME"
    }
}