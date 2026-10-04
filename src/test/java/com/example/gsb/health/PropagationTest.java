package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PropagationTest {

    private final CallMetrics dead = CallMetrics.of(100, 100, 100, 3000);
    private final CallMetrics healthy = CallMetrics.of(100, 0, 0, 100);

    @Test
    void unhealthyDownstreamDragsAggregatorEffectiveScoreDown() {
        HealthEngine engine = HealthEngine.builder().propagationHeadroom(10).build();
        engine.registerDependency("payment");
        engine.registerDependency("orderSvc");
        engine.registerCall("orderSvc", "payment");

        EvaluationReport report = engine.evaluate(Map.of("payment", dead));

        assertThat(report.rawScores().get("orderSvc")).isCloseTo(100.0, within(0.001));
        assertThat(report.effectiveScores().get("payment")).isCloseTo(0.0, within(0.001));
        assertThat(report.effectiveScores().get("orderSvc")).isCloseTo(10.0, within(0.001));
    }

    @Test
    void propagationFlowsThroughMultipleLevels() {
        HealthEngine engine = HealthEngine.builder().propagationHeadroom(10).build();
        engine.registerDependency("payment");
        engine.registerDependency("orderSvc");
        engine.registerDependency("gateway");
        engine.registerCall("orderSvc", "payment");
        engine.registerCall("gateway", "orderSvc");

        EvaluationReport report = engine.evaluate(Map.of("payment", dead));

        assertThat(report.effectiveScores().get("orderSvc")).isCloseTo(10.0, within(0.001));
        assertThat(report.effectiveScores().get("gateway")).isCloseTo(20.0, within(0.001));
    }

    @Test
    void headroomIsConfigurable() {
        HealthEngine engine = HealthEngine.builder().propagationHeadroom(50).build();
        engine.registerDependency("payment");
        engine.registerDependency("orderSvc");
        engine.registerCall("orderSvc", "payment");

        EvaluationReport report = engine.evaluate(Map.of("payment", dead));
        assertThat(report.effectiveScores().get("orderSvc")).isCloseTo(50.0, within(0.001));
    }

    @Test
    void healthyDownstreamDoesNotInflateOrDragAggregator() {
        HealthEngine engine = HealthEngine.builder().propagationHeadroom(10).build();
        engine.registerDependency("payment");
        engine.registerDependency("orderSvc");
        engine.registerCall("orderSvc", "payment");

        EvaluationReport report = engine.evaluate(Map.of(
                "payment", healthy,
                "orderSvc", CallMetrics.of(100, 40, 0, 100)));

        assertThat(report.effectiveScores().get("orderSvc")).isCloseTo(80.0, within(0.001));
    }
}
