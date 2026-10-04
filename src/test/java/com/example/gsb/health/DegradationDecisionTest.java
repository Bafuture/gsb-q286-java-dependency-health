package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DegradationDecisionTest {

    private HealthEngine engine;

    @BeforeEach
    void setUp() {
        engine = HealthEngine.builder().build();
        engine.registerDependency("payment");
        engine.registerDependency("search");
        engine.registerDependency("orderSvc");
        engine.registerCall("orderSvc", "payment");
        engine.registerFeature("checkout", "orderSvc");
        engine.registerFeature("refund", "payment");
        engine.registerFeature("browse", "search");
    }

    @Test
    void onlyFeaturesImpactedByUnhealthyDependencyAreDegraded() {
        EvaluationReport report = engine.evaluate(Map.of(
                "payment", CallMetrics.of(100, 100, 100, 3000)));

        assertThat(engine.isDegraded("payment")).isTrue();
        assertThat(report.degradedDependencies()).contains("payment");
        assertThat(report.degradedFeatures()).containsExactlyInAnyOrder("checkout", "refund");
        assertThat(engine.isFeatureDegraded("checkout")).isTrue();
        assertThat(engine.isFeatureDegraded("browse")).isFalse();
    }

    @Test
    void healthyEvaluationDoesNotDegradeAnything() {
        EvaluationReport report = engine.evaluate(Map.of(
                "payment", CallMetrics.of(100, 0, 0, 100),
                "search", CallMetrics.of(100, 0, 0, 100)));

        assertThat(report.degradedDependencies()).isEmpty();
        assertThat(report.degradedFeatures()).isEmpty();
        assertThat(report.events()).isEmpty();
    }

    @Test
    void reportExposesImpactListForEachDegradation() {
        EvaluationReport report = engine.evaluate(Map.of(
                "payment", CallMetrics.of(100, 100, 100, 3000)));

        AuditEvent event = report.events().get(0);
        assertThat(event.action()).isEqualTo(AuditEvent.Action.DEGRADED);
        assertThat(event.impactedFeatures()).containsExactlyInAnyOrder("checkout", "refund");
    }
}
