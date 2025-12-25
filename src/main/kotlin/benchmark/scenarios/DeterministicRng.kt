package benchmark.scenarios

/**
 * Deterministic pseudo-random generator for benchmarks.
 * No shared state, stable results across runs.
 */
object DeterministicRng {

    fun seed(a: Int, b: Long): Long =
        mix64((a.toLong() shl 32) xor b)

    /**
     * Deterministic mixing function (uses safe Long constants).
     */
    fun mix64(v0: Long): Long {
        var v = v0
        v = v xor (v ushr 33)
        v *= -0xae502812aa7333L
        v = v xor (v ushr 33)
        v *= -0x3b314601e57a13adL
        v = v xor (v ushr 33)
        return v
    }
}
