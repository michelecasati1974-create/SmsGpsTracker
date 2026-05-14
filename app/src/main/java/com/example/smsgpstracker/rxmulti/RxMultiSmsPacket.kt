package com.example.smsgpstracker.rxmulti

data class RxMultiSmsPacket(
    val sessionId: String,
    val seq: Int,
    val total: Int,
    val type: String,
    val payloadChunk: String,
    val segmentId: Long
)