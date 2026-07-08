package com.example.btvideo.model

enum class VideoSourceMode(val wireName: String) {
    LOCAL("local"),
    YOUTUBE_EXPERIMENTAL("youtube_experimental");

    companion object {
        fun fromWireName(value: String?): VideoSourceMode = entries.firstOrNull { it.wireName == value } ?: LOCAL
    }
}
