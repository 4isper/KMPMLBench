package com.m4isper.kmpmlbench.benchmark.domain.platform

/**
 * iOS/Native: a portable memory counter is not wired up, so this reports 0.0.
 * The benchmark stays correct (peak tracking is best-effort) and the metric is
 * only surfaced where a real counter exists.
 */
actual fun currentMemoryUsageMb(): Double = 0.0
