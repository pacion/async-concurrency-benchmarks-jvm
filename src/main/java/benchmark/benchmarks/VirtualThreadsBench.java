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
public class VirtualThreadsBench {

    @Param({"10", "100"})
    public int operationCount;

    @Param({"DirectExecution", "StructuredConcurrency"})
    public String executionMode;

    @Param({"Db", "Network", "Cpu", "Memory", "File"})
    public String scenario;

    @Benchmark
    public void benchmark(Blackhole bh) throws Exception {
        if ("DirectExecution".equals(executionMode)) {
            directExecution(bh);
        } else {
            structuredConcurrency(bh);
        }
    }

    private void directExecution(Blackhole bh) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(operationCount);

            for (int i = 0; i < operationCount; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    Object result = runScenario(id);
                    bh.consume(result);
                    return null;
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
        }
    }

    private void structuredConcurrency(Blackhole bh) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < operationCount; i++) {
                final int id = i;
                scope.fork(() -> {
                    Object result = runScenario(id);
                    bh.consume(result);
                    return null;
                });
            }

            scope.join();
            scope.throwIfFailed();
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
