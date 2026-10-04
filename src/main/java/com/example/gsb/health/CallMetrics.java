package com.example.gsb.health;

/**
 * 一个评分窗口内对某个下游的调用统计。
 */
public final class CallMetrics {

    private final long totalCalls;
    private final long errorCount;
    private final long timeoutCount;
    private final double avgLatencyMs;

    private CallMetrics(long totalCalls, long errorCount, long timeoutCount, double avgLatencyMs) {
        if (totalCalls <= 0) {
            throw new IllegalArgumentException("totalCalls 必须为正数: " + totalCalls);
        }
        if (errorCount < 0 || errorCount > totalCalls) {
            throw new IllegalArgumentException("errorCount 必须在 [0, totalCalls] 范围内");
        }
        if (timeoutCount < 0 || timeoutCount > totalCalls) {
            throw new IllegalArgumentException("timeoutCount 必须在 [0, totalCalls] 范围内");
        }
        if (avgLatencyMs < 0) {
            throw new IllegalArgumentException("avgLatencyMs 不能为负数");
        }
        this.totalCalls = totalCalls;
        this.errorCount = errorCount;
        this.timeoutCount = timeoutCount;
        this.avgLatencyMs = avgLatencyMs;
    }

    public static CallMetrics of(long totalCalls, long errorCount, long timeoutCount, double avgLatencyMs) {
        return new CallMetrics(totalCalls, errorCount, timeoutCount, avgLatencyMs);
    }

    public long totalCalls() {
        return totalCalls;
    }

    public long errorCount() {
        return errorCount;
    }

    public long timeoutCount() {
        return timeoutCount;
    }

    public double avgLatencyMs() {
        return avgLatencyMs;
    }

    public double errorRate() {
        return (double) errorCount / totalCalls;
    }

    public double timeoutRatio() {
        return (double) timeoutCount / totalCalls;
    }
}
