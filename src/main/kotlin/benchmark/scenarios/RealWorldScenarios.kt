package benchmark.scenarios

object RealWorldScenarios {

    data class QueryResult(val id: Int, val value: String, val timestamp: Long)

    fun simulateDatabaseQuery(operationId: Int): QueryResult {
        val query = DbWorkload.buildQuery(operationId)
        val json = DbWorkload.buildJson(operationId, query)
        val value = DbWorkload.validate(json)
        return QueryResult(operationId, value, System.nanoTime())
    }

    fun networkCallWithRetry(operationId: Int, maxRetries: Int = 1): String {
        for (attempt in 0 until maxRetries) {
            val shouldFail = ((operationId + attempt) % 10) < 3
            if (!shouldFail) {
                val latency = NetworkWorkload.simulateLatencyCost(operationId, attempt)
                return "Success-$operationId-attempt-$attempt-latency-$latency"
            }
            if (attempt < maxRetries - 1) {
                NetworkWorkload.simulateBackoffCost(attempt + 1)
            }
        }
        return "Failed-$operationId-after-$maxRetries"
    }

    fun heavyCalculation(iterations: Int): Long =
        CpuWorkload.heavyCalculation(iterations)

    fun transformLargeDataset(size: Int): List<Long> =
        MemoryWorkload.transformLargeDataset(size)

    fun fileProcessing(id: Int): Map<String, Int> =
        PseudoFileWorkload.process(id)
}
