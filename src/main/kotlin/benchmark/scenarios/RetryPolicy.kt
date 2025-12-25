package benchmark.scenarios

object RetryPolicy {
    fun shouldFail(operationId: Int, attempt: Int): Boolean {
        return ((operationId + attempt) % ScenarioConfig.FAIL_MOD) < ScenarioConfig.FAIL_THRESHOLD
    }
}
