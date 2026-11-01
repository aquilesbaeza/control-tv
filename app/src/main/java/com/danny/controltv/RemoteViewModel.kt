package com.danny.controltv

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.concurrent.Executors

/** Codigos de tecla de Android (KeyEvent.KEYCODE_*) que se mandan a la TV. */
object Keys {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val PLAY_PAUSE = 85
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val MUTE = 164
    const val SETTINGS = 176
    const val TV_INPUT = 178
}

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class TvStatus(val state: ConnState, val message: String)

/** component = "paquete/actividad" cuando se conoce; si es null se abre con monkey. */
data class TvApp(val pkg: String, val component: String?, val label: String)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("control_tv", Context.MODE_PRIVATE)

    // Un solo hilo para todo lo que habla con la TV: los comandos salen en orden.
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val queue = Channel<Cmd>(Channel.UNLIMITED)
    private var dadb: Dadb? = null

    private val _host = MutableStateFlow(prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST)
    val host: StateFlow<String> = _host.asStateFlow()

    private val _status = MutableStateFlow(TvStatus(ConnState.DISCONNECTED, "Sin conectar"))
    val status: StateFlow<TvStatus> = _status.asStateFlow()

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    private val _loadingApps = MutableStateFlow(false)
    val loadingApps: StateFlow<Boolean> = _loadingApps.asStateFlow()

    private sealed interface Cmd {
        data class Key(val code: Int, val long: Boolean) : Cmd
        data class Shell(val command: String) : Cmd
    }

    init {
        viewModelScope.launch(io) { processQueue() }
    }

    // ---------- Acciones que usa la pantalla ----------

    fun key(code: Int, long: Boolean = false) {
        queue.trySend(Cmd.Key(code, long))
    }

    /** "input text" solo acepta ASCII: se quitan tildes (a, n) y lo demas se descarta. */
    fun sendText(text: String) {
        val clean = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it.code in 32..126 }
        if (clean.isEmpty()) return
        val arg = clean.replace("'", "'\\''").replace(" ", "%s")
        queue.trySend(Cmd.Shell("input text '$arg'"))
    }

    fun launch(app: TvApp) {
        val cmd = if (app.component != null) {
            "am start -n ${app.component}"
        } else {
            "monkey -p ${app.pkg} -c android.intent.category.LEANBACK_LAUNCHER 1"
        }
        queue.trySend(Cmd.Shell(cmd))
    }

    fun connect(force: Boolean = false) {
        viewModelScope.launch(io) {
            if (force) closeQuietly()
            runShell("echo ok")
            if (_status.value.state == ConnState.CONNECTED && _apps.value.isEmpty()) {
                loadAppsInternal()
            }
        }
    }

    fun setHost(newHost: String) {
        val h = newHost.trim()
        if (h.isEmpty()) return
        prefs.edit().putString(KEY_HOST, h).putBoolean(KEY_AUTHORIZED, false).apply()
        _host.value = h
        _apps.value = emptyList()
        connect(force = true)
    }

    fun refreshApps() {
        viewModelScope.launch(io) { loadAppsInternal() }
    }

    // ---------- Cola de comandos ----------

    private suspend fun processQueue() {
        for (first in queue) {
            // Junta lo que se acumulo (por ejemplo, al deslizar rapido en el touchpad)
            // y lo manda en un solo comando: "input keyevent 20 20 20".
            val batch = mutableListOf(first)
            while (batch.size < 40) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch += next
            }
            val commands = mutableListOf<String>()
            val keys = mutableListOf<Int>()
            fun flushKeys() {
                if (keys.isNotEmpty()) {
                    commands += "input keyevent " + keys.joinToString(" ")
                    keys.clear()
                }
            }
            for (c in batch) {
                when (c) {
                    is Cmd.Key -> if (c.long) {
                        flushKeys()
                        commands += "input keyevent --longpress ${c.code}"
                    } else {
                        keys += c.code
                    }
                    is Cmd.Shell -> {
                        flushKeys()
                        commands += c.command
                    }
                }
            }
            flushKeys()
            runShell(commands.joinToString("; "))
        }
    }

    // ---------- Conexion ADB ----------

    private fun openConnection(): Dadb {
        val ctx = getApplication<Application>()
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        val keyPair = AdbKeyPair.read(priv, pub)

        val authorized = prefs.getBoolean(KEY_AUTHORIZED, false)
        _status.value = TvStatus(
            ConnState.CONNECTING,
            if (authorized) "Conectando a ${_host.value}…"
            else "Mirá la TV: si pregunta, marcá \"Permitir siempre\" y OK"
        )
        // La primera vez se espera hasta 2 min a que aceptes el permiso en la TV.
        val readTimeout = if (authorized) 10_000 else 120_000
        val d = Dadb.create(_host.value, PORT, keyPair, CONNECT_TIMEOUT, readTimeout)
        try {
            d.shell("echo ok")
        } catch (e: Exception) {
            runCatching { d.close() }
            throw e
        }
        prefs.edit().putBoolean(KEY_AUTHORIZED, true).apply()
        dadb = d
        return d
    }

    /** Corre un comando; si la conexion se cayo, reconecta una vez. Devuelve null si fallo. */
    private fun runShell(command: String): String? {
        for (attempt in 0..1) {
            try {
                val d = dadb ?: openConnection()
                val out = d.shell(command).output
                if (_status.value.state != ConnState.CONNECTED) {
                    _status.value = TvStatus(ConnState.CONNECTED, "Conectado a ${_host.value}")
                }
                return out
            } catch (e: Exception) {
                closeQuietly()
                if (attempt == 1) _status.value = TvStatus(ConnState.ERROR, errorText(e))
            }
        }
        return null
    }

    private fun errorText(e: Exception): String = when (e) {
        is SocketTimeoutException ->
            "La TV no respondió (${_host.value}). ¿Está encendida y en el mismo Wi-Fi?"
        is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            "No se pudo conectar a ${_host.value}. Revisá la IP y el Wi-Fi."
        else -> "Error: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun closeQuietly() {
        runCatching { dadb?.close() }
        dadb = null
    }

    // ---------- Apps instaladas en la TV ----------

    private fun loadAppsInternal() {
        _loadingApps.value = true
        try {
            val out = runShell(
                "cmd package query-activities --brief " +
                    "-a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"
            ) ?: return
            val rx = Regex("""^\s*([A-Za-z][\w.]*)/([\w.$]+)\s*$""")
            var list = out.lineSequence()
                .mapNotNull { rx.find(it) }
                .map {
                    val pkg = it.groupValues[1]
                    TvApp(pkg, "$pkg/${it.groupValues[2]}", labelFor(pkg))
                }
                .filter { it.pkg !in HIDDEN }
                .distinctBy { it.pkg }
                .toList()

            if (list.isEmpty()) {
                // Plan B: solo las apps instaladas por el usuario.
                val pk = runShell("pm list packages -3") ?: ""
                list = pk.lineSequence()
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() && it !in HIDDEN }
                    .map { TvApp(it, null, labelFor(it)) }
                    .toList()
            }

            _apps.value = list.sortedWith(
                compareBy<TvApp>(
                    { KNOWN_ORDER.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.label.lowercase() }
                )
            )
        } finally {
            _loadingApps.value = false
        }
    }

    override fun onCleared() {
        closeQuietly()
        io.close()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_HOST = "192.168.100.29"
        private const val PORT = 5555
        private const val CONNECT_TIMEOUT = 4_000
        private const val KEY_HOST = "host"
        private const val KEY_AUTHORIZED = "authorized"

        private val HIDDEN = setOf("com.gathin.tvhome", "com.danny.controltv")

        private val KNOWN = linkedMapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.netflix.ninja" to "Netflix",
            "com.disney.disneyplus" to "Disney+",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.wbd.stream" to "Max",
            "com.hbo.hbonow" to "HBO Max",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "com.spotify.tv.android" to "Spotify",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "tv.pluto.android" to "Pluto TV",
            "com.crunchyroll.crunchyroid" to "Crunchyroll",
            "com.plexapp.android" to "Plex",
            "org.xbmc.kodi" to "Kodi",
            "com.android.vending" to "Play Store",
            "com.mitv.livetv.ci_mediatek" to "TV en vivo",
            "com.android.tv.settings" to "Ajustes",
        )
        private val KNOWN_ORDER = KNOWN.keys.toList()

        private val NOISE = setOf(
            "com", "org", "net", "tv", "android", "google", "app", "apps", "mitv", "xiaomi",
            "atv", "androidtv", "mobile", "livingroom", "main", "ninja", "leanback"
        )

        fun labelFor(pkg: String): String {
            KNOWN[pkg]?.let { return it }
            val part = pkg.split('.').firstOrNull { it.length > 1 && it.lowercase() !in NOISE }
                ?: pkg.substringAfterLast('.')
            return part.replaceFirstChar { it.uppercase() }
        }
    }
}
