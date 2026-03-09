package com.example.smsgpstracker

class SequenceManager {

    private var currentSequence = 0

    fun next(): Int {
        currentSequence++

        if (currentSequence > 9999) {
            currentSequence = 1
        }

        return currentSequence
    }

    // 👇 INSERISCI QUESTO METODO QUI
    fun peekNext(): Int {
        var nextSeq = currentSequence + 1

        if (nextSeq > 9999) {
            nextSeq = 1
        }

        return nextSeq
    }

    fun reset() {
        currentSequence = 0
    }

    fun getCurrent(): Int {
        return currentSequence
    }
}


