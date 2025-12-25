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
public class CompletableFutureBench {

    @Param({"10", "100"})
    public int operationCount;

    @Param({"Sequential", "Parallel", "Pipeline"})
    public String executionMode;

    @Param({"Db", "Network", "Cpu", "Memory", "File"})
    public String scenario;

    @Param({"4", "8", "16"})
    public int poolSize;

    private ExecutorService executor;

    @Setup(Level.Trial)
    public void setup() {
        executor = Executors.newFixedThreadPool(poolSize);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Benchmark
    public void benchmark(Blackhole bh) {
        if ("Sequential".equals(executionMode)) {
            fanOutAllOf(bh);
        } else if ("Parallel".equals(executionMode)) {
            fanOutAllOf(bh);
        } else {
            pipelineMode(bh);
        }
    }

    /**
     * Fan-out N tasks, wait for all, then consume results.
     */
    private void fanOutAllOf(Blackhole bh) {
        List<CompletableFuture<Object>> futures = new ArrayList<>(operationCount);

        for (int i = 0; i < operationCount; i++) {
            final int id = i;
            futures.add(CompletableFuture.supplyAsync(() -> runScenario(id), executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<Object> f : futures) {
            bh.consume(f.join());
        }
    }

    /**
     * Pipeline: chain tasks; this is intentionally sequential dependency (prev -> next).
     * Uses thenApplyAsync(..., executor) for explicit executor control. [web:189]
     */
    private void pipelineMode(Blackhole bh) {
        CompletableFuture<Object> f = CompletableFuture.completedFuture(0);

        for (int i = 0; i < operationCount; i++) {
            final int id = i;
            f = f.thenApplyAsync(prev -> runScenario(id), executor);
        }

        bh.consume(f.join());
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
