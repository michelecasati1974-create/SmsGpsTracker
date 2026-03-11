package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    private var lastSeq = 0

    fun process(packet: RxMultiSmsPacket): List<Pair<Double,Double>> {

        if (packet.seq != lastSeq + 1) {

            android.util.Log.w(
                "RX_MULTI",
                "SEQ jump $lastSeq -> ${packet.seq}"
            )
        }

        lastSeq = packet.seq

        return PolylineCodec.decode(packet.encodedPolyline)
    }
}