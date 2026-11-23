package io.github.w0nderfu11.projectcontext.policy.config;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record PolicyConfiguration(
        PolicyRules global,
        Map<String, PolicyRules> projects
) {

    public PolicyConfiguration {
        Objects.requireNonNull(global, "global must not be null");
        Objects.requireNonNull(projects, "projects must not be null");

        Map<String, PolicyRules> projectRules = new TreeMap<>();

        for (Map.Entry<String, PolicyRules> entry : projects.entrySet()) {
            String name = Objects.requireNonNull(
                    entry.getKey(),
                    "project name must not be null"
            );

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "project name must not be blank"
                );
            }

            PolicyRules rules = Objects.requireNonNull(
                    entry.getValue(),
                    "project rules must not be null"
            );

            projectRules.put(name, rules);
        }

        projects = Collections.unmodifiableMap(projectRules);
    }

    public static PolicyConfiguration empty() {
        return new PolicyConfiguration(PolicyRules.empty(), Map.of());
    }

    public void validateProjects(ProjectRegistry projectRegistry) {
        Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );

        projects.keySet().forEach(projectRegistry::getRoot);
    }
}
