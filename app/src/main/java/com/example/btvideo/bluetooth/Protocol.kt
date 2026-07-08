package com.example.btvideo.bluetooth

import com.example.btvideo.model.SearchResult
import com.example.btvideo.model.VideoSourceMode
import org.json.JSONArray
import org.json.JSONObject

enum class FrameType(val code: Int) {
    SEARCH_REQUEST(1),
    SEARCH_RESPONSE(2),
    PLAY_REQUEST(3),
    VIDEO_META(4),
    VIDEO_CHUNK(5),
    VIDEO_END(6),
    ERROR(7),
    PING(8),
    PONG(9);

    companion object {
        fun fromCode(code: Int): FrameType = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unknown frame type: $code")
    }
}

object Protocol {
    const val SERVICE_NAME = "BtVideoPlayer"
    const val SERVICE_UUID = "7f67a2ac-4d64-4cb1-81f0-2cda9be39981"
    const val CHUNK_SIZE = 16 * 1024

    fun searchRequest(query: String, sourceMode: VideoSourceMode): ByteArray = JSONObject()
        .put("query", query)
        .put("sourceMode", sourceMode.wireName)
        .toString()
        .toByteArray()

    fun playRequest(videoId: String, sourceMode: VideoSourceMode, lowPower: Boolean): ByteArray = JSONObject()
        .put("videoId", videoId)
        .put("sourceMode", sourceMode.wireName)
        .put("lowPower", lowPower)
        .toString()
        .toByteArray()

    fun videoMeta(videoId: String, title: String, totalBytes: Long, mime: String): ByteArray = JSONObject()
        .put("videoId", videoId)
        .put("title", title)
        .put("totalBytes", totalBytes)
        .put("mime", mime)
        .toString()
        .toByteArray()

    fun error(message: String): ByteArray = JSONObject().put("message", message).toString().toByteArray()

    fun searchResponse(results: List<SearchResult>): ByteArray {
        val array = JSONArray()
        results.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("source", item.source)
                .put("verified", item.verified)
                .put("durationText", item.durationText)
                .put("playable", item.playable))
        }
        return JSONObject().put("results", array).toString().toByteArray()
    }

    fun parseResults(payload: ByteArray): List<SearchResult> {
        val json = JSONObject(String(payload))
        val array = json.getJSONArray("results")
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            SearchResult(
                id = obj.getString("id"),
                title = obj.getString("title"),
                source = obj.getString("source"),
                verified = obj.optBoolean("verified", false),
                durationText = obj.optString("durationText"),
                playable = obj.optBoolean("playable", true)
            )
        }
    }
}
