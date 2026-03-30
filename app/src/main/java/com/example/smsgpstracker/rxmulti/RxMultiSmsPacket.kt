package com.example.smsgpstracker.rxmulti

data class RxMultiSmsPacket(

    val sessionId: String,
    val seq: Int,
    val type: String, // D o F
    val payload: String
)