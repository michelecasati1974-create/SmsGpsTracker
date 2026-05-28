package com.example.smsgpstracker.rxmulti

data class RxMultiSmsPacket(

    val sessionId: String,

    val segmentId: Long,

    val startPointId: Long,

    val endPointId: Long,

    val seq: Int,

    val total: Int,

    val type: String,

    val payloadChunk: String
)