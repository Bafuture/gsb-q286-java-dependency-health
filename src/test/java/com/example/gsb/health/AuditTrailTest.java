package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditTrailTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));

    @Test
    void degradationAndRecoveryEventsAreRecordedWithTimeReasonAndFeatures() {
        HealthEngine engine = HealthEngine.builder().consecutiveRecoveries(2).clock(clock).build();
        engine.registerDependency("payment");
        engine.registerFeature("checkout", "payment");

        EvaluationReport degradeReport = engine.evaluate(Map.of(
                "payment", CallMetrics.of(100, 100, 100, 3000)));

        AuditEvent degrade = degradeReport.events().get(0);
        assertThat(degrade.timestamp()).isEqualTo(Instant.parse("2026-10-04T10:00:00Z"));
        assertThat(degrade.action()).isEqualTo(AuditEvent.Action.DEGRADED);
        assertThat(degrade.dependency()).isEqualTo("payment");
        assertThat(degrade.impactedFeatures()).containsExactly("checkout");
        assertThat(degrade.reason()).contains("降级阈值");

        clock.advance(java.time.Duration.ofMinutes(5));
        engine.evaluate(Map.of("payment", CallMetrics.of(100, 0, 0, 100)));
        clock.advance(java.time.Duration.ofMinutes(2));
        EvaluationReport recoverReport = engine.evaluate(Map.of(
                "payment", CallMetrics.of(100, 0, 0, 100)));

        AuditEvent recover = recoverReport.events().get(0);
        assertThat(recover.action()).isEqualTo(AuditEvent.Action.RECOVERED);
        assertThat(recover.timestamp()).isEqualTo(Instant.parse("2026-10-04T10:07:00Z"));
        assertThat(recover.reason()).contains("连续", "2", "恢复阈值");
        assertThat(recover.impactedFeatures()).containsExactly("checkout");

        assertThat(engine.auditEvents()).hasSize(2);
        assertThat(engine.auditEvents()).extracting(AuditEvent::action)
                .containsExactly(AuditEvent.Action.DEGRADED, AuditEvent.Action.RECOVERED);
    }
}
