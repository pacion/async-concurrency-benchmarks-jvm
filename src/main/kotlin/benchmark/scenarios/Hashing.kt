package benchmark.scenarios

object Hashing {
    fun fnv1a64(data: Long, hash: Long): Long {
        var h = hash xor data
        h *= 0x100000001b3L
        return h
    }
}
