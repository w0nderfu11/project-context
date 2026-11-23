package io.github.w0nderfu11.projectcontext.policy;

import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathViolationException;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.policy.enums.PolicyScope;
import io.github.w0nderfu11.projectcontext.policy.enums.RuleType;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ProjectAccessPolicy {

    private final ProjectRegistry projectRegistry;
    private final PolicyConfiguration configuration;

    public ProjectAccessPolicy(
            ProjectRegistry projectRegistry,
            PolicyConfiguration configuration
    ) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null"
        );

        configuration.validateProjects(projectRegistry);
    }

    public boolean isExcluded(String projectName, Path validatedPath)
            throws IOException {

        return findMatch(projectName, validatedPath) != null;
    }

    public void checkAccess(String projectName, Path validatedPath)
            throws IOException {

        MatchingRule match = findMatch(projectName, validatedPath);

        if (match != null) {
            throw new ProjectAccessDeniedException(
                    "Access denied by " + match.scope() + " "
                            + match.type() + " rule: " + match.value()
            );
        }
    }

    private MatchingRule findMatch(String projectName, Path validatedPath)
            throws IOException {

        Path root = projectRegistry.getRoot(projectName);

        Objects.requireNonNull(
                validatedPath,
                "validatedPath must not be null"
        );

        if (!validatedPath.startsWith(root)
                || !validatedPath.equals(validatedPath.normalize())) {
            throw new IllegalArgumentException(
                    "validatedPath must be a normalized absolute path within the project root"
            );
        }

        BasicFileAttributes attributes = Files.readAttributes(
                validatedPath,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );

        if (attributes.isSymbolicLink()) {
            throw new ProjectPathViolationException(
                    "Symbolic links are not allowed"
            );
        }

        if (!attributes.isRegularFile()
                && !attributes.isDirectory()) {
            throw new ProjectPathViolationException(
                    "Unsupported filesystem entry type"
            );
        }

        if (validatedPath.equals(root)) {
            if (!attributes.isDirectory()) {
                throw new ProjectPathViolationException(
                        "Registered project root is not a directory"
                );
            }

            return null;
        }

        PolicyRules global = configuration.global();
        PolicyRules project = configuration.projects()
                .getOrDefault(projectName, PolicyRules.empty());

        if (global.equals(PolicyRules.empty())
                && project.equals(PolicyRules.empty())) {
            return null;
        }

        Path relativePath = root.relativize(validatedPath);
        boolean directory = attributes.isDirectory();

        MatchingRule match = findMatch(
                PolicyScope.GLOBAL,
                global,
                relativePath,
                directory
        );

        if (match != null) {
            return match;
        }

        return findMatch(
                PolicyScope.PROJECT,
                project,
                relativePath,
                directory
        );
    }

    private MatchingRule findMatch(
            PolicyScope scope,
            PolicyRules rules,
            Path relativePath,
            boolean directory
    ) {
        if (!rules.directoryPaths().isEmpty()) {
            Path parent = directory ? relativePath : relativePath.getParent();

            while (parent != null) {
                MatchingRule match = matchRule(
                        scope,
                        RuleType.DIRECTORY_PATH,
                        rules.directoryPaths(),
                        portablePath(parent)
                );

                if (match != null) {
                    return match;
                }

                parent = parent.getParent();
            }
        }

        if (directory) {
            return null;
        }

        MatchingRule match = matchRule(
                scope,
                RuleType.FILE_PATH,
                rules.filePaths(),
                portablePath(relativePath)
        );

        if (match != null) {
            return match;
        }

        String fileName = relativePath.getFileName().toString();

        match = matchRule(
                scope,
                RuleType.FILE_NAME,
                rules.fileNames(),
                fileName
        );

        if (match != null) {
            return match;
        }

        int lastDot = fileName.lastIndexOf('.');

        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return null;
        }

        return matchRule(
                scope,
                RuleType.EXTENSION,
                rules.extensions(),
                fileName.substring(lastDot)
        );
    }

    private static String portablePath(Path path) {
        return path.toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }

    private static MatchingRule matchRule(
            PolicyScope scope,
            RuleType type,
            List<String> rules,
            String candidate
    ) {
        int index = Collections.binarySearch(
                rules,
                candidate,
                String.CASE_INSENSITIVE_ORDER
        );

        if (index < 0) {
            return null;
        }

        return new MatchingRule(
                scope,
                type,
                rules.get(index)
        );
    }

    private record MatchingRule(
            PolicyScope scope,
            RuleType type,
            String value
    ) {
    }
}
