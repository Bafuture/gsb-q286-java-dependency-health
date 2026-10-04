package com.example.gsb.health;

/**
 * 健康评分权重配置。
 *
 * @param errorWeight       错误率权重
 * @param latencyWeight     响应时间权重
 * @param timeoutWeight     超时比例权重
 * @param maxTolerableLatencyMs 可容忍的最大响应时间（毫秒），超过后响应时间子项记 0 分
 */
public record ScoreWeights(double errorWeight, double latencyWeight, double timeoutWeight,
                           double maxTolerableLatencyMs) {

    public ScoreWeights {
        if (errorWeight < 0 || latencyWeight < 0 || timeoutWeight < 0) {
            throw new IllegalArgumentException("weights must be >= 0");
        }
        if (errorWeight + latencyWeight + timeoutWeight <= 0) {
            throw new IllegalArgumentException("at least one weight must be > 0");
        }
        if (maxTolerableLatencyMs <= 0) {
            throw new IllegalArgumentException("maxTolerableLatencyMs must be > 0");
        }
    }

    /** 默认权重：错误率 0.5、响应时间 0.2、超时 0.3，最大可容忍响应时间 1000ms。 */
    public static ScoreWeights defaults() {
        return new ScoreWeights(0.5, 0.2, 0.3, 1000);
    }

    double total() {
        return errorWeight + latencyWeight + timeoutWeight;
    }
}
