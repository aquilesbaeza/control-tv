@file:OptIn(ExperimentalMaterial3Api::class)

package com.danny.controltv

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Section(val label: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Filled.Gamepad),
    TOUCH("Touchpad", Icons.Filled.TouchApp),
    APPS("Apps", Icons.Filled.Apps),
    TEXT("Teclado", Icons.Filled.Keyboard),
}

@Composable
fun RemoteApp(vm: RemoteViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                status = status,
                onReconnect = { vm.connect(force = true) },
                onSettings = { showSettings = true },
                onPower = { vm.key(Keys.POWER) },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pantalla interna del Fold (abierto) = dos columnas; portada = pestañas abajo.
            if (maxWidth >= 600.dp) WideLayout(vm) else CompactLayout(vm)
        }
    }

    if (showSettings) {
        SettingsDialog(
            host = host,
            onDismiss = { showSettings = false },
            onSave = {
                vm.setHost(it)
                showSettings = false
            },
        )
    }
}

// ---------------------------------------------------------------- Layouts

@Composable
private fun CompactLayout(vm: RemoteViewModel) {
    var section by rememberSaveable { mutableStateOf(Section.CONTROL) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val m = Modifier.fillMaxSize()
            when (section) {
                Section.CONTROL -> ControlPanel(vm, m)
                Section.TOUCH -> TouchpadPanel(vm, m)
                Section.APPS -> AppsPanel(vm, m)
                Section.TEXT -> KeyboardPanel(vm, m)
            }
        }
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            Section.entries.forEach { s ->
                NavigationBarItem(
                    selected = section == s,
                    onClick = { section = s },
                    icon = { Icon(s.icon, contentDescription = null) },
                    label = { Text(s.label) },
                )
            }
        }
    }
}

@Composable
private fun WideLayout(vm: RemoteViewModel) {
    val side = listOf(Section.TOUCH, Section.APPS, Section.TEXT)
    var section by rememberSaveable { mutableStateOf(Section.TOUCH) }
    Row(Modifier.fillMaxSize()) {
        ControlPanel(
            vm,
            Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            TabRow(selectedTabIndex = side.indexOf(section).coerceAtLeast(0)) {
                side.forEach { s ->
                    Tab(
                        selected = section == s,
                        onClick = { section = s },
                        text = { Text(s.label) },
                        icon = { Icon(s.icon, contentDescription = null) },
                    )
                }
            }
            val m = Modifier.fillMaxSize()
            when (section) {
                Section.APPS -> AppsPanel(vm, m)
                Section.TEXT -> KeyboardPanel(vm, m)
                else -> TouchpadPanel(vm, m)
            }
        }
    }
}

// ---------------------------------------------------------------- Barra superior

@Composable
private fun TopBar(
    status: TvStatus,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
    onPower: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = when (status.state) {
            ConnState.CONNECTED -> Color(0xFF3FB950)
            ConnState.CONNECTING -> Color(0xFFD29922)
            ConnState.ERROR -> Color(0xFFF85149)
            ConnState.DISCONNECTED -> Color.Gray
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            status.message,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onReconnect) { Icon(Icons.Filled.Refresh, "Reconectar") }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "IP de la TV") }
        RemoteButton(
            onPress = onPower,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            container = Color(0xFFB3261E),
            contentColor = Color.White,
        ) { Icon(Icons.Filled.PowerSettingsNew, "Apagar") }
    }
}

// ---------------------------------------------------------------- Botón base

/**
 * Botón con vibración. repeat = true: se repite mientras lo mantenés (flechas, volumen).
 * Si no repite, se dispara al soltar; onLongPress se dispara a los 0.5 s.
 */
@Composable
private fun RemoteButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    repeat: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    val longPress by rememberUpdatedState(onLongPress)
    val pressedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Box(
        modifier
            .clip(shape)
            .background(if (pressed) pressedColor else container)
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    var longFired = false
                    var job: Job? = null
                    if (repeat) {
                        press()
                        job = scope.launch {
                            delay(400)
                            while (isActive) {
                                press()
                                delay(110)
                            }
                        }
                    } else if (longPress != null) {
                        job = scope.launch {
                            delay(500)
                            longFired = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            longPress?.invoke()
                        }
                    }
                    val up = waitForUpOrCancellation()
                    job?.cancel()
                    pressed = false
                    if (!repeat && !longFired && up != null) press()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    RemoteButton(onPress = onPress, modifier = modifier.height(58.dp), repeat = repeat) {
        Icon(icon, description, Modifier.size(28.dp))
    }
}

// ---------------------------------------------------------------- Control

@Composable
private fun ControlPanel(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val dpadSize = minOf(maxWidth - 48.dp, 300.dp).coerceAtLeast(190.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", Modifier.weight(1f)) { vm.key(Keys.BACK) }
                IconKey(Icons.Filled.Home, "Inicio", Modifier.weight(1f)) { vm.key(Keys.HOME) }
                IconKey(Icons.Filled.Tune, "Ajustes de la TV", Modifier.weight(1f)) { vm.key(Keys.SETTINGS) }
            }

            DPad(vm, dpadSize)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey(Icons.AutoMirrored.Filled.VolumeDown, "Bajar volumen", Modifier.weight(1f), repeat = true) { vm.key(Keys.VOLUME_DOWN) }
                IconKey(Icons.AutoMirrored.Filled.VolumeOff, "Silencio", Modifier.weight(1f)) { vm.key(Keys.MUTE) }
                IconKey(Icons.AutoMirrored.Filled.VolumeUp, "Subir volumen", Modifier.weight(1f), repeat = true) { vm.key(Keys.VOLUME_UP) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey(Icons.Filled.FastRewind, "Retroceder", Modifier.weight(1f), repeat = true) { vm.key(Keys.REWIND) }
                RemoteButton(
                    onPress = { vm.key(Keys.PLAY_PAUSE) },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                ) {
                    Row {
                        Icon(Icons.Filled.PlayArrow, "Reproducir", Modifier.size(24.dp))
                        Icon(Icons.Filled.Pause, "Pausa", Modifier.size(24.dp))
                    }
                }
                IconKey(Icons.Filled.FastForward, "Adelantar", Modifier.weight(1f), repeat = true) { vm.key(Keys.FAST_FORWARD) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteButton(
                    onPress = { vm.key(Keys.TV_INPUT) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Input, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Entradas")
                    }
                }
                RemoteButton(
                    onPress = { vm.key(Keys.MENU) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text("Menú") }
            }
            Text(
                "Los botones de volumen del celular también controlan la TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DPad(vm: RemoteViewModel, size: Dp) {
    val btn = size / 3
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val clear = Color.Transparent
        RemoteButton(
            onPress = { vm.key(Keys.UP) }, repeat = true, shape = CircleShape, container = clear,
            modifier = Modifier
                .size(btn)
                .align(Alignment.TopCenter),
        ) { Icon(Icons.Filled.KeyboardArrowUp, "Arriba", Modifier.size(44.dp)) }
        RemoteButton(
            onPress = { vm.key(Keys.DOWN) }, repeat = true, shape = CircleShape, container = clear,
            modifier = Modifier
                .size(btn)
                .align(Alignment.BottomCenter),
        ) { Icon(Icons.Filled.KeyboardArrowDown, "Abajo", Modifier.size(44.dp)) }
        RemoteButton(
            onPress = { vm.key(Keys.LEFT) }, repeat = true, shape = CircleShape, container = clear,
            modifier = Modifier
                .size(btn)
                .align(Alignment.CenterStart),
        ) { Icon(Icons.Filled.KeyboardArrowLeft, "Izquierda", Modifier.size(44.dp)) }
        RemoteButton(
            onPress = { vm.key(Keys.RIGHT) }, repeat = true, shape = CircleShape, container = clear,
            modifier = Modifier
                .size(btn)
                .align(Alignment.CenterEnd),
        ) { Icon(Icons.Filled.KeyboardArrowRight, "Derecha", Modifier.size(44.dp)) }
        RemoteButton(
            onPress = { vm.key(Keys.CENTER) },
            onLongPress = { vm.key(Keys.CENTER, long = true) },
            shape = CircleShape,
            container = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(btn)
                .align(Alignment.Center),
        ) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
    }
}

// ---------------------------------------------------------------- Touchpad

@Composable
private fun TouchpadPanel(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    val step = 46.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var acc = Offset.Zero
                        var moved = false
                        var lifted = false
                        var upTime = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            upTime = change.uptimeMillis
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            acc += change.positionChange()
                            if (!moved && acc.getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                // Cada 46 dp de recorrido = una flecha, en la dirección dominante.
                                while (abs(acc.x) >= step || abs(acc.y) >= step) {
                                    if (abs(acc.x) >= abs(acc.y)) {
                                        val right = acc.x > 0
                                        vm.key(if (right) Keys.RIGHT else Keys.LEFT)
                                        acc = Offset(acc.x - (if (right) step else -step), 0f)
                                    } else {
                                        val goDown = acc.y > 0
                                        vm.key(if (goDown) Keys.DOWN else Keys.UP)
                                        acc = Offset(0f, acc.y - (if (goDown) step else -step))
                                    }
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            change.consume()
                        }
                        if (lifted && !moved) {
                            if (upTime - down.uptimeMillis > 500) {
                                vm.key(Keys.CENTER, long = true)
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else {
                                vm.key(Keys.CENTER)
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val hint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.TouchApp, null, Modifier.size(52.dp), tint = hint)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Deslizá para moverte\nTocá = OK  ·  Mantené = OK largo",
                    textAlign = TextAlign.Center,
                    color = hint,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconKey(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", Modifier.weight(1f)) { vm.key(Keys.BACK) }
            IconKey(Icons.Filled.Home, "Inicio", Modifier.weight(1f)) { vm.key(Keys.HOME) }
            IconKey(Icons.Filled.PlayArrow, "Reproducir o pausar", Modifier.weight(1f)) { vm.key(Keys.PLAY_PAUSE) }
        }
    }
}

// ---------------------------------------------------------------- Apps

@Composable
private fun AppsPanel(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loadingApps.collectAsStateWithLifecycle()
    Column(modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Apps de la TV", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            IconButton(onClick = { vm.refreshApps() }) { Icon(Icons.Filled.Refresh, "Actualizar lista") }
        }
        if (apps.isEmpty() && !loading) {
            Text(
                "Todavía no hay apps. Conectate a la TV y tocá actualizar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(apps, key = { it.pkg }) { app ->
                AppTile(app) { vm.launch(app) }
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit) {
    val view = LocalView.current
    val color = remember(app.pkg) { tileColor(app.pkg) }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val TILE_COLORS = listOf(
    Color(0xFF5E35B1), Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF3949AB), Color(0xFFD81B60),
)

private fun tileColor(pkg: String): Color = when (pkg) {
    "com.google.android.youtube.tv", "com.google.android.youtube.tvkids",
    "com.google.android.youtube.tvmusic" -> Color(0xFFE62117)
    "com.netflix.ninja" -> Color(0xFFB20710)
    "com.disney.disneyplus" -> Color(0xFF113CCF)
    "com.amazon.amazonvideo.livingroom" -> Color(0xFF00A8E1)
    "com.wbd.stream", "com.hbo.hbonow" -> Color(0xFF002BE7)
    "com.spotify.tv.android" -> Color(0xFF1DB954)
    else -> TILE_COLORS[Math.floorMod(pkg.hashCode(), TILE_COLORS.size)]
}

// ---------------------------------------------------------------- Teclado

@Composable
private fun KeyboardPanel(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            vm.sendText(text)
            text = ""
        }
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Escribí aquí y aparece en la TV", style = MaterialTheme.typography.titleMedium)
        Text(
            "Primero entrá a un cuadro de texto en la TV (por ejemplo, el buscador de YouTube). " +
                "Las tildes y la ñ se mandan sin acento (á → a, ñ → n).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        Button(onClick = { send() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("Enviar a la TV")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconKey(Icons.AutoMirrored.Filled.Backspace, "Borrar", Modifier.weight(1f), repeat = true) { vm.key(Keys.DEL) }
            IconKey(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter", Modifier.weight(1f)) { vm.key(Keys.ENTER) }
        }
        OutlinedButton(onClick = { repeat(40) { vm.key(Keys.DEL) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Borrar todo en la TV")
        }
    }
}

// ---------------------------------------------------------------- Ajustes

@Composable
private fun SettingsDialog(host: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(host) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("IP de la TV") },
        text = {
            Column {
                Text(
                    "La misma IP que usás en los scripts (TV_HOST). El celular tiene que estar en el mismo Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Guardar y conectar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
