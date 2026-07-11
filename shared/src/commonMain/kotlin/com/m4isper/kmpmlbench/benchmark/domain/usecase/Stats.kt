package com.m4isper.kmpmlbench.benchmark.domain.usecase

/** Pure statistical helpers for benchmark metrics. No framework dependencies. */
object Stats {
    /**
     * Linear-interpolated percentile of an already-sorted list of values.
     * [p] is in `[0, 100]`. Returns 0.0 for an empty list.
     */
    fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = (p / 100.0) * (sorted.size - 1)
        val low = rank.toInt()
        val high = minOf(low + 1, sorted.lastIndex)
        val frac = rank - low
        return sorted[low] + (sorted[high] - sorted[low]) * frac
    }
}
