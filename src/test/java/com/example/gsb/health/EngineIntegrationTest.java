package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 端到端：多下游场景中只精准降级受影响链路，并在稳定恢复后解除。 */
class EngineIntegrationTest {

    @Test
    void selectiveDegradationAndStableRecoveryAcrossWholeSystem() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = HealthAssessmentEngine.builder()
                .weights(ScoreWeights.defaults())
                .degradeThreshold(60)
                .requiredConsecutiveHealthyPasses(2)
                .clock(clock)
                .build();
        engine.addCallEdge("order-service", "payment");
        engine.addCallEdge("order-service", "inventory");
        engine.mapFeature("checkout", "order-service");
        engine.mapFeature("search", "search-engine");
        engine.mapFeature("recommendations", "recommendation-service");

        // payment 全面故障：错误率 100%、延迟打满、全部超时
        engine.recordMetrics("payment", new CallMetrics(1.0, 2000, 1.0));
        EvaluationReport report = engine.evaluate();

        assertThat(report.degradedFeatures()).containsExactly("checkout");
        assertThat(report.degradedFeatures()).doesNotContain("search", "recommendations");

        // payment 恢复，连续两次评估达标后解除降级
        engine.recordMetrics("payment", new CallMetrics(0.0, 50, 0.0));
        engine.evaluate();
        assertThat(engine.degradedDependencies()).isNotEmpty();
        EvaluationReport recovered = engine.evaluate();
        assertThat(recovered.degradedFeatures()).isEmpty();
        assertThat(engine.auditTrail().events())
                .filteredOn(e -> e.type() == AuditEvent.Type.RECOVERED)
                .isNotEmpty();
    }
}
