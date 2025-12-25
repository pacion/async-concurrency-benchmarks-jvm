package benchmark.scenarios

object NetworkWorkload {

    fun simulateLatencyCost(operationId: Int, attempt: Int): Long {
        var x = DeterministicRng.seed(operationId, attempt.toLong())
        repeat(ScenarioConfig.NETWORK_MIX_ROUNDS) { i ->
            x = DeterministicRng.mix64(x + i)
        }
        return ((x ushr 11) % ScenarioConfig.NETWORK_LATENCY_MOD)
    }

    fun simulateBackoffCost(attemptNumber: Int): Long {
        var x = 1L shl attemptNumber
        repeat(ScenarioConfig.BACKOFF_MIX_ROUNDS) { i ->
            x = DeterministicRng.mix64(x + i)
        }
        return x
    }
}
