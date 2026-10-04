package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyGraphImpactTest {

    private HealthEngine engine;

    @BeforeEach
    void setUp() {
        engine = HealthEngine.builder().build();
        engine.registerDependency("payment")
                .registerDependency("search")
                .registerDependency("recommend")
                .registerDependency("orderSvc");
        engine.registerCall("orderSvc", "payment");
        engine.registerFeature("checkout", "orderSvc");
        engine.registerFeature("refund", "payment");
        engine.registerFeature("browse", "search");
        engine.registerFeature("home", "recommend");
    }

    @Test
    void impactSurfaceIncludesDirectAndTransitiveFeatures() {
        assertThat(engine.impactedFeaturesOf("payment")).containsExactlyInAnyOrder("checkout", "refund");
        assertThat(engine.impactedFeaturesOf("orderSvc")).containsExactly("checkout");
    }

    @Test
    void unrelatedFeaturesAreOutsideTheImpactSurface() {
        assertThat(engine.impactedFeaturesOf("payment")).doesNotContain("browse", "home");
        assertThat(engine.impactedFeaturesOf("search")).containsExactly("browse");
    }

    @Test
    void registeringCallsToUnknownDependenciesIsRejected() {
        assertThatThrownBy(() -> engine.registerCall("orderSvc", "ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cyclicCallsBetweenDependenciesAreRejected() {
        engine.registerDependency("a");
        engine.registerDependency("b");
        engine.registerCall("a", "b");
        assertThatThrownBy(() -> engine.registerCall("b", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("环");
    }
}
