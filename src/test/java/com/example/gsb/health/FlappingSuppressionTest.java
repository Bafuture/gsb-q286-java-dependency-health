package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 恢复判定：连续 N 次评分达标才解除降级，中途抖动则计数清零。 */
class FlappingSuppressionTest {

    private static final CallMetrics UNHEALTHY = new CallMetrics(0.8, 0, 0); // 20 分
    private static final CallMetrics HEALTHY = new CallMetrics(0.0, 0, 0);   // 100 分

    private HealthAssessmentEngine newEngine(int requiredPasses, MutableClock clock) {
        return HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .requiredConsecutiveHealthyPasses(requiredPasses)
                .clock(clock)
                .build();
    }

    @Test
    void singleHealthyPassDoesNotRecover() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(3, clock);
        engine.registerDependency("payment");

        engine.recordMetrics("payment", UNHEALTHY);
        engine.evaluate();
        assertThat(engine.degradedDependencies()).containsExactly("payment");

        engine.recordMetrics("payment", HEALTHY);
        engine.evaluate();
        assertThat(engine.degradedDependencies()).containsExactly("payment");
    }

    @Test
    void consecutiveHealthyPassesEventuallyRecover() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(3, clock);
        engine.registerDependency("payment");

        engine.recordMetrics("payment", UNHEALTHY);
        engine.evaluate();

        engine.recordMetrics("payment", HEALTHY);
        engine.evaluate();
        engine.evaluate();
        assertThat(engine.degradedDependencies()).containsExactly("payment");
        engine.evaluate();
        assertThat(engine.degradedDependencies()).isEmpty();
    }

    @Test
    void aBadPassResetsTheConsecutiveCounter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(3, clock);
        engine.registerDependency("payment");

        engine.recordMetrics("payment", UNHEALTHY);
        engine.evaluate();

        engine.recordMetrics("payment", HEALTHY);
        engine.evaluate();
        engine.evaluate();
        // 第三次评估分数再次变差，连续恢复计数清零
        engine.recordMetrics("payment", UNHEALTHY);
        engine.evaluate();
        engine.recordMetrics("payment", HEALTHY);
        engine.evaluate();
        engine.evaluate();
        assertThat(engine.degradedDependencies()).containsExactly("payment");
        engine.evaluate();
        assertThat(engine.degradedDependencies()).isEmpty();
    }

    @Test
    void healthyDependencyNeverEntersDegradedState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(2, clock);
        engine.registerDependency("payment");

        engine.recordMetrics("payment", HEALTHY);
        engine.evaluate();
        engine.evaluate();
        engine.evaluate();
        assertThat(engine.degradedDependencies()).isEmpty();
        assertThat(engine.auditTrail().events()).isEmpty();
    }

    @Test
    void hysteresisBandKeepsDependencyDegraded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .recoveryThreshold(80)
                .requiredConsecutiveHealthyPasses(1)
                .clock(clock)
                .build();
        engine.registerDependency("payment");

        engine.recordMetrics("payment", new CallMetrics(0.5, 0, 0)); // 50：触发降级
        engine.evaluate();
        engine.recordMetrics("payment", new CallMetrics(0.3, 0, 0)); // 70：迟滞区间，不恢复
        engine.evaluate();
        assertThat(engine.degradedDependencies()).containsExactly("payment");

        engine.recordMetrics("payment", new CallMetrics(0.1, 0, 0)); // 90：高于恢复阈值
        engine.evaluate();
        assertThat(engine.degradedDependencies()).isEmpty();
    }
}
