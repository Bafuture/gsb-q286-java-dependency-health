package com.example.gsb.health;

/**
 * 一个下游依赖在单个统计窗口内的调用指标。
 *
 * @param errorRate       错误率，取值 [0,1]，错误调用数 / 总调用数
 * @param avgLatencyMs    平均响应时间（毫秒）
 * @param timeoutRatio    超时比例，取值 [0,1]，超时调用数 / 总调用数
 * @param sampleCount     窗口内调用样本数（为 0 时视为无数据，默认健康）
 */
public record CallMetrics(double errorRate, double avgLatencyMs, double timeoutRatio, long sampleCount) {

    public CallMetrics {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        errorRate = clampUnit(errorRate);
        timeoutRatio = clampUnit(timeoutRatio);
        if (avgLatencyMs < 0) {
            throw new IllegalArgumentException("avgLatencyMs must be >= 0");
        }
    }

    /** 有数据样本的便捷构造器。 */
    public CallMetrics(double errorRate, double avgLatencyMs, double timeoutRatio) {
        this(errorRate, avgLatencyMs, timeoutRatio, 1);
    }

    /** 无调用数据：用于从未上报过指标的依赖。 */
    public static CallMetrics noData() {
        return new CallMetrics(0, 0, 0, 0);
    }

    static double clampUnit(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("ratio must not be NaN");
        }
        return Math.max(0, Math.min(1, value));
    }
}
