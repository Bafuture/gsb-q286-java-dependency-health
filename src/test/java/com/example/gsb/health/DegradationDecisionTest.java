package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 降级决策：只降级受影响的功能，不影响其它链路，并给出影响面清单。 */
class DegradationDecisionTest {

    private HealthAssessmentEngine engine;

    @BeforeEach
    void setUp() {
        engine = HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .build();
        engine.addCallEdge("order-service", "payment");
        engine.addCallEdge("order-service", "inventory");
        engine.mapFeature("checkout", "order-service");
        engine.mapFeature("order-detail", "order-service");
        engine.mapFeature("search", "search-engine");
    }

    @Test
    void healthySystemDegradesNothing() {
        EvaluationReport report = engine.evaluate();
        assertThat(report.degradedDeps()).isEmpty();
        assertThat(report.degradedFeatures()).isEmpty();
        assertThat(report.newEvents()).isEmpty();
    }

    @Test
    void unhealthyDependencyDegradesOnlyImpactedFeatures() {
        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0)); // 20 分

        EvaluationReport report = engine.evaluate();

        assertThat(report.degradedFeatures()).containsExactlyInAnyOrder("checkout", "order-detail");
        assertThat(report.degradedFeatures()).doesNotContain("search");
    }

    @Test
    void impactListIdentifiesCausingDependencies() {
        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));

        EvaluationReport report = engine.evaluate();

        // payment 自身不健康，传播使 order-service 也不健康，两者都是 checkout 的触发源
        assertThat(report.featureCauses().get("checkout"))
                .containsExactlyInAnyOrder("payment", "order-service");
        assertThat(report.featureCauses()).doesNotContainKey("search");
    }

    @Test
    void unrelatedDependencyStaysHealthy() {
        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));

        EvaluationReport report = engine.evaluate();

        assertThat(report.effectiveScores().get("search-engine")).isEqualTo(100.0);
        assertThat(report.degradedDeps()).doesNotContain("search-engine");
    }

    @Test
    void scoreJustAboveThresholdDoesNotDegrade() {
        engine.recordMetrics("payment", new CallMetrics(0.39, 0, 0)); // 61 分 >= 60

        EvaluationReport report = engine.evaluate();

        assertThat(report.degradedDeps()).isEmpty();
    }
}
