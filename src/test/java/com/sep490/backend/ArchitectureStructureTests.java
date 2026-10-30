package com.sep490.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureStructureTests {
    private static final String BASE_PACKAGE = "com.sep490.backend";
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/sep490/backend");
    private static final Set<String> MODULES = Set.of(
            "admin", "ai", "auth", "explore", "learning", "vocabulary");
    private static final Map<String, Set<String>> LEARNING_SLICES = Map.of(
            "authoring", Set.of("application", "web"),
            "course", Set.of("application", "domain", "infrastructure", "web"),
            "grammar", Set.of("application", "web"),
            "importing", Set.of("controller", "dto", "exception", "repository", "service", "web"),
            "lesson", Set.of("application", "domain", "infrastructure", "web"),
            "path", Set.of("application", "web"),
            "progress", Set.of("application", "domain", "infrastructure", "port", "web"),
            "quiz", Set.of("application", "web"),
            "shared", Set.of("application", "infrastructure", "web"));
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final Pattern MODULE_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.sep490\\.backend\\.modules\\.([^.]+)[.;]");

    @Test
    void productionSourcesFollowPackageByFeatureLayout() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : productionSources()) {
            Path relative = SOURCE_ROOT.relativize(source);
            String expectedPackage = BASE_PACKAGE;
            if (relative.getParent() != null) {
                expectedPackage += "." + relative.getParent().toString().replace('\\', '.').replace('/', '.');
            }

            String content = Files.readString(source);
            var packageMatcher = PACKAGE.matcher(content);
            if (!packageMatcher.find() || !expectedPackage.equals(packageMatcher.group(1))) {
                violations.add(relative + " must declare package " + expectedPackage);
            }

            if (relative.getNameCount() == 1) {
                if (!"BackendApplication.java".equals(relative.toString())) {
                    violations.add(relative + " is not an allowed root class");
                }
                continue;
            }

            String topLevel = relative.getName(0).toString();
            if (!Set.of("common", "modules").contains(topLevel)) {
                violations.add(relative + " uses a legacy package-by-layer directory");
            } else if ("modules".equals(topLevel)
                    && (relative.getNameCount() < 3 || !MODULES.contains(relative.getName(1).toString()))) {
                violations.add(relative + " is not owned by a known feature module");
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void commonDoesNotDependOnFeatureModules() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            Path relative = SOURCE_ROOT.relativize(source);
            if (relative.getNameCount() > 1 && "common".equals(relative.getName(0).toString())) {
                var matcher = MODULE_IMPORT.matcher(Files.readString(source));
                if (matcher.find()) {
                    violations.add(relative + " imports module " + matcher.group(1));
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void learningUsesCapabilityFirstPackages() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : productionSources()) {
            Path relative = SOURCE_ROOT.relativize(source);
            if (relative.getNameCount() < 3
                    || !"modules".equals(relative.getName(0).toString())
                    || !"learning".equals(relative.getName(1).toString())) {
                continue;
            }

            if (relative.getNameCount() < 5) {
                violations.add(relative + " must belong to a learning capability and layer");
                continue;
            }

            String capability = relative.getName(2).toString();
            String layer = relative.getName(3).toString();
            Set<String> allowedLayers = LEARNING_SLICES.get(capability);
            if (allowedLayers == null) {
                violations.add(relative + " uses unknown learning capability " + capability);
            } else if (!allowedLayers.contains(layer)) {
                violations.add(relative + " uses invalid layer " + layer + " for " + capability);
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void featureModuleDependenciesAreAcyclic() throws IOException {
        Map<String, Set<String>> dependencies = new HashMap<>();
        MODULES.forEach(module -> dependencies.put(module, new HashSet<>()));

        for (Path source : productionSources()) {
            Path relative = SOURCE_ROOT.relativize(source);
            if (relative.getNameCount() < 3 || !"modules".equals(relative.getName(0).toString())) {
                continue;
            }
            String owner = relative.getName(1).toString();
            var matcher = MODULE_IMPORT.matcher(Files.readString(source));
            while (matcher.find()) {
                String dependency = matcher.group(1);
                if (!owner.equals(dependency)) {
                    dependencies.get(owner).add(dependency);
                }
            }
        }

        Map<String, Integer> incoming = new HashMap<>();
        MODULES.forEach(module -> incoming.put(module, 0));
        dependencies.values().forEach(targets -> targets.forEach(
                target -> incoming.computeIfPresent(target, (ignored, count) -> count + 1)));

        var ready = new ArrayDeque<String>();
        incoming.forEach((module, count) -> {
            if (count == 0) {
                ready.add(module);
            }
        });

        int visited = 0;
        while (!ready.isEmpty()) {
            String module = ready.remove();
            visited++;
            for (String dependency : dependencies.get(module)) {
                int remaining = incoming.computeIfPresent(dependency, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(dependency);
                }
            }
        }

        assertEquals(MODULES.size(), visited, "Feature dependency cycle: " + dependencies);
    }

    private static List<Path> productionSources() throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
