package benchmark.scenarios

/**
 * Single place to tune workload cost.
 */
object ScenarioConfig {
    // DB simulation
    const val PARAM_COUNT = 50
    const val JSON_RECORDS_BASE = 7
    const val JSON_RECORDS_MASK = 63 // +0..63

    // Retry policy: deterministic ~30% failures
    const val FAIL_MOD = 10
    const val FAIL_THRESHOLD = 3 // 3/10 = 30%

    // Network simulation
    const val NETWORK_MIX_ROUNDS = 200
    const val NETWORK_LATENCY_MOD = 100_000
    const val BACKOFF_MIX_ROUNDS = 2_000

    // Pseudo-file (in-memory) simulation
    const val FILE_LINES = 1_000
    const val LINE_LEN = 48
}
