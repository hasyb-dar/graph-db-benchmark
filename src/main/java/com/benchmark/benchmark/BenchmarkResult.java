package com.benchmark.benchmark;

public class BenchmarkResult {

    private final String platform;
    private final String workload;
    private final int iterations;
    private final double p50Ms;
    private final double p95Ms;

    public BenchmarkResult(
            String platform,
            String workload,
            int iterations,
            double p50Ms,
            double p95Ms
    ) {
        this.platform = platform;
        this.workload = workload;
        this.iterations = iterations;
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
    }

    public String getPlatform() {
        return platform;
    }

    public String getWorkload() {
        return workload;
    }

    public int getIterations() {
        return iterations;
    }

    public double getP50Ms() {
        return p50Ms;
    }

    public double getP95Ms() {
        return p95Ms;
    }

    @Override
    public String toString() {
        return String.format(
                "%s | %s | iterations=%d | p50=%.3f ms | p95=%.3f ms",
                platform,
                workload,
                iterations,
                p50Ms,
                p95Ms
        );
    }
}