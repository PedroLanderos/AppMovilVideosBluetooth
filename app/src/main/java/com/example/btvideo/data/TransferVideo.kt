package com.example.btvideo.data

import java.io.InputStream

data class TransferVideo(
    val displayName: String,
    val totalBytes: Long,
    val mime: String,
    val inputStreamFactory: () -> InputStream
)
