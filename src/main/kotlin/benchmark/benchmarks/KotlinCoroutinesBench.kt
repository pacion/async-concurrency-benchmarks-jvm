package benchmark.benchmarks

import benchmark.scenarios.RealWorldScenarios
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@Fork(value = 1)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
open class KotlinCoroutinesBench {

    @Param("10", "100")
    var operationCount: Int = 10

    @Param("DefaultDispatcher", "IoDispatcher")
    lateinit var dispatcherType: String

    @Param("LaunchMode", "AsyncMode", "FlowMode")
    lateinit var executionMode: String

    @Param("Db", "Network", "Cpu", "Memory", "File")
    lateinit var scenario: String

    @Param("1", "4", "8")
    var concurrency: Int = 1

    private lateinit var dispatcher: CoroutineDispatcher

    @Setup(Level.Trial)
    fun setup() {
        dispatcher = when (dispatcherType) {
            "IoDispatcher" -> Dispatchers.IO
            else -> Dispatchers.Default.limitedParallelism(concurrency)
        }
    }

    @Benchmark
    fun benchmarkCoroutines(bh: Blackhole): Unit = runBlocking {
        when (executionMode) {
            "LaunchMode" -> launchMode(bh)
            "AsyncMode" -> asyncMode(bh)
            "FlowMode" -> flowMode(bh)
            else -> Unit
        }
    }

    private suspend fun launchMode(bh: Blackhole) = coroutineScope {
        val limiter = Semaphore(concurrency)

        repeat(operationCount) { id ->
            launch(dispatcher) {
                limiter.withPermit {
                    bh.consume(runScenario(id))
                }
            }
        }
    }

    private suspend fun asyncMode(bh: Blackhole) = coroutineScope {
        val limiter = Semaphore(concurrency)

        val deferreds = (0 until operationCount).map { id ->
            async(dispatcher) {
                limiter.withPermit {
                    runScenario(id)
                }
            }
        }

        deferreds.awaitAll().forEach { bh.consume(it) }
    }

    private suspend fun flowMode(bh: Blackhole) {
        (0 until operationCount).asFlow()
            .flatMapMerge(concurrency) { id ->
                flow {
                    emit(runScenario(id))
                }.flowOn(dispatcher)
            }
            .collect { bh.consume(it) }
    }

    /**
     * One place that decides what is being benchmarked.
     * Returns Any so we can consume it in Blackhole regardless of the scenario result type.
     */
    private fun runScenario(id: Int): Any = when (scenario) {
        "Db" -> RealWorldScenarios.simulateDatabaseQuery(id)
        "Network" -> RealWorldScenarios.networkCallWithRetry(id, 3)
        "Cpu" -> RealWorldScenarios.heavyCalculation(id * 100)
        "Memory" -> RealWorldScenarios.transformLargeDataset(1_000)
        "File" -> RealWorldScenarios.fileProcessing(id)
        else -> RealWorldScenarios.heavyCalculation(id * 100)
    }
}
