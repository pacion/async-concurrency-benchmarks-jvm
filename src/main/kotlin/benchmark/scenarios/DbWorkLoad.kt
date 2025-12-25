package benchmark.scenarios

object DbWorkload {

    fun buildQuery(id: Int): String {
        val sb = StringBuilder(16 + ScenarioConfig.PARAM_COUNT * 16)
        sb.append("id=").append(id)

        var seed = DeterministicRng.seed(id, 0x1234_5678_9ABC_DEF0L)
        for (i in 0 until ScenarioConfig.PARAM_COUNT) {
            seed = DeterministicRng.mix64(seed + i)
            val v = ((seed ushr 33).toInt() and 1023)
            sb.append("&param").append(i).append('=').append(v)
        }
        return sb.toString()
    }

    fun buildJson(id: Int, query: String): String {
        val records = ScenarioConfig.JSON_RECORDS_BASE +
                (DeterministicRng.mix64(DeterministicRng.seed(id, 0x0FED_CBA9_8765_4321L)).toInt() and ScenarioConfig.JSON_RECORDS_MASK)

        return """
            {
              "id": $id,
              "data": "${query.take(100)}...",
              "records": [$records]
            }
        """.trimIndent()
    }

    fun validate(json: String): String {
        val tokens = json.split(' ', '\n', '\t')
        var checksum = 0L
        for (t in tokens) {
            if (t.isNotEmpty()) checksum = Hashing.fnv1a64(t.hashCode().toLong(), checksum)
        }
        return "validated-$checksum"
    }
}
