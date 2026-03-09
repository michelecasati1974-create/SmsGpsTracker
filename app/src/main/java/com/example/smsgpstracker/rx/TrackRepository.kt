object TrackRepository {

    val points = mutableListOf<Pair<Double, Double>>()

    fun addPoints(list: List<Pair<Double, Double>>) {

        points.addAll(list)
    }

    fun clear() {
        points.clear()
    }
}