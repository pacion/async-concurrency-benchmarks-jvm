package benchmark.benchmarks

import benchmark.scenarios.RealWorldScenarios
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.TimeUnit

@Fork(value = 1)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
open class KotlinThreadsBench {

    @Param("10", "100", "1000")
    var operationCount: Int = 10

    @Param("Db", "Network", "Cpu", "Memory", "File")
    lateinit var scenario: String

    @Param("SyncBlock", "ReentrantLock", "ReadWriteLock", "JavaSemaphore", "Atomic")
    lateinit var lockType: String

    @Param("4", "8", "16")
    var poolSize: Int = 4

    @Param("1", "4", "8")
    var permits: Int = 4

    private val syncLock = Any()
    private val reentrantLock = ReentrantLock()
    private val rwLock = ReentrantReadWriteLock()
    private lateinit var javaSemaphore: Semaphore
    private val atomicCounter = AtomicInteger(0)

    private lateinit var executor: ExecutorService

    @Setup(Level.Trial)
    fun setup() {
        executor = Executors.newFixedThreadPool(poolSize)
        javaSemaphore = Semaphore(permits)
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    @Benchmark
    fun benchmarkLocks(bh: Blackhole) {
        val futures = ArrayList<Future<*>>(operationCount)

        repeat(operationCount) { id ->
            futures.add(executor.submit {
                val result = executeWithLock(id)
                bh.consume(result)
            })
        }

        for (f in futures) {
            f.get()
        }
    }

    private fun executeWithLock(id: Int): Any {
        return when (lockType) {
            "SyncBlock" -> synchronized(syncLock) { runScenario(id) }

            "ReentrantLock" -> {
                reentrantLock.lock()
                try {
                    runScenario(id)
                } finally {
                    reentrantLock.unlock()
                }
            }

            "ReadWriteLock" -> executeReadWrite(id)

            "JavaSemaphore" -> {
                javaSemaphore.acquire()
                try {
                    runScenario(id)
                } finally {
                    javaSemaphore.release()
                }
            }

            "Atomic" -> {
                atomicCounter.incrementAndGet()
                runScenario(id)
            }

            else -> runScenario(id)
        }
    }

    /**
     * Deterministic 10% writes, 90% reads (no RNG).
     * This keeps results stable across runs.
     */
    private fun executeReadWrite(id: Int): Any {
        val isWrite = (id % 10 == 0)

        return if (isWrite) {
            rwLock.writeLock().lock()
            try {
                runScenario(id)
            } finally {
                rwLock.writeLock().unlock()
            }
        } else {
            rwLock.readLock().lock()
            try {
                RealWorldScenarios.heavyCalculation(100)
            } finally {
                rwLock.readLock().unlock()
            }
        }
    }

    /**
     * Centralized scenario selection.
     * Returns Any so it can always be Blackhole-consumed.
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
