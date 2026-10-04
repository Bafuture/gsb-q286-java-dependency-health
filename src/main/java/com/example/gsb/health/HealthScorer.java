package com.example.gsb.health;

/**
 * 健康评分器。
 *
 * 评分公式（满分 100）：
 *   score = 100 * ( wErr * (1 - errorRate)
 *                 + wLat * latencyScore
 *                 + wTO  * (1 - timeoutRatio) ) / (wErr + wLat + wTO)
 *
 * latencyScore 采用两段线性：平均延迟 <= goodLatencyMs 记 1.0，
 * >= badLatencyMs 记 0.0，之间线性衰减。
 */
public final class HealthScorer {

    private final HealthWeights weights;
    private final double goodLatencyMs;
    private final double badLatencyMs;

    public HealthScorer(HealthWeights weights, double goodLatencyMs, double badLatencyMs) {
        if (weights == null) {
            throw new IllegalArgumentException("weights 不能为空");
        }
        if (goodLatencyMs < 0 || badLatencyMs <= goodLatencyMs) {
            throw new IllegalArgumentException("需要满足 0 <= goodLatencyMs < badLatencyMs");
        }
        this.weights = weights;
        this.goodLatencyMs = goodLatencyMs;
        this.badLatencyMs = badLatencyMs;
    }

    public double score(CallMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("metrics 不能为空");
        }
        double latencyScore;
        double avgLatency = metrics.avgLatencyMs();
        if (avgLatency <= goodLatencyMs) {
            latencyScore = 1.0;
        } else if (avgLatency >= badLatencyMs) {
            latencyScore = 0.0;
        } else {
            latencyScore = 1.0 - (avgLatency - goodLatencyMs) / (badLatencyMs - goodLatencyMs);
        }
        double raw = weights.errorWeight() * (1.0 - metrics.errorRate())
                + weights.latencyWeight() * latencyScore
                + weights.timeoutWeight() * (1.0 - metrics.timeoutRatio());
        double score = 100.0 * raw / weights.sum();
        return Math.max(0.0, Math.min(100.0, score));
    }

    public HealthWeights weights() {
        return weights;
    }

    public double goodLatencyMs() {
        return goodLatencyMs;
    }

    public double badLatencyMs() {
        return badLatencyMs;
    }
}
