package com.example.btvideo.data

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class YoutubeExperimentalSourceTest {

    private lateinit var youtubeSource: YoutubeExperimentalSource

    /**
     * Video corto usado solo para verificar que el pipeline produce un MP4 con pista de video.
     * Si YouTube cambia formatos o bloquea el acceso, este test puede fallar aunque la app compile bien.
     */
    private val testVideoUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // La Application también inicializa estas librerías, pero hacerlo aquí ayuda cuando el test
        // se ejecuta aislado desde Android Studio.
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
        } catch (_: Exception) {
            // Si ya estaban inicializadas, algunas versiones pueden lanzar excepción. Se ignora.
        }

        youtubeSource = YoutubeExperimentalSource(context)
    }

    @Test
    fun search_returnsYoutubeResults() {
        val results = youtubeSource.search("video corto", maxResults = 3)

        assertTrue("La búsqueda debe regresar al menos un resultado", results.isNotEmpty())
        assertTrue("El primer resultado debe tener id o URL", results.first().id.isNotBlank())
        assertTrue("El primer resultado debe tener título", results.first().title.isNotBlank())
    }

    @Test
    fun getVideo_normalMode_returnsPlayableMp4WithVideoTrack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val transferVideo = youtubeSource.getVideo(testVideoUrl, lowPower = false)

        assertNotNull("TransferVideo no debe ser nulo", transferVideo)
        assertTrue("El archivo debe pesar más de 0 bytes", transferVideo.totalBytes > 0)
        assertEquals("El MIME esperado debe ser video/mp4", "video/mp4", transferVideo.mime)

        val copiedFile = copyTransferToTempFile(context.cacheDir, transferVideo, "normal_mode_test.mp4")
        assertTrue("El MP4 generado debe contener pista de video", copiedFile.hasVideoTrack())
    }

    @Test
    fun getVideo_lowPowerMode_returnsPlayableMp4WithVideoTrack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val transferVideo = youtubeSource.getVideo(testVideoUrl, lowPower = true)

        assertNotNull("TransferVideo no debe ser nulo", transferVideo)
        assertTrue("El archivo debe pesar más de 0 bytes", transferVideo.totalBytes > 0)
        assertEquals("El MIME esperado debe ser video/mp4", "video/mp4", transferVideo.mime)

        val copiedFile = copyTransferToTempFile(context.cacheDir, transferVideo, "low_power_mode_test.mp4")
        assertTrue("El MP4 generado debe contener pista de video", copiedFile.hasVideoTrack())
    }

    private fun copyTransferToTempFile(cacheDir: File, transferVideo: TransferVideo, fileName: String): File {
        val output = File(cacheDir, fileName)
        if (output.exists()) output.delete()

        transferVideo.inputStreamFactory().use { input ->
            output.outputStream().use { fileOutput ->
                input.copyTo(fileOutput)
            }
        }

        return output
    }

    private fun File.hasVideoTrack(): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            width > 0 && height > 0
        } catch (_: Exception) {
            false
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }
}
