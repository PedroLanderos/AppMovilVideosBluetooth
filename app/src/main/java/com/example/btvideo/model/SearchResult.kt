package com.example.btvideo.model

data class SearchResult(
    val id: String,
    val title: String,
    val source: String,
    val verified: Boolean,
    val durationText: String = "",
    val playable: Boolean = true
)
