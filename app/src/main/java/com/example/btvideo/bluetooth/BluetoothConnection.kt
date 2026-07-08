package com.example.btvideo.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothConnection(
    private val context: Context,
    private val listener: Listener
) : Closeable {

    interface Listener {
        fun onConnected(role: String)
        fun onDisconnected(reason: String)
        fun onFrame(type: FrameType, payload: ByteArray)
        fun onLog(message: String)
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val executor = Executors.newCachedThreadPool()
    private var socket: BluetoothSocket? = null
    private var out: DataOutputStream? = null
    private val running = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun startServer() {
        val bt = adapter ?: return listener.onDisconnected("Este dispositivo no soporta Bluetooth")
        running.set(true)
        executor.execute {
            try {
                listener.onLog("Esperando cliente...")
                val serverSocket = bt.listenUsingRfcommWithServiceRecord(
                    Protocol.SERVICE_NAME,
                    UUID.fromString(Protocol.SERVICE_UUID)
                )
                val connected = serverSocket.accept()
                serverSocket.close()
                attachSocket(connected, "Servidor")
            } catch (e: IOException) {
                listener.onDisconnected("Servidor detenido: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        running.set(true)
        executor.execute {
            try {
                listener.onLog("Conectando con ${device.name ?: device.address}...")
                adapter?.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(UUID.fromString(Protocol.SERVICE_UUID))
                s.connect()
                attachSocket(s, "Cliente")
            } catch (e: IOException) {
                listener.onDisconnected("No se pudo conectar: ${e.message}")
            }
        }
    }

    private fun attachSocket(newSocket: BluetoothSocket, role: String) {
        socket = newSocket
        out = DataOutputStream(newSocket.outputStream)
        listener.onConnected(role)
        executor.execute { readLoop(newSocket) }
    }

    private fun readLoop(s: BluetoothSocket) {
        try {
            val input = DataInputStream(s.inputStream)
            while (running.get()) {
                val typeCode = input.readInt()
                val length = input.readInt()
                if (length < 0 || length > 25 * 1024 * 1024) throw IOException("Invalid frame length: $length")
                val payload = ByteArray(length)
                input.readFully(payload)
                listener.onFrame(FrameType.fromCode(typeCode), payload)
            }
        } catch (e: Exception) {
            if (running.get()) listener.onDisconnected("Conexión perdida: ${e.message}")
        }
    }

    @Synchronized
    fun send(type: FrameType, payload: ByteArray = ByteArray(0)) {
        val stream = out ?: throw IllegalStateException("Socket no conectado")
        stream.writeInt(type.code)
        stream.writeInt(payload.size)
        stream.write(payload)
        stream.flush()
    }

    override fun close() {
        running.set(false)
        try { socket?.close() } catch (_: IOException) { }
        executor.shutdownNow()
    }
}
