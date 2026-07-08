package com.example.btvideo.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.btvideo.R
import com.example.btvideo.bluetooth.BluetoothConnection
import com.example.btvideo.bluetooth.FrameType
import com.example.btvideo.bluetooth.Protocol
import com.example.btvideo.data.VideoCatalog
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs
import org.json.JSONObject
import java.io.IOException

class ServerActivity : Activity(), BluetoothConnection.Listener {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var connection: BluetoothConnection
    private lateinit var catalog: VideoCatalog

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        PermissionHelper.request(this)

        status = findViewById(R.id.serverStatus)
        logView = findViewById(R.id.serverLog)
        catalog = VideoCatalog(this)
        connection = BluetoothConnection(this, this)

        findViewById<Button>(R.id.startServerButton).setOnClickListener {
            status.text = "Estado: esperando cliente..."
            connection.startServer()
        }
    }

    override fun onConnected(role: String) = runOnUiThread {
        status.text = "Estado: conectado como $role"
        Toast.makeText(this, "Cliente conectado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected(reason: String) = runOnUiThread {
        status.text = "Estado: desconectado"
        appendLog(reason)
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    override fun onFrame(type: FrameType, payload: ByteArray) {
        when (type) {
            FrameType.SEARCH_REQUEST -> handleSearch(payload)
            FrameType.PLAY_REQUEST -> handlePlay(payload)
            FrameType.PING -> connection.send(FrameType.PONG)
            else -> appendLog("Frame no esperado en servidor: $type")
        }
    }

    private fun handleSearch(payload: ByteArray) {
        val query = JSONObject(String(payload)).getString("query")
        appendLog("Búsqueda: $query")
        val results = catalog.search(query)
        connection.send(FrameType.SEARCH_RESPONSE, Protocol.searchResponse(results))
    }

    private fun handlePlay(payload: ByteArray) {
        val json = JSONObject(String(payload))
        val videoId = json.getString("videoId")
        val lowPower = json.optBoolean("lowPower", false)
        val result = catalog.find(videoId)
        val video = catalog.openVideoAsset(videoId, lowPower)
        if (result == null || video == null) {
            connection.send(FrameType.ERROR, Protocol.error("No existe el video $videoId en assets/videos. Agrega $videoId.mp4 o ${videoId}_low.mp4."))
            return
        }

        appendLog("Enviando: ${result.title} (${video.totalBytes} bytes)")
        connection.send(FrameType.VIDEO_META, Protocol.videoMeta(videoId, result.title, video.totalBytes, "video/mp4"))

        try {
            video.inputStream.use { input ->
                val buffer = ByteArray(Protocol.CHUNK_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val exact = if (read == buffer.size) buffer else buffer.copyOf(read)
                    connection.send(FrameType.VIDEO_CHUNK, exact)
                }
            }
            connection.send(FrameType.VIDEO_END)
            appendLog("Transferencia completa: ${result.title}")
        } catch (e: IOException) {
            connection.send(FrameType.ERROR, Protocol.error("Error enviando video: ${e.message}"))
        }
    }

    override fun onLog(message: String) = appendLog(message)

    private fun appendLog(message: String) = runOnUiThread {
        logView.append("\n$message")
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.close()
    }
}
