package com.example.btvideo.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.example.btvideo.R
import com.example.btvideo.bluetooth.BluetoothConnection
import com.example.btvideo.bluetooth.FrameType
import com.example.btvideo.bluetooth.Protocol
import com.example.btvideo.data.LocalStore
import com.example.btvideo.data.StoredVideo
import com.example.btvideo.model.SearchResult
import com.example.btvideo.model.VideoSourceMode
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClientActivity : Activity(), BluetoothConnection.Listener {

    private lateinit var status: TextView
    private lateinit var bufferStatus: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var resultsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var privateModeCheck: CheckBox
    private lateinit var audioOnlyCheck: CheckBox
    private lateinit var sourceRadioGroup: RadioGroup
    private lateinit var sourceHelpText: TextView
    private lateinit var videoContainer: FrameLayout
    private lateinit var videoView: VideoView
    private lateinit var connection: BluetoothConnection
    private lateinit var store: LocalStore

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var pairedDevices: List<BluetoothDevice> = emptyList()

    private var currentFile: File? = null
    private var currentOutput: FileOutputStream? = null
    private var currentBytes = 0L
    private var expectedBytes = 0L
    private var currentTitle = ""
    private var currentVideoId = ""
    private var currentMime = "video/mp4"
    private var transferStartedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        PermissionHelper.request(this)

        status = findViewById(R.id.clientStatus)
        bufferStatus = findViewById(R.id.bufferStatus)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        resultsContainer = findViewById(R.id.resultsContainer)
        searchInput = findViewById(R.id.searchInput)
        privateModeCheck = findViewById(R.id.privateModeCheck)
        audioOnlyCheck = findViewById(R.id.audioOnlyCheck)
        sourceRadioGroup = findViewById(R.id.sourceRadioGroup)
        sourceHelpText = findViewById(R.id.sourceHelpText)
        videoContainer = findViewById(R.id.videoContainer)

        setupVideoView()

        store = LocalStore(this)
        connection = BluetoothConnection(this, this)
        loadPairedDevices()

        findViewById<Button>(R.id.connectButton).setOnClickListener { connectSelectedDevice() }
        findViewById<Button>(R.id.searchButton).setOnClickListener { sendSearch() }
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistory() }
        findViewById<Button>(R.id.favoritesButton).setOnClickListener { showFavorites() }

        sourceRadioGroup.setOnCheckedChangeListener { _, _ -> updateSourceHelpText() }
        updateSourceHelpText()
    }

    private fun setupVideoView(): Unit {
        videoContainer.removeAllViews()
        videoContainer.setBackgroundColor(Color.BLACK)

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            setBackgroundColor(Color.BLACK)
            setZOrderOnTop(true)
            setMediaController(MediaController(this@ClientActivity))

            setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = false
                status.text = "Estado: reproduciendo $currentTitle"
                start()
            }

            setOnErrorListener { _, _, _ ->
                status.text = "Estado: formato de video no compatible con este dispositivo"
                Toast.makeText(
                    this@ClientActivity,
                    "Formato no compatible. Prueba otro video o usa baja calidad.",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
        }

        videoContainer.addView(videoView)
        videoView.bringToFront()
    }

    private fun selectedSourceMode(): VideoSourceMode {
        return when (sourceRadioGroup.checkedRadioButtonId) {
            R.id.youtubeExperimentalRadio -> VideoSourceMode.YOUTUBE_EXPERIMENTAL
            else -> VideoSourceMode.LOCAL
        }
    }

    private fun updateSourceHelpText(): Unit {
        sourceHelpText.text = when (selectedSourceMode()) {
            VideoSourceMode.LOCAL ->
                "Biblioteca del servidor: busca videos agregados en el celular servidor o videos demo incluidos."

            VideoSourceMode.YOUTUBE_EXPERIMENTAL ->
                "YouTube: el servidor realizará la búsqueda y preparará el video para enviarlo al cliente."
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices(): Unit {
        pairedDevices = adapter?.bondedDevices?.toList().orEmpty()
        val labels = pairedDevices.map { it.name ?: it.address }

        deviceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )

        if (labels.isEmpty()) {
            status.text = "Estado: empareja primero ambos celulares desde ajustes de Bluetooth"
        }
    }

    private fun connectSelectedDevice(): Unit {
        val index = deviceSpinner.selectedItemPosition

        if (index !in pairedDevices.indices) {
            Toast.makeText(this, "No hay dispositivo emparejado seleccionado", Toast.LENGTH_SHORT).show()
            return
        }

        connection.connect(pairedDevices[index])
    }

    private fun sendSearch(): Unit {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) return

        val sourceMode = selectedSourceMode()

        connection.send(
            FrameType.SEARCH_REQUEST,
            Protocol.searchRequest(query, sourceMode)
        )

        status.text = if (sourceMode == VideoSourceMode.LOCAL) {
            "Estado: buscando en biblioteca del servidor..."
        } else {
            "Estado: buscando en YouTube..."
        }
    }

    override fun onConnected(role: String): Unit = runOnUiThread {
        status.text = "Estado: conectado como $role"
        Toast.makeText(this, "Servidor conectado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected(reason: String): Unit = runOnUiThread {
        status.text = "Estado: desconectado. $reason"
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    override fun onFrame(type: FrameType, payload: ByteArray): Unit {
        when (type) {
            FrameType.SEARCH_RESPONSE -> showResults(Protocol.parseResults(payload))
            FrameType.VIDEO_META -> prepareVideo(payload)
            FrameType.VIDEO_CHUNK -> writeChunk(payload)
            FrameType.VIDEO_END -> finishVideo()
            FrameType.ERROR -> showError(payload)
            else -> Unit
        }
    }

    private fun showResults(results: List<SearchResult>): Unit = runOnUiThread {
        status.text = "Estado: resultados recibidos"
        resultsContainer.removeAllViews()

        addSectionTitle("Resultados")

        if (results.isEmpty()) {
            resultsContainer.addView(TextView(this).apply {
                text = "Sin resultados"
                setTextColor(themedTextColor())
            })
            return@runOnUiThread
        }

        results.forEach { item ->
            addSearchResultRow(item)
        }
    }

    private fun addSearchResultRow(item: SearchResult): Unit {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }

        val titleView = TextView(this).apply {
            val sourceText = when {
                item.source.contains("youtube", ignoreCase = true) -> "YouTube"
                item.source.contains("library", ignoreCase = true) -> "Biblioteca del servidor"
                item.source.contains("asset", ignoreCase = true) -> "Videos demo"
                item.source.contains("local", ignoreCase = true) -> "Biblioteca local"
                else -> item.source
            }

            val durationText = if (item.durationText.isBlank()) "" else " | ${item.durationText}"

            text = "${item.title}\nFuente: $sourceText$durationText"
            textSize = 16f
            setTextColor(themedTextColor())
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val play = Button(this).apply {
            text = "Reproducir"
            isEnabled = item.playable
            setOnClickListener {
                requestPlayback(item.id, sourceModeForSearchResult(item))
            }
        }

        val favorite = Button(this).apply {
            text = if (store.isFavorite(item.id)) "Quitar favorito" else "Favorito"

            setOnClickListener {
                val added = store.toggleFavorite(item.id, item.title)
                text = if (added) "Quitar favorito" else "Favorito"

                Toast.makeText(
                    this@ClientActivity,
                    if (added) "Agregado a favoritos" else "Quitado de favoritos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        actions.addView(play)
        actions.addView(favorite)

        row.addView(titleView)
        row.addView(actions)

        resultsContainer.addView(row)
    }

    private fun showHistory(): Unit {
        runOnUiThread {
            val items: List<StoredVideo> = store.getHistory()
            status.text = "Estado: mostrando historial"

            showStoredVideos(
                sectionTitle = "Historial",
                emptyText = "Aún no hay videos en el historial. Reproduce un video con modo privado apagado.",
                items = items,
                allowClear = true,
                onClear = {
                    store.clearHistory()
                    Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
                    showHistory()
                }
            )
        }
    }

    private fun showFavorites(): Unit {
        runOnUiThread {
            val items: List<StoredVideo> = store.getFavorites()
            status.text = "Estado: mostrando favoritos"

            showStoredVideos(
                sectionTitle = "Favoritos",
                emptyText = "Aún no hay favoritos. Usa el botón Favorito en un resultado de búsqueda.",
                items = items,
                allowClear = false,
                onClear = null
            )
        }
    }

    private fun showStoredVideos(
        sectionTitle: String,
        emptyText: String,
        items: List<StoredVideo>,
        allowClear: Boolean,
        onClear: (() -> Unit)?
    ): Unit {
        resultsContainer.removeAllViews()
        addSectionTitle(sectionTitle)

        if (items.isEmpty()) {
            resultsContainer.addView(TextView(this).apply {
                text = emptyText
                setTextColor(themedTextColor())
            })
            return
        }

        if (allowClear && onClear != null) {
            resultsContainer.addView(Button(this).apply {
                text = "Limpiar historial"
                setOnClickListener { onClear.invoke() }
            })
        }

        items.forEach { item ->
            addStoredVideoRow(item, sectionTitle)
        }
    }

    private fun addStoredVideoRow(item: StoredVideo, sectionTitle: String): Unit {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }

        val dateText = SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(item.timestamp))

        val titleView = TextView(this).apply {
            text = "${item.title}\n$dateText"
            textSize = 16f
            setTextColor(themedTextColor())
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val play = Button(this).apply {
            text = "Reproducir"
            setOnClickListener {
                requestPlayback(item.videoId, sourceModeForVideoId(item.videoId))
            }
        }

        val favorite = Button(this).apply {
            text = if (store.isFavorite(item.videoId)) "Quitar favorito" else "Favorito"

            setOnClickListener {
                val added = store.toggleFavorite(item.videoId, item.title)
                text = if (added) "Quitar favorito" else "Favorito"

                Toast.makeText(
                    this@ClientActivity,
                    if (added) "Agregado a favoritos" else "Quitado de favoritos",
                    Toast.LENGTH_SHORT
                ).show()

                if (sectionTitle == "Favoritos" && !added) {
                    showFavorites()
                }
            }
        }

        actions.addView(play)
        actions.addView(favorite)

        row.addView(titleView)
        row.addView(actions)

        resultsContainer.addView(row)
    }

    private fun requestPlayback(videoId: String, sourceMode: VideoSourceMode): Unit {
        connection.send(
            FrameType.PLAY_REQUEST,
            Protocol.playRequest(videoId, sourceMode, audioOnlyCheck.isChecked)
        )

        status.text = "Estado: solicitando video..."
        bufferStatus.text = "Buffer: esperando transferencia"
    }

    private fun sourceModeForSearchResult(item: SearchResult): VideoSourceMode {
        return if (
            item.source.contains("youtube", ignoreCase = true) ||
            isYoutubeIdOrUrl(item.id)
        ) {
            VideoSourceMode.YOUTUBE_EXPERIMENTAL
        } else {
            VideoSourceMode.LOCAL
        }
    }

    private fun sourceModeForVideoId(videoId: String): VideoSourceMode {
        return if (isYoutubeIdOrUrl(videoId)) {
            VideoSourceMode.YOUTUBE_EXPERIMENTAL
        } else {
            VideoSourceMode.LOCAL
        }
    }

    private fun isYoutubeIdOrUrl(value: String): Boolean {
        val cleaned = value.trim()

        return cleaned.contains("youtube.com", ignoreCase = true) ||
                cleaned.contains("youtu.be", ignoreCase = true)
    }

    private fun addSectionTitle(textValue: String): Unit {
        resultsContainer.addView(TextView(this).apply {
            text = textValue
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(themedTextColor())
            setPadding(0, 4, 0, 8)
        })
    }

    private fun themedTextColor(): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))

        return try {
            typedArray.getColor(0, Color.WHITE)
        } finally {
            typedArray.recycle()
        }
    }

    private fun prepareVideo(payload: ByteArray): Unit {
        val json = JSONObject(String(payload))

        currentVideoId = json.getString("videoId")
        currentTitle = json.getString("title")
        expectedBytes = json.getLong("totalBytes")
        currentMime = json.optString("mime", "video/mp4")
        currentBytes = 0
        transferStartedAt = System.currentTimeMillis()

        currentFile = File(
            cacheDir,
            "bt_${System.currentTimeMillis()}${extensionForMime(currentMime)}"
        )

        currentOutput = FileOutputStream(currentFile)

        runOnUiThread {
            bufferStatus.text = "Buffer: 0%"
            status.text = "Estado: recibiendo $currentTitle"
        }
    }

    private fun writeChunk(payload: ByteArray): Unit {
        currentOutput?.write(payload)
        currentBytes += payload.size

        val elapsedSeconds =
            ((System.currentTimeMillis() - transferStartedAt).coerceAtLeast(1)) / 1000.0

        val kbps = (currentBytes * 8.0 / 1000.0) / elapsedSeconds
        val percent = if (expectedBytes > 0) {
            (currentBytes * 100 / expectedBytes).toInt()
        } else {
            0
        }

        runOnUiThread {
            bufferStatus.text = String.format(
                Locale.US,
                "Buffer: %d%% | %.1f kbps",
                percent,
                kbps
            )
        }
    }

    private fun finishVideo(): Unit {
        currentOutput?.flush()
        currentOutput?.close()
        currentOutput = null

        val file = currentFile ?: return

        if (!privateModeCheck.isChecked) {
            store.addHistory(currentVideoId, currentTitle)
        }

        runOnUiThread {
            status.text = "Estado: reproducción lista"
            bufferStatus.text = "Buffer: 100%"

            setupVideoView()
            videoView.setVideoURI(Uri.fromFile(file))
            videoView.requestFocus()
        }
    }

    private fun extensionForMime(mime: String): String {
        return when {
            mime.contains("webm", ignoreCase = true) -> ".webm"
            mime.contains("quicktime", ignoreCase = true) -> ".mov"
            mime.contains("3gpp", ignoreCase = true) -> ".3gp"
            else -> ".mp4"
        }
    }

    private fun showError(payload: ByteArray): Unit = runOnUiThread {
        val message = JSONObject(String(payload)).optString("message", "Error desconocido")
        status.text = "Error: $message"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onLog(message: String): Unit = runOnUiThread {
        status.text = message
    }

    override fun onDestroy(): Unit {
        super.onDestroy()
        currentOutput?.close()
        connection.close()
        store.close()
    }
}