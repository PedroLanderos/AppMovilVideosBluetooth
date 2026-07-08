package com.example.btvideo.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.os.Bundle
import android.widget.*
import com.example.btvideo.R
import com.example.btvideo.bluetooth.BluetoothConnection
import com.example.btvideo.bluetooth.FrameType
import com.example.btvideo.bluetooth.Protocol
import com.example.btvideo.data.LocalStore
import com.example.btvideo.model.SearchResult
import com.example.btvideo.model.VideoSourceMode
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    private lateinit var sourceWarningText: TextView
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
        sourceWarningText = findViewById(R.id.sourceWarningText)
        videoContainer = findViewById(R.id.videoContainer)

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setMediaController(MediaController(this@ClientActivity))
        }
        videoContainer.addView(videoView)

        store = LocalStore(this)
        connection = BluetoothConnection(this, this)
        loadPairedDevices()

        findViewById<Button>(R.id.connectButton).setOnClickListener { connectSelectedDevice() }
        findViewById<Button>(R.id.searchButton).setOnClickListener { sendSearch() }

        sourceRadioGroup.setOnCheckedChangeListener { _, _ -> updateSourceHelpText() }
        updateSourceHelpText()
    }

    private fun selectedSourceMode(): VideoSourceMode = when (sourceRadioGroup.checkedRadioButtonId) {
        R.id.youtubeExperimentalRadio -> VideoSourceMode.YOUTUBE_EXPERIMENTAL
        else -> VideoSourceMode.LOCAL
    }

    private fun updateSourceHelpText() {
        sourceWarningText.text = when (selectedSourceMode()) {
            VideoSourceMode.LOCAL -> "Biblioteca servidor: busca videos agregados en el celular servidor o videos demo incluidos."
            VideoSourceMode.YOUTUBE_EXPERIMENTAL -> "YouTube experimental: el servidor buscará/descargará con yt-dlp. Úsalo solo con contenido autorizado o con permiso."
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        pairedDevices = adapter?.bondedDevices?.toList().orEmpty()
        val labels = pairedDevices.map { it.name ?: it.address }
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        if (labels.isEmpty()) status.text = "Estado: empareja primero ambos celulares desde ajustes de Bluetooth"
    }

    private fun connectSelectedDevice() {
        val index = deviceSpinner.selectedItemPosition
        if (index !in pairedDevices.indices) {
            Toast.makeText(this, "No hay dispositivo emparejado seleccionado", Toast.LENGTH_SHORT).show()
            return
        }
        connection.connect(pairedDevices[index])
    }

    private fun sendSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) return
        val sourceMode = selectedSourceMode()
        connection.send(FrameType.SEARCH_REQUEST, Protocol.searchRequest(query, sourceMode))
        status.text = if (sourceMode == VideoSourceMode.LOCAL) "Estado: buscando en biblioteca del servidor..." else "Estado: buscando en ${sourceMode.wireName}..."
    }

    override fun onConnected(role: String) = runOnUiThread {
        status.text = "Estado: conectado como $role"
        Toast.makeText(this, "Servidor conectado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected(reason: String) = runOnUiThread {
        status.text = "Estado: desconectado. $reason"
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    override fun onFrame(type: FrameType, payload: ByteArray) {
        when (type) {
            FrameType.SEARCH_RESPONSE -> showResults(Protocol.parseResults(payload))
            FrameType.VIDEO_META -> prepareVideo(payload)
            FrameType.VIDEO_CHUNK -> writeChunk(payload)
            FrameType.VIDEO_END -> finishVideo()
            FrameType.ERROR -> showError(payload)
            else -> Unit
        }
    }

    private fun showResults(results: List<SearchResult>) = runOnUiThread {
        status.text = "Estado: resultados recibidos"
        resultsContainer.removeAllViews()
        if (results.isEmpty()) {
            resultsContainer.addView(TextView(this).apply { text = "Sin resultados" })
            return@runOnUiThread
        }
        results.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }
            val title = TextView(this).apply {
                val secureText = if (item.verified) "verificada" else "no verificada"
                text = "${item.title}\nFuente: ${item.source} | $secureText ${item.durationText}"
                textSize = 16f
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val play = Button(this).apply {
                text = "Reproducir"
                isEnabled = item.playable
                setOnClickListener {
                    val sourceMode = if (item.source.contains("youtube", ignoreCase = true)) {
                        VideoSourceMode.YOUTUBE_EXPERIMENTAL
                    } else {
                        VideoSourceMode.LOCAL
                    }
                    connection.send(FrameType.PLAY_REQUEST, Protocol.playRequest(item.id, sourceMode, audioOnlyCheck.isChecked))
                    status.text = "Estado: solicitando video..."
                    bufferStatus.text = "Buffer: esperando transferencia"
                    if (!item.verified) {
                        Toast.makeText(this@ClientActivity, "Contenido no verificado/experimental", Toast.LENGTH_LONG).show()
                    }
                }
            }
            val favorite = Button(this).apply {
                text = "Favorito"
                setOnClickListener {
                    val added = store.toggleFavorite(item.id, item.title)
                    Toast.makeText(this@ClientActivity, if (added) "Agregado a favoritos" else "Quitado de favoritos", Toast.LENGTH_SHORT).show()
                }
            }
            actions.addView(play)
            actions.addView(favorite)
            row.addView(title)
            row.addView(actions)
            resultsContainer.addView(row)
        }
    }

    private fun prepareVideo(payload: ByteArray) {
        val json = JSONObject(String(payload))
        currentVideoId = json.getString("videoId")
        currentTitle = json.getString("title")
        expectedBytes = json.getLong("totalBytes")
        currentBytes = 0
        transferStartedAt = System.currentTimeMillis()
        currentFile = File(cacheDir, "bt_${System.currentTimeMillis()}.mp4")
        currentOutput = FileOutputStream(currentFile)
        runOnUiThread {
            bufferStatus.text = "Buffer: 0%"
            status.text = "Estado: recibiendo $currentTitle"
        }
    }

    private fun writeChunk(payload: ByteArray) {
        currentOutput?.write(payload)
        currentBytes += payload.size
        val elapsedSeconds = ((System.currentTimeMillis() - transferStartedAt).coerceAtLeast(1)) / 1000.0
        val kbps = (currentBytes * 8.0 / 1000.0) / elapsedSeconds
        val percent = if (expectedBytes > 0) (currentBytes * 100 / expectedBytes).toInt() else 0
        runOnUiThread {
            bufferStatus.text = String.format(Locale.US, "Buffer: %d%% | %.1f kbps", percent, kbps)
        }
    }

    private fun finishVideo() {
        currentOutput?.flush()
        currentOutput?.close()
        currentOutput = null
        val file = currentFile ?: return
        if (!privateModeCheck.isChecked) store.addHistory(currentVideoId, currentTitle)
        runOnUiThread {
            status.text = "Estado: reproducción lista"
            bufferStatus.text = "Buffer: 100%"
            videoView.setVideoURI(Uri.fromFile(file))
            videoView.start()
        }
    }

    private fun showError(payload: ByteArray) = runOnUiThread {
        val message = JSONObject(String(payload)).optString("message", "Error desconocido")
        status.text = "Error: $message"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onLog(message: String) = runOnUiThread { status.text = message }

    override fun onDestroy() {
        super.onDestroy()
        currentOutput?.close()
        connection.close()
    }
}
