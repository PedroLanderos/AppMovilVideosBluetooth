package com.example.btvideo.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStoreTest {

    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(LocalStore.DATABASE_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStore.DATABASE_NAME)
    }

    @Test
    fun addHistory_thenGetHistory_returnsSavedVideo() {
        store.addHistory("local_video_1", "Video local de prueba")

        val history = store.getHistory()

        assertEquals(1, history.size)
        assertEquals("local_video_1", history.first().videoId)
        assertEquals("Video local de prueba", history.first().title)
        assertTrue(history.first().timestamp > 0)
    }

    @Test
    fun getHistory_returnsNewestFirst() {
        store.addHistory("video_1", "Primer video")
        Thread.sleep(5)
        store.addHistory("video_2", "Segundo video")

        val history = store.getHistory()

        assertEquals(2, history.size)
        assertEquals("video_2", history[0].videoId)
        assertEquals("video_1", history[1].videoId)
    }

    @Test
    fun clearHistory_removesHistoryItems() {
        store.addHistory("video_1", "Primer video")
        store.addHistory("video_2", "Segundo video")

        store.clearHistory()

        assertTrue(store.getHistory().isEmpty())
    }

    @Test
    fun toggleFavorite_addsAndRemovesFavorite() {
        val added = store.toggleFavorite("https://www.youtube.com/watch?v=test1234567", "Video favorito")

        assertTrue(added)
        assertTrue(store.isFavorite("https://www.youtube.com/watch?v=test1234567"))
        assertEquals(1, store.getFavorites().size)
        assertEquals("Video favorito", store.getFavorites().first().title)

        val removed = store.toggleFavorite("https://www.youtube.com/watch?v=test1234567", "Video favorito")

        assertFalse(removed)
        assertFalse(store.isFavorite("https://www.youtube.com/watch?v=test1234567"))
        assertTrue(store.getFavorites().isEmpty())
    }

    @Test
    fun clearFavorites_removesFavoriteItems() {
        store.toggleFavorite("video_1", "Primer favorito")
        store.toggleFavorite("video_2", "Segundo favorito")

        store.clearFavorites()

        assertTrue(store.getFavorites().isEmpty())
    }
}
