import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.benchmark"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:1.37")
    kapt("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")

    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.projectreactor:reactor-core:3.7.0")

    implementation("org.slf4j:slf4j-api:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += "-Xjvm-default=all"
    }
}

tasks.shadowJar {
    archiveBaseName.set("benchmarks")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "org.openjdk.jmh.Main"
    }
}

/**
 * Reads JMH results from JSON and prints a ranking:
 * - time: primaryMetric.score (we use -bm avgt and -tu s => s/op)
 * - memory: secondary metric gc.alloc.rate.norm (B/op)
 */
fun printRanking(jsonPath: String) {
    val resultsFile = file(jsonPath)
    if (!resultsFile.exists()) {
        println("\n❌ Results file not found: $jsonPath")
        println("   Make sure JMH finished successfully and that -rff points to the correct path.\n")
        return
    }

    try {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(resultsFile) as List<Map<String, Any>>

        if (json.isEmpty()) {
            println("\n⚠️ Results file exists, but contains no data: $jsonPath\n")
            return
        }

        val results = json.map { benchmark ->
            val nameFull = benchmark["benchmark"] as String
            val name = nameFull.substringAfterLast(".")

            val paramsMap = benchmark["params"] as? Map<String, String>
            val paramsStr = paramsMap
                ?.entries
                ?.joinToString(", ") { "${it.key}=${it.value}" }
                ?: "no params"

            val primaryMetric = benchmark["primaryMetric"] as Map<String, Any>
            val score = (primaryMetric["score"] as Number).toDouble()

            val secondary = benchmark["secondaryMetrics"] as? Map<String, Any>
            val memMetric = secondary?.get("·gc.alloc.rate.norm") as? Map<String, Any>
            val memScore = (memMetric?.get("score") as? Number)?.toDouble() ?: 0.0
            val memUnit = memMetric?.get("scoreUnit") as? String ?: "B/op"

            Triple("$name [$paramsStr]", score, Pair(memScore, memUnit))
        }.sortedBy { it.second } // lower time = better

        val best = results.firstOrNull()
        val worst = results.lastOrNull()

        println("\n" + "=".repeat(120))
        println("🏁 JMH REPORT - RANKING (Mode: AverageTime, Unit: s/op, Memory: B/op)")
        println("=".repeat(120))

        if (best != null && worst != null && results.size > 1) {
            println("✅ Best result:  ${best.first}  ->  ${"%.6f".format(best.second)} s/op")
            println("❌ Worst result: ${worst.first} ->  ${"%.6f".format(worst.second)} s/op")
            println("-".repeat(120))
        }

        println(String.format("%-4s | %-80s | %-15s | %-15s", "No.", "BENCHMARK + PARAMS", "TIME [s/op]", "MEMORY"))
        println("-".repeat(120))

        results.forEachIndexed { index, (name, score, mem) ->
            val timeStr = String.format("%.6f", score)

            val memStr = when {
                mem.first > 1024 * 1024 -> String.format("%.2f MB/op", mem.first / (1024 * 1024))
                mem.first > 1024 -> String.format("%.2f kB/op", mem.first / 1024)
                else -> String.format("%.0f B/op", mem.first)
            }

            // green: very fast, yellow: ok, red: slow
            val color = if (score < 0.001) "\u001B[32m" else if (score > 0.1) "\u001B[31m" else "\u001B[33m"
            val reset = "\u001B[0m"

            val displayName = if (name.length > 80) name.take(77) + "..." else name

            println(
                String.format(
                    "%-4d | %s%-80s%s | %s%-15s%s | %s",
                    index + 1, color, displayName, reset, color, timeStr, reset, memStr
                )
            )
        }

        println("=".repeat(120))
        println("📄 Report generated from: $jsonPath\n")

    } catch (e: Exception) {
        println("\n⚠️ Failed to generate ranking from: $jsonPath")
        println("   Error details: ${e.message}\n")
    }
}

tasks.register<Exec>("jmhFast") {
    group = "benchmark"
    description = "Fast benchmark (short warmup, short measurement) + ranking + GC"
    dependsOn("shadowJar")

    val reportFile = "build/results-fast.json"

    doFirst {
        println("\n🏃 Running FAST benchmark...")
        println("   JSON results: $reportFile\n")
    }

    commandLine(
        "java", "--enable-preview", "-jar", "build/libs/benchmarks.jar",
        "-wi", "1", "-i", "2", "-f", "1", "-r", "1s", "-w", "1s",
        "-tu", "s", "-bm", "avgt", "-prof", "gc",
        "-rf", "json", "-rff", reportFile,
        "-p", "operationCount=10,100"
    )

    isIgnoreExitValue = true
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            println("\n⚠️ Benchmark finished with an error (exit code: $exit).")
            println("   Try: ./gradlew jmhFast --info or run benchmarks with more verbose logging.\n")
        } else {
            println("\n✅ Benchmark finished successfully.\n")
        }
        printRanking(reportFile)
    }
}

tasks.register<Exec>("jmhMedium") {
    group = "benchmark"
    description = "Medium benchmark + ranking + GC"
    dependsOn("shadowJar")

    val reportFile = "build/results-medium.json"

    doFirst {
        println("\n🚴 Running MEDIUM benchmark...")
        println("   JSON results: $reportFile\n")
    }

    commandLine(
        "java", "--enable-preview", "-jar", "build/libs/benchmarks.jar",
        "-wi", "2", "-i", "3", "-f", "1",
        "-r", "2s", "-w", "2s",
        "-tu", "s", "-bm", "avgt", "-prof", "gc",
        "-rf", "json", "-rff", reportFile,
        "-p", "operationCount=10,100,1000,10000"
    )

    isIgnoreExitValue = true
    doLast { printRanking(reportFile) }
}

tasks.register<Exec>("jmhHigh") {
    group = "benchmark"
    description = "Full benchmark (longer) + ranking + GC"
    dependsOn("shadowJar")

    val reportFile = "build/results-high.json"

    doFirst {
        println("\n🚀 Running HIGH benchmark (full set)...")
        println("   JSON results: $reportFile\n")
    }

    commandLine(
        "java", "--enable-preview", "-jar", "build/libs/benchmarks.jar",
        "-wi", "3", "-i", "5", "-f", "1",
        "-tu", "s", "-bm", "avgt", "-prof", "gc",
        "-rf", "json", "-rff", reportFile,
        "-p", "operationCount=10,100,1000,10000,100000"
    )

    isIgnoreExitValue = true
    doLast { printRanking(reportFile) }
}
