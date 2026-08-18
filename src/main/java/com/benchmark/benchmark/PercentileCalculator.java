package com.benchmark.benchmark;

import java.util.Arrays;

public class PercentileCalculator {

    private PercentileCalculator() {
    }

    public static double percentile(long[] values, double percentile) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("No latency values provided");
        }

        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }

        long[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);

        double rank = (percentile / 100.0) * (sorted.length - 1);

        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);

        if (lower == upper) {
            return sorted[lower] / 1_000_000.0;
        }

        double weight = rank - lower;

        double value =
                sorted[lower] +
                        weight * (sorted[upper] - sorted[lower]);

        return value / 1_000_000.0;
    }
}