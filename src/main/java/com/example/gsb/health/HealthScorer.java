package com.example.gsb.health;

/**
 * 健康评分器：按错误率、响应时间、超时比例加权计算 0~100 的健康分。
 *
 * <p>评分公式（各子项先归一化到 [0,1]，权重归一化后加权求和，再放大到 100 分制）：
 * <pre>
 * score = 100 * ( wE * (1 - errorRate)
 *               + wL * (1 - min(1, avgLatency / maxTolerableLatency))
 *               + wT * (1 - timeoutRatio) ) / (wE + wL + wT)
 * </pre>
 * 无样本数据时返回满分 100（未观测不等于不健康）。
 */
public class HealthScorer {

    public static final double MAX_SCORE = 100.0;

    private final ScoreWeights weights;

    public HealthScorer(ScoreWeights weights) {
        this.weights = weights;
    }

    public ScoreWeights weights() {
        return weights;
    }

    /** 计算单个依赖的原始健康分（不含传播），范围 [0,100]。 */
    public double score(CallMetrics metrics) {
        if (metrics.sampleCount() == 0) {
            return MAX_SCORE;
        }
        double errorComponent = 1 - metrics.errorRate();
        double latencyComponent = 1 - Math.min(1.0, metrics.avgLatencyMs() / weights.maxTolerableLatencyMs());
        double timeoutComponent = 1 - metrics.timeoutRatio();
        double weighted = weights.errorWeight() * errorComponent
                + weights.latencyWeight() * latencyComponent
                + weights.timeoutWeight() * timeoutComponent;
        return MAX_SCORE * weighted / weights.total();
    }
}
