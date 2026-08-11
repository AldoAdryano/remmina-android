package com.remotex.feature.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidPcmPlayer(context: Context) : PcmPlayer {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    private var track: AudioTrack? = null
    private var focusGranted = false
    private var targetPrebufferBytes = 0
    private var prebufferedBytes = 0
    private var playbackStarted = false

    override suspend fun start(initialDelayMs: Int) = withContext(Dispatchers.IO) {
        stopInternal()
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(
            RemoteAudioCommand.RATE_HZ,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Perangkat Android tidak mendukung format audio remote" }

        targetPrebufferBytes = AudioSyncPolicy.pcmBytesForDelay(
            delayMs = initialDelayMs,
            rateHz = RemoteAudioCommand.RATE_HZ,
            channels = RemoteAudioCommand.CHANNELS,
            bytesPerSample = BYTES_PER_SAMPLE,
        )
        val safetyBytes = AudioSyncPolicy.pcmBytesForDelay(
            delayMs = BUFFER_HEADROOM_MS,
            rateHz = RemoteAudioCommand.RATE_HZ,
            channels = RemoteAudioCommand.CHANNELS,
            bytesPerSample = BYTES_PER_SAMPLE,
        )
        val bufferSize = maxOf(minBuffer, targetPrebufferBytes + safetyBytes)

        focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        require(focusGranted) { "Audio focus Android tidak tersedia" }

        val created = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RemoteAudioCommand.RATE_HZ)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        require(created.state == AudioTrack.STATE_INITIALIZED) {
            created.release()
            "AudioTrack gagal diinisialisasi"
        }
        track = created
        prebufferedBytes = 0
        playbackStarted = targetPrebufferBytes == 0
        if (playbackStarted) created.play()
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext
        val current = track ?: return@withContext
        var offset = 0
        while (offset < bytes.size) {
            val written = current.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) error("AudioTrack write gagal: $written")
            if (written == 0) break
            offset += written
            if (!playbackStarted) {
                prebufferedBytes += written
                if (prebufferedBytes >= targetPrebufferBytes) {
                    current.play()
                    playbackStarted = true
                }
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal()
    }

    private fun stopInternal() {
        track?.let { current ->
            runCatching { if (current.playState == AudioTrack.PLAYSTATE_PLAYING) current.stop() }
            runCatching { current.flush() }
            runCatching { current.release() }
        }
        track = null
        targetPrebufferBytes = 0
        prebufferedBytes = 0
        playbackStarted = false
        if (focusGranted) runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        focusGranted = false
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_HEADROOM_MS = 80
    }
}
