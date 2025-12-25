package benchmark.benchmarks;

import benchmark.scenarios.RealWorldScenarios;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class PlatformThreadsBench {

    @Param({"10", "100"})
    public int operationCount;

    @Param({"Db", "Network", "Cpu", "Memory", "File"})
    public String scenario;

    @Param({"4", "8", "16"})
    public int poolSize;

    @Param({"FixedPool", "WorkStealing"})
    public String poolType;

    private ExecutorService executor;

    @Setup(Level.Trial)
    public void setup() {
        executor = "FixedPool".equals(poolType)
                ? Executors.newFixedThreadPool(poolSize)
                : Executors.newWorkStealingPool(poolSize);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Benchmark
    public void benchmark(Blackhole bh) throws Exception {
        List<Future<Object>> futures = new ArrayList<>(operationCount);

        for (int i = 0; i < operationCount; i++) {
            final int id = i;
            futures.add(executor.submit(() -> runScenario(id)));
        }

        for (Future<Object> future : futures) {
            bh.consume(future.get());
        }
    }

    private Object runScenario(int id) {
        return switch (scenario) {
            case "Db" -> RealWorldScenarios.INSTANCE.simulateDatabaseQuery(id);
            case "Network" -> RealWorldScenarios.INSTANCE.networkCallWithRetry(id, 3);
            case "Cpu" -> RealWorldScenarios.INSTANCE.heavyCalculation(id * 100);
            case "Memory" -> RealWorldScenarios.INSTANCE.transformLargeDataset(1_000);
            case "File" -> RealWorldScenarios.INSTANCE.fileProcessing(id);
            default -> RealWorldScenarios.INSTANCE.heavyCalculation(id * 100);
        };
    }
}
