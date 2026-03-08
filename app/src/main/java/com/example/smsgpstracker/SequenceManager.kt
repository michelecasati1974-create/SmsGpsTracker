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

    fun reset() {
        currentSequence = 0
    }

    fun getCurrent(): Int {
        return currentSequence
    }
}