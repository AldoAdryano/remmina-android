package com.remotex.feature.vnc.presentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.wifi.WifiManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.remotex.feature.vnc.screenshot.VncScreenshotSaver

@Composable
fun VncScreen(
    viewModel: VncViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.sessionState.collectAsState()
    val frame by viewModel.frame.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val scaleMode by viewModel.scaleMode.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hiddenText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var armedModifiers by remember { mutableStateOf(emptySet<Int>()) }
    var fullscreen by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val keysym = KeySymMapper.fromAndroid(event.nativeKeyEvent) ?: return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) dispatchKey(keysym)
                true
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onBack) { Text("← $title") }
            ModifierButton("Ctrl", KeySymMapper.CTRL_L in armedModifiers) { toggleModifier(KeySymMapper.CTRL_L) }
            ModifierButton("Alt", KeySymMapper.ALT_L in armedModifiers) { toggleModifier(KeySymMapper.ALT_L) }
            ModifierButton("Shift", KeySymMapper.SHIFT_L in armedModifiers) { toggleModifier(KeySymMapper.SHIFT_L) }
            ModifierButton("Super", KeySymMapper.SUPER_L in armedModifiers) { toggleModifier(KeySymMapper.SUPER_L) }
            ToolButton("Tab") { dispatchKey(KeySymMapper.TAB) }
            ToolButton("Esc") { dispatchKey(KeySymMapper.ESC) }
            ToolButton(if (inputMode == VncInputMode.TRACKPAD) "Trackpad" else "Sentuh") {
                viewModel.setInputMode(
                    if (inputMode == VncInputMode.TRACKPAD) VncInputMode.DIRECT_TOUCH else VncInputMode.TRACKPAD,
                )
            }
            ToolButton(
                when (scaleMode) {
                    VncScaleMode.FIT_SCREEN -> "Pas Layar"
                    VncScaleMode.ORIGINAL_SIZE -> "Asli"
                    VncScaleMode.STRETCH -> "Regang"
                },
            ) {
                viewModel.setScaleMode(
                    when (scaleMode) {
                        VncScaleMode.FIT_SCREEN -> VncScaleMode.ORIGINAL_SIZE
                        VncScaleMode.ORIGINAL_SIZE -> VncScaleMode.STRETCH
                        VncScaleMode.STRETCH -> VncScaleMode.FIT_SCREEN
                    },
                )
            }
            ToolButton(if (fullscreen) "Keluar Fullscreen" else "Fullscreen") { fullscreen = !fullscreen }
            ToolButton("Keyboard") {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            ToolButton("Kirim Clipboard") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isNotEmpty()) viewModel.send(VncInputEvent.Clipboard(text))
            }
            ToolButton("Screenshot") {
                val current = frame
                if (current != null) {
                    statusMessage = VncScreenshotSaver(context).save(current).fold(
                        onSuccess = { "Tersimpan: $it" },
                        onFailure = { "Screenshot gagal: ${it.message}" },
                    )
                }
            }
            ToolButton("Putuskan") {
                viewModel.disconnect()
                onBack()
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    VncSurfaceView(ctx).apply {
                        onInput = viewModel::send
                        onKeyboardRequested = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.inputMode = inputMode
                    view.scaleMode = scaleMode
                    frame?.let(view::setFrame)
                },
            )
            when (val current = state) {
                VncSessionState.Idle,
                VncSessionState.Connecting,
                -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is VncSessionState.Reconnecting -> Text(
                    "Menyambung ulang ${current.attempt}/3…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )

                is VncSessionState.Failed -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.reason, color = Color.White)
                    Button(onClick = viewModel::reconnectNow) { Text("Sambungkan ulang") }
                }

                else -> Unit
            }
            statusMessage?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
            BasicTextField(
                value = hiddenText,
                onValueChange = { value ->
                    val newText = if (value.startsWith(hiddenText)) value.removePrefix(hiddenText) else value
                    newText.forEach { char -> dispatchKey(char.code) }
                    hiddenText = value.takeLast(32)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .height(1.dp)
                    .focusRequester(focusRequester),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
            )
        }
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ModifierButton(label: String, armed: Boolean, onClick: () -> Unit) {
    if (armed) FilledTonalButton(onClick = onClick) { Text(label) }
    else androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}
