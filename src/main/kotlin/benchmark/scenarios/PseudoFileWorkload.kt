package benchmark.scenarios

object PseudoFileWorkload {

    fun process(id: Int): Map<String, Int> {
        val bytes = generateBytes(id)
        val content = bytes.decodeToString()

        return content.lineSequence()
            .groupingBy { it.substringBefore('-') }
            .eachCount()
    }

    private fun generateBytes(id: Int): ByteArray {
        val sb = StringBuilder(ScenarioConfig.FILE_LINES * (ScenarioConfig.LINE_LEN + 1))

        var seed = DeterministicRng.seed(id, 0x1111_2222_3333_4444L)
        for (i in 1..ScenarioConfig.FILE_LINES) {
            seed = DeterministicRng.mix64(seed + i)
            val suffix = ((seed ushr 40).toInt() and 1023)

            sb.append("line-").append(id).append('-').append(i).append("-data-").append(suffix)

            val pad = ScenarioConfig.LINE_LEN - (sb.length % (ScenarioConfig.LINE_LEN + 1))
            repeat(if (pad < 0) 0 else pad) { sb.append('x') }

            sb.append('\n')
        }

        return sb.toString().encodeToByteArray()
    }
}
