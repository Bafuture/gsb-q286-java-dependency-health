package com.example.gsb.health;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图：登记下游依赖、下游之间的调用关系（聚合服务 -> 下游），
 * 以及业务功能对依赖的直接依赖（功能 -> 依赖）。
 *
 * 下游之间的边必须是有向无环图；功能只作为图的入口节点。
 */
final class DependencyGraph {

    private final Set<String> dependencies = new LinkedHashSet<>();
    private final Map<String, Set<String>> downstreamEdges = new LinkedHashMap<>();
    private final Map<String, Set<String>> featureDependencies = new LinkedHashMap<>();

    void registerDependency(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("依赖名不能为空");
        }
        if (dependencies.add(name)) {
            downstreamEdges.put(name, new LinkedHashSet<>());
        }
    }

    /**
     * 登记调用关系：aggregator 调用 downstream（aggregator -> downstream）。
     */
    void registerCall(String aggregator, String downstream) {
        requireDependency(aggregator);
        requireDependency(downstream);
        if (reaches(downstream, aggregator, new HashSet<>())) {
            throw new IllegalArgumentException(
                    "添加调用边 " + aggregator + " -> " + downstream + " 会形成环");
        }
        downstreamEdges.get(aggregator).add(downstream);
    }

    /**
     * 登记业务功能 feature 直接依赖的下游。
     */
    void registerFeature(String feature, String... directDependencies) {
        if (feature == null || feature.isBlank()) {
            throw new IllegalArgumentException("功能名不能为空");
        }
        Set<String> deps = featureDependencies.computeIfAbsent(feature, key -> new LinkedHashSet<>());
        for (String dep : directDependencies) {
            requireDependency(dep);
            deps.add(dep);
        }
    }

    Set<String> dependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    Set<String> features() {
        return Collections.unmodifiableSet(featureDependencies.keySet());
    }

    Set<String> directDownstreams(String dependency) {
        requireDependency(dependency);
        return Collections.unmodifiableSet(downstreamEdges.get(dependency));
    }

    /**
     * 某依赖不健康时的影响面：沿 功能 -> 依赖 -> 下游 边可达该依赖的全部功能。
     */
    Set<String> impactedFeatures(String dependency) {
        requireDependency(dependency);
        Set<String> impacted = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : featureDependencies.entrySet()) {
            if (reachableDependencies(entry.getValue()).contains(dependency)) {
                impacted.add(entry.getKey());
            }
        }
        return impacted;
    }

    private Set<String> reachableDependencies(Set<String> roots) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (visited.add(current)) {
                stack.addAll(downstreamEdges.getOrDefault(current, Set.of()));
            }
        }
        return visited;
    }

    private boolean reaches(String source, String target, Set<String> visiting) {
        if (source.equals(target)) {
            return true;
        }
        if (!visiting.add(source)) {
            return false;
        }
        for (String downstream : downstreamEdges.getOrDefault(source, Set.of())) {
            if (reaches(downstream, target, visiting)) {
                return true;
            }
        }
        return false;
    }

    private void requireDependency(String name) {
        if (!dependencies.contains(name)) {
            throw new IllegalArgumentException("未登记的依赖: " + name);
        }
    }
}
