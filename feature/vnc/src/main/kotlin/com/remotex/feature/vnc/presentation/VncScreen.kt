package com.remotex.feature.vnc.presentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.wifi.WifiManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncInputMode
import com.remotex.feature.vnc.domain.VncScaleMode
import com.remotex.feature.vnc.domain.VncSessionState
import com.remotex.feature.vnc.input.KeySymMapper
import com.remotex.feature.vnc.quality.VncQualityMode
import com.remotex.feature.vnc.screenshot.VncScreenshotSaver
import kotlinx.coroutines.delay

@Composable
fun VncScreen(
    viewModel: VncViewModel,
    title: String,
    onBack: () -> Unit,
    showAudioControl: Boolean = false,
    audioPlaying: Boolean = false,
    audioConnecting: Boolean = false,
    audioMessage: String? = null,
    onAudioToggle: () -> Unit = {},
    showWatchControl: Boolean = false,
    watchActive: Boolean = false,
    watchConnecting: Boolean = false,
    watchMessage: String? = null,
    onWatchToggle: () -> Unit = {},
    watchContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.sessionState.collectAsState()
    val frame by viewModel.frame.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val scaleMode by viewModel.scaleMode.collectAsState()
    val qualityMode by viewModel.qualityMode.collectAsState()
    val performanceStats by viewModel.performanceStats.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hiddenText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var armedModifiers by remember { mutableStateOf(emptySet<Int>()) }
    var fullscreen by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsEpoch by remember { mutableIntStateOf(0) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var surfaceView by remember { mutableStateOf<VncSurfaceView?>(null) }
    var requestedOrientation by rememberSaveable {
        mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    fun keepControlsVisible() {
        controlsVisible = true
        controlsEpoch += 1
    }

    fun closeSession() {
        viewModel.disconnect()
        onBack()
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.disconnect() }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(activity, requestedOrientation) {
        activity?.requestedOrientation = requestedOrientation
        onDispose { }
    }

    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (fullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null && fullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val connected = state is VncSessionState.Connected
    DisposableEffect(context, connected) {
        if (!connected) return@DisposableEffect onDispose { }
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RemoteX:VNC")
        wifiLock?.setReferenceCounted(false)
        runCatching { wifiLock?.acquire() }
        onDispose { runCatching { if (wifiLock?.isHeld == true) wifiLock.release() } }
    }

    LaunchedEffect(connected) {
        if (connected) keepControlsVisible()
    }

    LaunchedEffect(controlsVisible, controlsEpoch, connected, qualityMenuExpanded) {
        if (controlsVisible && connected && !qualityMenuExpanded) {
            delay(3_500)
            if (!qualityMenuExpanded) controlsVisible = false
        }
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        delay(2_500)
        if (statusMessage == message) statusMessage = null
    }

    LaunchedEffect(audioMessage) {
        if (!audioMessage.isNullOrBlank()) statusMessage = audioMessage
    }

    LaunchedEffect(watchMessage) {
        if (!watchMessage.isNullOrBlank()) statusMessage = watchMessage
    }

    LaunchedEffect(viewModel, context) {
        viewModel.remoteClipboard.collect { text ->
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("RemoteX", text))
                statusMessage = "Clipboard remote diterima"
            }
        }
    }

    fun dispatchKey(keysym: Int) {
        armedModifiers.forEach { viewModel.send(VncInputEvent.Key(it, true)) }
        viewModel.send(VncInputEvent.Key(keysym, true))
        viewModel.send(VncInputEvent.Key(keysym, false))
        armedModifiers.toList().asReversed().forEach { viewModel.send(VncInputEvent.Key(it, false)) }
        armedModifiers = emptySet()
    }

    fun toggleModifier(keysym: Int) {
        armedModifiers = if (keysym in armedModifiers) armedModifiers - keysym else armedModifiers + keysym
    }

    fun cycleScaleMode() {
        viewModel.setScaleMode(
            when (scaleMode) {
                VncScaleMode.FIT_SCREEN -> VncScaleMode.FILL_SCREEN
                VncScaleMode.FILL_SCREEN -> VncScaleMode.ORIGINAL_SIZE
                VncScaleMode.ORIGINAL_SIZE -> VncScaleMode.STRETCH
                VncScaleMode.STRETCH -> VncScaleMode.FIT_SCREEN
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val keysym = KeySymMapper.fromAndroid(event.nativeKeyEvent) ?: return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) dispatchKey(keysym)
                true
            },
    ) {
        AndroidView(
            factory = { ctx ->
                VncSurfaceView(ctx).apply {
                    onInput = viewModel::send
                    onKeyboardRequested = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    surfaceView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.inputMode = inputMode
                view.scaleMode = scaleMode
                frame?.let(view::setFrame)
            },
        )

        if (watchActive || watchConnecting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                watchContent?.invoke()
                if (watchConnecting) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }

        when (val current = state) {
            VncSessionState.Idle,
            VncSessionState.Connecting,
            -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is VncSessionState.Reconnecting -> StatusSurface(
                text = "Menyambung ulang ${current.attempt}/3…",
                modifier = Modifier.align(Alignment.Center),
            )

            is VncSessionState.Failed -> Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.reason)
                    Button(onClick = viewModel::reconnectNow) { Text("Sambungkan ulang") }
                }
            }

            else -> Unit
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 52.dp, top = 8.dp),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { keepControlsVisible(); closeSession() }) {
                        Text("←", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ModifierButton("Ctrl", KeySymMapper.CTRL_L in armedModifiers) {
                        keepControlsVisible(); toggleModifier(KeySymMapper.CTRL_L)
                    }
                    ModifierButton("Alt", KeySymMapper.ALT_L in armedModifiers) {
                        keepControlsVisible(); toggleModifier(KeySymMapper.ALT_L)
                    }
                    ModifierButton("Shift", KeySymMapper.SHIFT_L in armedModifiers) {
                        keepControlsVisible(); toggleModifier(KeySymMapper.SHIFT_L)
                    }
                    ModifierButton("Super", KeySymMapper.SUPER_L in armedModifiers) {
                        keepControlsVisible(); toggleModifier(KeySymMapper.SUPER_L)
                    }
                    ToolButton("Tab") { keepControlsVisible(); dispatchKey(KeySymMapper.TAB) }
                    ToolButton("Enter") { keepControlsVisible(); dispatchKey(KeySymMapper.RETURN) }
                    ToolButton("Esc") { keepControlsVisible(); dispatchKey(KeySymMapper.ESC) }
                    ToolButton(if (inputMode == VncInputMode.TRACKPAD) "Trackpad" else "Sentuh") {
                        keepControlsVisible()
                        viewModel.setInputMode(
                            if (inputMode == VncInputMode.TRACKPAD) VncInputMode.DIRECT_TOUCH else VncInputMode.TRACKPAD,
                        )
                    }
                    ToolButton(
                        when (scaleMode) {
                            VncScaleMode.FIT_SCREEN -> "Pas Layar"
                            VncScaleMode.FILL_SCREEN -> "Isi Layar"
                            VncScaleMode.ORIGINAL_SIZE -> "Asli"
                            VncScaleMode.STRETCH -> "Regang"
                        },
                    ) {
                        keepControlsVisible()
                        cycleScaleMode()
                    }
                    Box {
                        ToolButton("Kualitas: ${qualityMode.label()}") {
                            keepControlsVisible()
                            qualityMenuExpanded = true
                        }
                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false },
                        ) {
                            VncQualityMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (mode == qualityMode) "✓ ${mode.label()}" else mode.label(),
                                        )
                                    },
                                    onClick = {
                                        viewModel.setQualityMode(mode)
                                        qualityMenuExpanded = false
                                        keepControlsVisible()
                                        statusMessage = "Kualitas: ${mode.label()}"
                                    },
                                )
                            }
                        }
                    }
                    if (showWatchControl) {
                        ToolButton(
                            when {
                                watchConnecting -> "Menonton…"
                                watchActive -> "Keluar Menonton"
                                else -> "Menonton"
                            },
                        ) {
                            keepControlsVisible()
                            onWatchToggle()
                        }
                    }
                    if (showAudioControl && !watchActive && !watchConnecting) {
                        ToolButton(
                            when {
                                audioConnecting -> "Audio…"
                                audioPlaying -> "Bisukan"
                                else -> "Suara"
                            },
                        ) {
                            keepControlsVisible()
                            onAudioToggle()
                        }
                    }
                    ToolButton(if (fullscreen) "Jendela" else "Fullscreen") {
                        keepControlsVisible()
                        fullscreen = !fullscreen
                    }
                    ToolButton("Putar") {
                        keepControlsVisible()
                        requestedOrientation =
                            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        statusMessage =
                            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
                                "Orientasi potret"
                            } else {
                                "Orientasi lanskap"
                            }
                    }
                    ToolButton("Keyboard") {
                        keepControlsVisible()
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    ToolButton("Clipboard") {
                        keepControlsVisible()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (text.isNotEmpty()) {
                            viewModel.send(VncInputEvent.Clipboard(text))
                            statusMessage = "Clipboard dikirim"
                        }
                    }
                    ToolButton("Screenshot") {
                        keepControlsVisible()
                        val bitmap = surfaceView?.snapshotBitmap()
                        if (bitmap != null) {
                            statusMessage = VncScreenshotSaver(context).save(bitmap).fold(
                                onSuccess = { "Tersimpan: $it" },
                                onFailure = { "Screenshot gagal: ${it.message}" },
                            )
                            bitmap.recycle()
                        }
                    }
                    ToolButton("Putuskan") { keepControlsVisible(); closeSession() }
                }
            }
        }

        VncControlsHandle(
            controlsVisible = controlsVisible,
            onClick = {
                controlsVisible = !controlsVisible
                if (controlsVisible) controlsEpoch += 1
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )


        if (connected) {
            val indicator = if (watchActive || watchConnecting) {
                if (watchActive) "Mode Menonton • 720p30" else "Menyiapkan Mode Menonton…"
            } else {
                val fpsText = if (performanceStats.fps > 0) "${performanceStats.fps} FPS" else "-- FPS"
                val modeText = if (qualityMode == VncQualityMode.AUTO) {
                    "Otomatis · ${performanceStats.activeQuality.label()}"
                } else {
                    performanceStats.activeQuality.label()
                }
                "$fpsText • $modeText"
            }
            PerformanceIndicator(
                text = indicator,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }

        statusMessage?.let {
            StatusSurface(
                text = it,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }

        BasicTextField(
            value = hiddenText,
            onValueChange = { value ->
                val newText = if (value.startsWith(hiddenText)) value.removePrefix(hiddenText) else value
                newText.forEach { char ->
                    when (char) {
                        '\n', '\r' -> dispatchKey(KeySymMapper.RETURN)
                        else -> dispatchKey(char.code)
                    }
                }
                hiddenText = value.filterNot { it == '\n' || it == '\r' }.takeLast(32)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(
                onDone = { dispatchKey(KeySymMapper.RETURN) },
                onGo = { dispatchKey(KeySymMapper.RETURN) },
                onNext = { dispatchKey(KeySymMapper.RETURN) },
                onSearch = { dispatchKey(KeySymMapper.RETURN) },
                onSend = { dispatchKey(KeySymMapper.RETURN) },
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .height(1.dp)
                .focusRequester(focusRequester),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        )
    }
}

@Composable
private fun VncControlsHandle(
    controlsVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        shadowElevation = 6.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = if (controlsVisible) "Sembunyikan kontrol" else "Tampilkan kontrol",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StatusSurface(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.72f),
    ) {
        Text(
            text,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}


@Composable
private fun PerformanceIndicator(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.48f),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun VncQualityMode.label(): String = when (this) {
    VncQualityMode.AUTO -> "Otomatis"
    VncQualityMode.PERFORMANCE -> "Performa"
    VncQualityMode.BALANCED -> "Seimbang"
    VncQualityMode.HIGH -> "Tinggi"
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ModifierButton(label: String, armed: Boolean, onClick: () -> Unit) {
    if (armed) FilledTonalButton(onClick = onClick) { Text(label) }
    else TextButton(onClick = onClick) { Text(label) }
}
