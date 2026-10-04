package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HealthScorerTest {

    private final HealthScorer scorer = new HealthScorer(ScoreWeights.defaults());

    @Test
    void perfectlyHealthyMetricsScore100() {
        assertThat(scorer.score(new CallMetrics(0, 0, 0))).isEqualTo(100.0);
    }

    @Test
    void noSampleDataScoresFullMarks() {
        assertThat(scorer.score(CallMetrics.noData())).isEqualTo(100.0);
    }

    @Test
    void fullyBrokenMetricsScoreZero() {
        assertThat(scorer.score(new CallMetrics(1.0, 1000, 1.0))).isEqualTo(0.0);
    }

    @Test
    void defaultWeightsCombineErrorLatencyAndTimeout() {
        // 错误率 0.2 → 0.8；延迟 500/1000 → 0.5；超时 0.1 → 0.9
        // (0.5*0.8 + 0.2*0.5 + 0.3*0.9) / 1.0 * 100 = 77
        double score = scorer.score(new CallMetrics(0.2, 500, 0.1));
        assertThat(score).isCloseTo(77.0, within(1e-9));
    }

    @Test
    void latencyBeyondTolerableCapsLatencyComponentAtZero() {
        double atCap = scorer.score(new CallMetrics(0, 1000, 0));
        double beyondCap = scorer.score(new CallMetrics(0, 5000, 0));
        assertThat(atCap).isCloseTo(80.0, within(1e-9));
        assertThat(beyondCap).isEqualTo(atCap);
    }

    @Test
    void customWeightsChangeScore() {
        HealthScorer errorOnly = new HealthScorer(new ScoreWeights(1.0, 0.0, 0.0, 1000));
        assertThat(errorOnly.score(new CallMetrics(0.3, 9999, 0.9))).isCloseTo(70.0, within(1e-9));

        HealthScorer timeoutOnly = new HealthScorer(new ScoreWeights(0.0, 0.0, 1.0, 1000));
        assertThat(timeoutOnly.score(new CallMetrics(0.3, 9999, 0.9))).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void weightsAreNormalizedWhenNotSummingToOne() {
        HealthScorer doubled = new HealthScorer(new ScoreWeights(1.0, 0.4, 0.6, 1000));
        assertThat(doubled.score(new CallMetrics(0.2, 500, 0.1))).isCloseTo(77.0, within(1e-9));
    }

    @Test
    void invalidRatiosAreRejected() {
        assertThatThrownBy(() -> new CallMetrics(Double.NaN, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallMetrics(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
