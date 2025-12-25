package benchmark.benchmarks;

import benchmark.scenarios.RealWorldScenarios;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class RxJavaBench {

    @Param({"10", "100"})
    public int operationCount;

    @Param({"Observable", "Flowable", "ParallelFlowable"})
    public String reactiveType;

    @Param({"Db", "Network", "Cpu", "Memory", "File"})
    public String scenario;

    @Param({"1", "4", "8"})
    public int concurrency;

    @Benchmark
    public void benchmark(Blackhole bh) {
        switch (reactiveType) {
            case "Observable" -> observableMode(bh);
            case "Flowable" -> flowableMode(bh);
            case "ParallelFlowable" -> parallelFlowableMode(bh);
            default -> observableMode(bh);
        }
    }

    private void observableMode(Blackhole bh) {
        Observable.range(0, operationCount)
                .flatMap(
                        id -> Observable.fromCallable(() -> runScenario(id))
                                .subscribeOn(Schedulers.io()),
                        /* delayErrors */ false,
                        /* maxConcurrency */ concurrency
                )
                .blockingForEach(bh::consume);
    }

    private void flowableMode(Blackhole bh) {
        Flowable.range(0, operationCount)
                .flatMap(
                        id -> Flowable.fromCallable(() -> runScenario(id))
                                .subscribeOn(Schedulers.io()),
                        /* delayErrors */ false,
                        /* maxConcurrency */ concurrency
                )
                .blockingForEach(bh::consume);
    }

    private void parallelFlowableMode(Blackhole bh) {
        Flowable.range(0, operationCount)
                .parallel(concurrency)
                .runOn(Schedulers.io())
                .map(this::runScenario)
                .sequential()
                .blockingForEach(bh::consume);
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
