package benchmark.scenarios

object MemoryWorkload {
    fun transformLargeDataset(size: Int): List<Long> =
        (0..size)
            .asSequence()
            .map { it.toLong() }
            .map { v -> v * v * 6364136223846793005L }
            .filter { it % 3L == 0L }
            .map { it * 2 + it.rotateLeft(32) }
            .toList()
}
