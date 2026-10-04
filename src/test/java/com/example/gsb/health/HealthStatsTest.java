package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HealthStatsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));
    private final CallMetrics dead = CallMetrics.of(100, 100, 100, 3000);
    private final CallMetrics healthy = CallMetrics.of(100, 0, 0, 100);

    private HealthEngine engine() {
        HealthEngine engine = HealthEngine.builder()
                .consecutiveRecoveries(2)
                .clock(clock)
                .build();
        engine.registerDependency("payment");
        engine.registerDependency("search");
        engine.registerFeature("checkout", "payment");
        engine.registerFeature("browse", "search");
        return engine;
    }

    @Test
    void reportsDependencyCountDegradedItemsAndTriggerCount() {
        HealthEngine engine = engine();

        HealthStats initial = engine.stats();
        assertThat(initial.dependencyCount()).isEqualTo(2);
        assertThat(initial.currentDegradedDependencies()).isEmpty();
        assertThat(initial.totalDegradationCount()).isZero();

        engine.evaluate(Map.of("payment", dead));
        HealthStats degraded = engine.stats();
        assertThat(degraded.currentDegradedDependencies()).containsExactly("payment");
        assertThat(degraded.totalDegradationCount()).isEqualTo(1);

        engine.evaluate(Map.of("payment", dead));
        assertThat(engine.stats().totalDegradationCount()).isEqualTo(1);
    }

    @Test
    void averageRecoveryTimeIsComputedAcrossCycles() {
        HealthEngine engine = engine();

        engine.evaluate(Map.of("payment", dead));
        clock.advance(Duration.ofMinutes(2));
        engine.evaluate(Map.of("payment", healthy));
        clock.advance(Duration.ofMinutes(2));
        engine.evaluate(Map.of("payment", healthy));

        HealthStats afterFirstRecovery = engine.stats();
        assertThat(afterFirstRecovery.completedRecoveryCount()).isEqualTo(1);
        assertThat(afterFirstRecovery.averageRecoveryTime()).isEqualTo(Duration.ofMinutes(4));

        engine.evaluate(Map.of("payment", dead));
        clock.advance(Duration.ofMinutes(5));
        engine.evaluate(Map.of("payment", healthy));
        clock.advance(Duration.ofMinutes(5));
        engine.evaluate(Map.of("payment", healthy));

        HealthStats afterSecondRecovery = engine.stats();
        assertThat(afterSecondRecovery.completedRecoveryCount()).isEqualTo(2);
        assertThat(afterSecondRecovery.totalDegradationCount()).isEqualTo(2);
        assertThat(afterSecondRecovery.currentDegradedDependencies()).isEmpty();
        assertThat(afterSecondRecovery.averageRecoveryTime()).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void averageRecoveryTimeIsZeroWhenNoRecoveryHasHappened() {
        HealthEngine engine = engine();
        engine.evaluate(Map.of("payment", dead));
        assertThat(engine.stats().averageRecoveryTime()).isEqualTo(Duration.ZERO);
    }
}
