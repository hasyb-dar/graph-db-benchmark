package com.benchmark.benchmark;

public class PercentileCalculatorTest {

    public static void main(String[] args) {

        long[] latencies = {
                1_000_000,
                2_000_000,
                3_000_000,
                4_000_000,
                5_000_000
        };

        double p50 =
                PercentileCalculator.percentile(latencies, 50);

        double p95 =
                PercentileCalculator.percentile(latencies, 95);

        System.out.println("p50 = " + p50 + " ms");
        System.out.println("p95 = " + p95 + " ms");
    }
}