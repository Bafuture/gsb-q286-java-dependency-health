package com.example.gsb.health;

/**
 * 健康评分三要素的权重：错误率、响应时间、超时比例。
 * 权重不需要预先归一化，评分时会按权重之和归一处理。
 */
public final class HealthWeights {

    private final double errorWeight;
    private final double latencyWeight;
    private final double timeoutWeight;

    private HealthWeights(double errorWeight, double latencyWeight, double timeoutWeight) {
        if (errorWeight < 0 || latencyWeight < 0 || timeoutWeight < 0) {
            throw new IllegalArgumentException("权重不能为负数");
        }
        if (errorWeight + latencyWeight + timeoutWeight <= 0) {
            throw new IllegalArgumentException("权重之和必须大于 0");
        }
        this.errorWeight = errorWeight;
        this.latencyWeight = latencyWeight;
        this.timeoutWeight = timeoutWeight;
    }

    public static HealthWeights of(double errorWeight, double latencyWeight, double timeoutWeight) {
        return new HealthWeights(errorWeight, latencyWeight, timeoutWeight);
    }

    public double errorWeight() {
        return errorWeight;
    }

    public double latencyWeight() {
        return latencyWeight;
    }

    public double timeoutWeight() {
        return timeoutWeight;
    }

    public double sum() {
        return errorWeight + latencyWeight + timeoutWeight;
    }
}
