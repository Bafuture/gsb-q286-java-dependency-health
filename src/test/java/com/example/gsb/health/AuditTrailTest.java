package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 决策审计：记录每次降级与恢复的时间、依据与被影响功能。 */
class AuditTrailTest {

    @Test
    void recordsDegradationAndRecoveryWithTimestampReasonAndImpacts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .requiredConsecutiveHealthyPasses(1)
                .clock(clock)
                .build();
        engine.addCallEdge("order-service", "payment");
        engine.mapFeature("checkout", "order-service");

        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
        EvaluationReport degraded = engine.evaluate();
        AuditEvent degradeEvent = degraded.newEvents().stream()
                .filter(e -> e.dependency().equals("payment")).findFirst().orElseThrow();

        assertThat(degradeEvent.type()).isEqualTo(AuditEvent.Type.DEGRADED);
        assertThat(degradeEvent.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(degradeEvent.dependency()).isEqualTo("payment");
        assertThat(degradeEvent.impactedFeatures()).containsExactly("checkout");
        assertThat(degradeEvent.reason()).contains("20.00").contains("60.00");

        clock.advance(java.time.Duration.ofMinutes(5));
        engine.recordMetrics("payment", new CallMetrics(0.0, 0, 0));
        EvaluationReport recovered = engine.evaluate();
        AuditEvent recoverEvent = recovered.newEvents().stream()
                .filter(e -> e.dependency().equals("payment")).findFirst().orElseThrow();

        assertThat(recoverEvent.type()).isEqualTo(AuditEvent.Type.RECOVERED);
        assertThat(recoverEvent.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
        assertThat(recoverEvent.impactedFeatures()).containsExactly("checkout");
        assertThat(recoverEvent.reason()).contains("consecutive");
    }

    @Test
    void repeatedDegradationAppendsSeparateEvents() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .requiredConsecutiveHealthyPasses(1)
                .clock(clock)
                .build();
        engine.registerDependency("payment");

        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
        engine.evaluate();
        engine.recordMetrics("payment", new CallMetrics(0.0, 0, 0));
        engine.evaluate();
        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
        engine.evaluate();

        assertThat(engine.auditTrail().eventsFor("payment"))
                .extracting(AuditEvent::type)
                .containsExactly(AuditEvent.Type.DEGRADED, AuditEvent.Type.RECOVERED, AuditEvent.Type.DEGRADED);
    }
}
