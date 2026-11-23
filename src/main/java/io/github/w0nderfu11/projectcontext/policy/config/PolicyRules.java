package io.github.w0nderfu11.projectcontext.policy.config;

import io.github.w0nderfu11.projectcontext.policy.enums.RuleType;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record PolicyRules(
        List<String> fileNames,
        List<String> extensions,
        List<String> filePaths,
        List<String> directoryPaths
) {

    private static final PolicyRules EMPTY = new PolicyRules(
            List.of(),
            List.of(),
            List.of(),
            List.of()
    );

    public PolicyRules {
        fileNames = validateRules(RuleType.FILE_NAME, fileNames);
        extensions = validateRules(RuleType.EXTENSION, extensions);
        filePaths = validateRules(RuleType.FILE_PATH, filePaths);
        directoryPaths = validateRules(RuleType.DIRECTORY_PATH, directoryPaths);
    }

    public static PolicyRules empty() {
        return EMPTY;
    }

    List<String> values(RuleType type) {
        return switch (type) {
            case FILE_NAME -> fileNames;
            case EXTENSION -> extensions;
            case FILE_PATH -> filePaths;
            case DIRECTORY_PATH -> directoryPaths;
        };
    }

    PolicyRules withValues(RuleType type, List<String> values) {
        return switch (type) {
            case FILE_NAME -> new PolicyRules(
                    values,
                    extensions,
                    filePaths,
                    directoryPaths
            );
            case EXTENSION -> new PolicyRules(
                    fileNames,
                    values,
                    filePaths,
                    directoryPaths
            );
            case FILE_PATH -> new PolicyRules(
                    fileNames,
                    extensions,
                    values,
                    directoryPaths
            );
            case DIRECTORY_PATH -> new PolicyRules(
                    fileNames,
                    extensions,
                    filePaths,
                    values
            );
        };
    }

    private static List<String> validateRules(
            RuleType type,
            List<String> values
    ) {
        Objects.requireNonNull(values, type + " rules must not be null");

        Set<String> rules = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (String value : values) {
            String normalized = normalizeValue(type, value);

            if (!rules.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate " + type + " rule: " + normalized
                );
            }
        }

        return List.copyOf(rules);
    }

    static String normalizeValue(RuleType type, String value) {
        Objects.requireNonNull(value, "rule value must not be null");

        if (type == RuleType.FILE_PATH
                || type == RuleType.DIRECTORY_PATH) {
            return normalizePath(type, value);
        }

        validateName(value);

        if (type == RuleType.EXTENSION && (
                !value.startsWith(".")
                        || value.lastIndexOf('.') != 0
                        || value.endsWith(" ")
        )) {
            throw new IllegalArgumentException(
                    "extension must be a single suffix with a leading dot"
            );
        }

        return value;
    }

    private static String normalizePath(RuleType type, String value) {
        String path = value.replace(File.separatorChar, '/');

        if (path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "rule path must be relative"
            );
        }

        if (type == RuleType.FILE_PATH
                && (path.endsWith("/") || path.endsWith("/."))) {
            throw new IllegalArgumentException(
                    "file path must end in a file name"
            );
        }

        List<String> components = new ArrayList<>();

        for (String component : path.split("/")) {
            if (component.isEmpty() || component.equals(".")) {
                continue;
            }

            if (component.equals("..")) {
                throw new IllegalArgumentException(
                        "rule path must not contain '..' segments"
                );
            }

            validateName(component);
            components.add(component);
        }

        if (components.isEmpty()) {
            throw new IllegalArgumentException(
                    "rule path must name a project entry"
            );
        }

        return String.join("/", components);
    }

    private static void validateName(String name) {
        boolean invalidWindowsSuffix = File.separatorChar == '\\'
                && (name.endsWith(".") || name.endsWith(" "));

        if (invalidWindowsSuffix
                || name.isBlank()
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf(File.separatorChar) >= 0
                || Path.of(name).getRoot() != null) {
            throw new IllegalArgumentException(
                    "invalid rule name or path component: " + name
            );
        }
    }
}
