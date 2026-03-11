package com.example.smsgpstracker.rxmulti

data class RxMultiSmsPacket(

    val seq: Int,

    val pointsDeclared: Int,

    val encodedPolyline: String
)