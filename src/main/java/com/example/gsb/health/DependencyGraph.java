package com.example.gsb.health;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图：登记下游依赖、依赖之间的调用边（上游聚合服务 -&gt; 下游），
 * 以及业务功能到依赖的映射（功能 -&gt; 直接依赖）。
 *
 * <p>影响面 = 某依赖的所有（传递）上游功能；传播沿调用边自下游向上游进行。
 */
public class DependencyGraph {

    /** 调用边：key 为上游，value 为其直接调用的下游集合。 */
    private final Map<String, Set<String>> callEdges = new LinkedHashMap<>();
    /** 功能 -&gt; 直接依赖集合。 */
    private final Map<String, Set<String>> featureDeps = new LinkedHashMap<>();
    private final Set<String> dependencies = new LinkedHashSet<>();

    /** 登记一个依赖（下游服务或上游聚合服务）。 */
    public void registerDependency(String name) {
        dependencies.add(requireName(name, "dependency"));
    }

    /** 登记调用关系：upstream 调用 downstream，两端自动登记。 */
    public void addCallEdge(String upstream, String downstream) {
        requireName(upstream, "upstream");
        requireName(downstream, "downstream");
        if (upstream.equals(downstream)) {
            throw new IllegalArgumentException("self call edge is not allowed: " + upstream);
        }
        registerDependency(upstream);
        registerDependency(downstream);
        callEdges.computeIfAbsent(upstream, k -> new LinkedHashSet<>()).add(downstream);
    }

    /** 登记业务功能对某依赖的直接依赖关系，依赖自动登记。 */
    public void mapFeature(String feature, String dependency) {
        requireName(feature, "feature");
        registerDependency(dependency);
        featureDeps.computeIfAbsent(feature, k -> new LinkedHashSet<>()).add(dependency);
    }

    public Set<String> dependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<String> features() {
        return Collections.unmodifiableSet(featureDeps.keySet());
    }

    /** upstream 直接调用的下游集合。 */
    public Set<String> directDownstreamsOf(String upstream) {
        return callEdges.getOrDefault(upstream, Set.of());
    }

    /** 功能的直接依赖集合。 */
    public Set<String> directDependenciesOf(String feature) {
        return featureDeps.getOrDefault(feature, Set.of());
    }

    /**
     * 依赖的传递闭包：从 dependency 出发沿调用边向下可达的所有依赖（含自身）。
     * 对环安全（visited 去重）。
     */
    public Set<String> transitiveClosure(String dependency) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(dependency);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (String next : directDownstreamsOf(current)) {
                stack.push(next);
            }
        }
        return visited;
    }

    /**
     * 影响面计算：返回（传递）依赖于 dependency 的全部业务功能。
     * 即：功能的任一传递依赖等于 dependency 时，该功能受影响。
     */
    public Set<String> impactedFeatures(String dependency) {
        Set<String> impacted = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : featureDeps.entrySet()) {
            for (String direct : entry.getValue()) {
                if (transitiveClosure(direct).contains(dependency)) {
                    impacted.add(entry.getKey());
                    break;
                }
            }
        }
        return impacted;
    }

    /**
     * 依赖传递影响到的上游服务集合（含自身）：沿调用边反向可达的所有节点。
     */
    public Set<String> affectedUpstreams(String dependency) {
        Map<String, Set<String>> reverse = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : callEdges.entrySet()) {
            for (String down : entry.getValue()) {
                reverse.computeIfAbsent(down, k -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(dependency);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (String up : reverse.getOrDefault(current, Set.of())) {
                stack.push(up);
            }
        }
        return visited;
    }

    private static String requireName(String name, String what) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(what + " name must not be blank");
        }
        return name;
    }
}
