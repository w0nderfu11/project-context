package io.github.w0nderfu11.projectcontext.policy.config;

import io.github.w0nderfu11.projectcontext.policy.enums.PolicyScope;
import io.github.w0nderfu11.projectcontext.policy.enums.RuleType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PolicyConfigurationService {

    private final PolicyConfigurationStore store;
    private PolicyConfiguration configuration;

    public PolicyConfigurationService(PolicyConfigurationStore store)
            throws IOException {

        this.store = Objects.requireNonNull(
                store,
                "store must not be null"
        );
        this.configuration = store.load();
    }

    public synchronized PolicyConfiguration getConfiguration() {
        return configuration;
    }

    public void addRule(
            PolicyScope scope,
            String projectName,
            RuleType type,
            String value
    ) throws IOException {

        updateRule(
                scope,
                projectName,
                type,
                value,
                true
        );
    }

    public void removeRule(
            PolicyScope scope,
            String projectName,
            RuleType type,
            String value
    ) throws IOException {

        updateRule(
                scope,
                projectName,
                type,
                value,
                false
        );
    }

    private synchronized void updateRule(
            PolicyScope scope,
            String projectName,
            RuleType type,
            String value,
            boolean add
    ) throws IOException {

        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");

        PolicyRules rules = getRules(scope, projectName);
        String normalizedValue = PolicyRules.normalizeValue(type, value);
        List<String> values = new ArrayList<>(rules.values(type));

        if (add) {
            values.add(normalizedValue);
        } else if (!values.removeIf(normalizedValue::equalsIgnoreCase)) {
            throw new IllegalArgumentException(
                    type + " rule does not exist: " + normalizedValue
            );
        }

        PolicyRules updatedRules = rules.withValues(type, values);
        PolicyConfiguration proposedConfiguration = buildUpdatedConfiguration(
                scope,
                projectName,
                updatedRules
        );

        store.save(proposedConfiguration);
        configuration = proposedConfiguration;
    }

    private PolicyConfiguration buildUpdatedConfiguration(
            PolicyScope scope,
            String projectName,
            PolicyRules updatedRules
    ) {
        PolicyConfiguration proposedConfiguration;

        if (scope == PolicyScope.GLOBAL) {
            proposedConfiguration = new PolicyConfiguration(
                    updatedRules,
                    configuration.projects()
            );
        } else {
            Map<String, PolicyRules> projects =
                    new HashMap<>(configuration.projects());

            if (updatedRules.equals(PolicyRules.empty())) {
                projects.remove(projectName);
            } else {
                projects.put(projectName, updatedRules);
            }

            proposedConfiguration = new PolicyConfiguration(
                    configuration.global(),
                    projects
            );
        }
        return proposedConfiguration;
    }

    private PolicyRules getRules(PolicyScope scope, String projectName) {
        if (scope == PolicyScope.GLOBAL) {
            if (projectName != null) {
                throw new IllegalArgumentException(
                        "projectName must be null for GLOBAL rules"
                );
            }

            return configuration.global();
        }

        store.validateProject(projectName);
        return configuration.projects()
                .getOrDefault(projectName, PolicyRules.empty());
    }
}
