package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
        // checkout -> order-service -> {payment, inventory}; search -> search-engine
        graph.addCallEdge("order-service", "payment");
        graph.addCallEdge("order-service", "inventory");
        graph.mapFeature("checkout", "order-service");
        graph.mapFeature("order-detail", "order-service");
        graph.mapFeature("search", "search-engine");
    }

    @Test
    void registersDependenciesTransitively() {
        assertThat(graph.dependencies())
                .containsExactlyInAnyOrder("order-service", "payment", "inventory", "search-engine");
    }

    @Test
    void impactSurfaceIncludesOnlyTransitivelyAffectedFeatures() {
        assertThat(graph.impactedFeatures("payment"))
                .containsExactlyInAnyOrder("checkout", "order-detail");
        assertThat(graph.impactedFeatures("search-engine")).containsExactly("search");
    }

    @Test
    void directFeatureDependencyImpactsThatFeature() {
        graph.mapFeature("pay-now", "payment");
        assertThat(graph.impactedFeatures("payment")).contains("pay-now");
    }

    @Test
    void unaffectedFeaturesAreExcluded() {
        assertThat(graph.impactedFeatures("payment")).doesNotContain("search");
    }

    @Test
    void affectedUpstreamsWalkCallEdgesBackwards() {
        assertThat(graph.affectedUpstreams("payment")).containsExactly("payment", "order-service");
        assertThat(graph.affectedUpstreams("order-service")).containsExactly("order-service");
    }

    @Test
    void cyclicGraphDoesNotHangImpactComputation() {
        graph.addCallEdge("payment", "order-service"); // 制造环
        assertThat(graph.impactedFeatures("payment")).contains("checkout");
    }

    @Test
    void rejectsBlankNamesAndSelfEdges() {
        assertThatThrownBy(() -> graph.registerDependency(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graph.addCallEdge("a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
