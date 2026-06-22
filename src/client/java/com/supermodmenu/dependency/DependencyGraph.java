package com.supermodmenu.dependency;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.util.*;

/**
 * Builds a dependency graph from all loaded mods.
 * Each node is a mod ID; edges represent "depends on" relationships.
 */
public class DependencyGraph {

    public record Edge(String from, String to, ModDependency.Kind kind) {}

    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
    private final Set<String> nodes = new LinkedHashSet<>();

    private DependencyGraph() {}

    public static DependencyGraph build() {
        DependencyGraph graph = new DependencyGraph();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            graph.nodes.add(id);
            graph.adjacency.putIfAbsent(id, new ArrayList<>());

            for (ModDependency dep : mod.getMetadata().getDependencies()) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS ||
                    dep.getKind() == ModDependency.Kind.RECOMMENDS) {
                    Edge edge = new Edge(id, dep.getModId(), dep.getKind());
                    graph.adjacency.get(id).add(edge);
                    graph.nodes.add(dep.getModId());
                }
            }
        }
        return graph;
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    /** Returns all edges going OUT from a given mod (what it depends on). */
    public List<Edge> getDependenciesOf(String modId) {
        return Collections.unmodifiableList(adjacency.getOrDefault(modId, List.of()));
    }

    /** Returns all mods that depend ON the given mod. */
    public List<String> getDependentsOf(String modId) {
        List<String> dependents = new ArrayList<>();
        for (Map.Entry<String, List<Edge>> entry : adjacency.entrySet()) {
            for (Edge edge : entry.getValue()) {
                if (edge.to().equals(modId) && edge.kind() == ModDependency.Kind.DEPENDS) {
                    dependents.add(entry.getKey());
                }
            }
        }
        return dependents;
    }

    /** Returns a simple text representation of the graph for debugging. */
    public String toTextTree(String rootModId) {
        StringBuilder sb = new StringBuilder();
        buildTree(rootModId, sb, 0, new HashSet<>());
        return sb.toString();
    }

    private void buildTree(String modId, StringBuilder sb, int depth, Set<String> visited) {
        sb.append("  ".repeat(depth)).append("- ").append(modId).append("\n");
        if (visited.contains(modId)) return;
        visited.add(modId);
        for (Edge edge : getDependenciesOf(modId)) {
            buildTree(edge.to(), sb, depth + 1, visited);
        }
    }
}
