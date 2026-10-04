package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HealthScorerTest {

    private final HealthScorer scorer = new HealthScorer(HealthWeights.of(0.5, 0.3, 0.2), 200, 2000);

    @Test
    void perfectMetricsYieldFullScore() {
        double score = scorer.score(CallMetrics.of(100, 0, 0, 100));
        assertThat(score).isCloseTo(100.0, within(0.001));
    }

    @Test
    void allErrorsTimeoutsAndSlowLatencyYieldZero() {
        double score = scorer.score(CallMetrics.of(100, 100, 100, 3000));
        assertThat(score).isCloseTo(0.0, within(0.001));
    }

    @Test
    void latencyIsInterpolatedBetweenGoodAndBadBands() {
        double midLatency = (200 + 2000) / 2.0;
        double score = scorer.score(CallMetrics.of(100, 0, 0, midLatency));
        assertThat(score).isCloseTo(100.0 * (0.5 + 0.3 * 0.5 + 0.2), within(0.001));
    }

    @Test
    void latencyBelowBadButAboveGoodDecaysLinearly() {
        double score = scorer.score(CallMetrics.of(100, 0, 0, 1100));
        assertThat(score).isCloseTo(85.0, within(0.001));
    }

    @Test
    void customWeightsAreHonoredAndNormalized() {
        HealthScorer errorOnly = new HealthScorer(HealthWeights.of(1.0, 0.0, 0.0), 200, 2000);
        assertThat(errorOnly.score(CallMetrics.of(100, 40, 0, 100)))
                .isCloseTo(60.0, within(0.001));

        HealthScorer doubled = new HealthScorer(HealthWeights.of(2.0, 0.0, 0.0), 200, 2000);
        assertThat(doubled.score(CallMetrics.of(100, 40, 0, 100)))
                .isCloseTo(60.0, within(0.001));
    }

    @Test
    void errorAndTimeoutRatesAreWeightedIndependently() {
        double score = scorer.score(CallMetrics.of(100, 60, 0, 100));
        assertThat(score).isCloseTo(70.0, within(0.001));

        double timeoutScore = scorer.score(CallMetrics.of(100, 0, 50, 100));
        assertThat(timeoutScore).isCloseTo(90.0, within(0.001));
    }
}
