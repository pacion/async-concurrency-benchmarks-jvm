package benchmark.scenarios

import kotlin.math.sqrt

object CpuWorkload {
    fun heavyCalculation(iterations: Int): Long {
        var acc = 0L
        for (i in 0..iterations) {
            acc = Hashing.fnv1a64(i.toLong(), acc)
            acc += sqrt(i.toDouble()).toLong()
        }
        return acc
    }
}
