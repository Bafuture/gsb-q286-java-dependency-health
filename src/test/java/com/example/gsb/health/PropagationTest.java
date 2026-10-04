package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * 传播规则：有效健康分 = min(自身分, 所有直接下游的有效分) —— 最弱链路规则。
 */
class PropagationTest {

    private HealthAssessmentEngine newEngine() {
        return HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .build();
    }

    @Test
    void unhealthyDownstreamDragsAggregatorScoreDown() {
        HealthAssessmentEngine engine = newEngine();
        engine.addCallEdge("order-service", "payment");
        engine.recordMetrics("order-service", new CallMetrics(0.0, 0, 0));   // 自身 100
        engine.recordMetrics("payment", new CallMetrics(0.5, 0, 0));         // 自身 50

        EvaluationReport report = engine.evaluate();

        assertThat(report.ownScores().get("order-service")).isEqualTo(100.0);
        assertThat(report.effectiveScores().get("order-service")).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void healthyDownstreamKeepsAggregatorScore() {
        HealthAssessmentEngine engine = newEngine();
        engine.addCallEdge("order-service", "payment");
        engine.recordMetrics("order-service", new CallMetrics(0.1, 0, 0)); // 90
        engine.recordMetrics("payment", new CallMetrics(0.0, 0, 0));       // 100

        EvaluationReport report = engine.evaluate();

        assertThat(report.effectiveScores().get("order-service")).isCloseTo(90.0, within(1e-9));
    }

    @Test
    void propagationIsTransitiveAcrossMultipleLevels() {
        HealthAssessmentEngine engine = newEngine();
        engine.addCallEdge("gateway", "order-service");
        engine.addCallEdge("order-service", "payment");
        engine.recordMetrics("payment", new CallMetrics(0.7, 0, 0)); // 30

        EvaluationReport report = engine.evaluate();

        assertThat(report.effectiveScores().get("order-service")).isCloseTo(30.0, within(1e-9));
        assertThat(report.effectiveScores().get("gateway")).isCloseTo(30.0, within(1e-9));
    }

    @Test
    void weakestOfMultipleDownstreamsWins() {
        HealthAssessmentEngine engine = newEngine();
        engine.addCallEdge("order-service", "payment");
        engine.addCallEdge("order-service", "inventory");
        engine.recordMetrics("payment", new CallMetrics(0.2, 0, 0));   // 80
        engine.recordMetrics("inventory", new CallMetrics(0.4, 0, 0)); // 60

        EvaluationReport report = engine.evaluate();

        assertThat(report.effectiveScores().get("order-service")).isCloseTo(60.0, within(1e-9));
    }

    @Test
    void cyclicDependenciesDoNotHangOrCorruptScores() {
        HealthAssessmentEngine engine = newEngine();
        engine.addCallEdge("a", "b");
        engine.addCallEdge("b", "a");
        engine.recordMetrics("a", new CallMetrics(0.5, 0, 0)); // 50
        engine.recordMetrics("b", new CallMetrics(0.0, 0, 0)); // 100

        EvaluationReport report = engine.evaluate();

        assertThat(report.effectiveScores().get("a")).isCloseTo(50.0, within(1e-9));
        assertThat(report.effectiveScores().get("b")).isCloseTo(50.0, within(1e-9));
    }
}
