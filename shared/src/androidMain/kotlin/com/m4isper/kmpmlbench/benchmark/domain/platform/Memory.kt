package com.m4isper.kmpmlbench.benchmark.domain.platform

/** Android (JVM): used heap (total - free) in megabytes. */
actual fun currentMemoryUsageMb(): Double {
    val rt = Runtime.getRuntime()
    val usedBytes = rt.totalMemory() - rt.freeMemory()
    return usedBytes / (1024.0 * 1024.0)
}
