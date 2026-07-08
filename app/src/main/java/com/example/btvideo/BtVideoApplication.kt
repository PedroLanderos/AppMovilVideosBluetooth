package com.example.btvideo

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

class BtVideoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e("BtVideoApplication", "No se pudo inicializar youtubedl-android", e)
        } catch (e: Exception) {
            Log.e("BtVideoApplication", "Error inesperado inicializando youtubedl-android", e)
        }
    }
}