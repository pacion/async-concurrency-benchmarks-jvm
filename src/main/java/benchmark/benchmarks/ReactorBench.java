package benchmark.benchmarks;

import benchmark.scenarios.RealWorldScenarios;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ReactorBench {

    @Param({"10", "100"})
    public int operationCount;

    @Param({"Flux", "ParallelFlux"})
    public String reactiveType;

    @Param({"Db", "Network", "Cpu", "Memory", "File"})
    public String scenario;

    @Param({"1", "4", "8"})
    public int concurrency;

    @Benchmark
    public void benchmark(Blackhole bh) {
        if ("Flux".equals(reactiveType)) {
            fluxMode(bh);
        } else {
            parallelFluxMode(bh);
        }
    }

    /**
     * flatMap with explicit max concurrency.
     * Each inner callable is scheduled (subscribeOn) to actually be async.
     */
    private void fluxMode(Blackhole bh) {
        Flux.range(0, operationCount)
                .flatMap(
                        id -> Mono.fromCallable(() -> runScenario(id))
                                .subscribeOn(Schedulers.boundedElastic()),
                        concurrency
                )
                .doOnNext(bh::consume)
                .blockLast();
    }

    /**
     * CPU-style parallelization using ParallelFlux.
     * runOn is required to execute rails on a Scheduler. [web:151][web:160]
     */
    private void parallelFluxMode(Blackhole bh) {
        Flux.range(0, operationCount)
                .parallel(concurrency)
                .runOn(Schedulers.parallel())
                .map(this::runScenario)
                .doOnNext(bh::consume)
                .sequential()
                .blockLast();
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
