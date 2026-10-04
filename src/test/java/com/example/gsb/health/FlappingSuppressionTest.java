package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlappingSuppressionTest {

    private HealthEngine engine;
    private final CallMetrics dead = CallMetrics.of(100, 100, 100, 3000);
    private final CallMetrics healthy = CallMetrics.of(100, 0, 0, 100);
    private final CallMetrics score70 = CallMetrics.of(100, 60, 0, 100);

    @BeforeEach
    void setUp() {
        engine = HealthEngine.builder()
                .consecutiveRecoveries(3)
                .degradeThreshold(60)
                .recoverThreshold(80)
                .build();
        engine.registerDependency("payment");
        engine.evaluate(Map.of("payment", dead));
        assertThat(engine.isDegraded("payment")).isTrue();
    }

    @Test
    void recoveryRequiresConsecutiveHealthyEvaluations() {
        engine.evaluate(Map.of("payment", healthy));
        assertThat(engine.isDegraded("payment")).isTrue();

        engine.evaluate(Map.of("payment", healthy));
        assertThat(engine.isDegraded("payment")).isTrue();

        EvaluationReport report = engine.evaluate(Map.of("payment", healthy));
        assertThat(engine.isDegraded("payment")).isFalse();
        assertThat(report.events()).singleElement()
                .extracting(AuditEvent::action).isEqualTo(AuditEvent.Action.RECOVERED);
    }

    @Test
    void aSingleBadEvaluationResetsConsecutiveRecoveryCounter() {
        engine.evaluate(Map.of("payment", healthy));
        engine.evaluate(Map.of("payment", healthy));
        engine.evaluate(Map.of("payment", dead));
        engine.evaluate(Map.of("payment", healthy));
        engine.evaluate(Map.of("payment", healthy));
        assertThat(engine.isDegraded("payment")).isTrue();

        engine.evaluate(Map.of("payment", healthy));
        assertThat(engine.isDegraded("payment")).isFalse();
    }

    @Test
    void scoreInsideHysteresisBandKeepsDependencyDegraded() {
        EvaluationReport report = engine.evaluate(Map.of("payment", score70));
        assertThat(report.effectiveScores().get("payment")).isEqualTo(70.0);
        assertThat(engine.isDegraded("payment")).isTrue();
        assertThat(report.events()).isEmpty();

        engine.evaluate(Map.of("payment", healthy));
        engine.evaluate(Map.of("payment", healthy));
        EvaluationReport stillDegraded = engine.evaluate(Map.of("payment", score70));
        assertThat(engine.isDegraded("payment")).isTrue();
        assertThat(stillDegraded.events()).isEmpty();
    }
}
