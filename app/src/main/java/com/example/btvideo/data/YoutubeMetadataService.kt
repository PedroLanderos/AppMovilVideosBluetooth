package com.example.btvideo.data

import com.example.btvideo.model.SearchResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Servicio opcional para búsqueda oficial de metadata con YouTube Data API.
 * No descarga ni extrae video. Úsalo solo si tienes API key y aceptas los términos de YouTube.
 */
class YoutubeMetadataService(private val apiKey: String) {
    fun search(query: String, maxResults: Int = 5): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&maxResults=$maxResults&q=$encodedQuery&key=$apiKey"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return connection.inputStream.bufferedReader().use { reader ->
            val root = JSONObject(reader.readText())
            val items = root.getJSONArray("items")
            List(items.length()) { index ->
                val item = items.getJSONObject(index)
                val id = item.getJSONObject("id").getString("videoId")
                val snippet = item.getJSONObject("snippet")
                SearchResult(
                    id = id,
                    title = snippet.getString("title"),
                    source = "youtube-metadata",
                    verified = false,
                    durationText = ""
                )
            }
        }
    }
}
