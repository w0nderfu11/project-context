
package io.github.w0nderfu11.projectcontext.filesystem;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public final class ProjectPathResolver {

    private final ProjectRegistry projectRegistry;

    public ProjectPathResolver(ProjectRegistry projectRegistry) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );
    }

    public Path getRoot(String projectName) {
        return projectRegistry.getRoot(projectName);
    }

    public Path resolve(String projectName, Path requestedPath)
            throws IOException {

        Path rootPath = getRoot(projectName);

        Objects.requireNonNull(
                requestedPath,
                "requestedPath must not be null"
        );

        validateRequestedPath(rootPath, requestedPath);
        validateRoot(rootPath);

        Path normalizedPath = requestedPath.normalize();

        validateComponents(rootPath, normalizedPath);

        Path realPath = normalizedPath.toRealPath();

        validateCanonicalPath(
                rootPath,
                normalizedPath,
                realPath
        );

        return realPath;
    }

    private void validateRequestedPath(
            Path rootPath,
            Path requestedPath
    ) throws ProjectPathViolationException {

        if (!requestedPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    "requestedPath must be absolute"
            );
        }

        for (Path component : requestedPath) {
            if ("..".equals(component.toString())) {
                throw new IllegalArgumentException(
                        "requestedPath must not contain '..' segments"
                );
            }
        }

        if (!requestedPath.normalize().startsWith(rootPath)) {
            throw new ProjectPathViolationException(
                    "Path is outside the registered project root"
            );
        }
    }

    private void validateRoot(Path rootPath)
            throws IOException {

        BasicFileAttributes attributes = Files.readAttributes(
                rootPath,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );

        if (attributes.isSymbolicLink()) {
            throw new ProjectPathViolationException(
                    "Symbolic links are not allowed"
            );
        }

        if (!attributes.isDirectory()) {
            throw new ProjectPathViolationException(
                    "Registered project root is not a directory"
            );
        }

        Path realRootPath = rootPath.toRealPath();

        if (!realRootPath.equals(rootPath)) {
            throw new ProjectPathViolationException(
                    "Unsafe project root redirection detected"
            );
        }
    }

    private void validateComponents(
            Path rootPath,
            Path requestedPath
    ) throws IOException {

        if (rootPath.equals(requestedPath)) {
            return;
        }

        Path relativePath = rootPath.relativize(requestedPath);
        Path currentPath = rootPath;

        for (Path component : relativePath) {
            currentPath = currentPath.resolve(component);

            BasicFileAttributes attributes = Files.readAttributes(
                    currentPath,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );

            if (attributes.isSymbolicLink()) {
                throw new ProjectPathViolationException(
                        "Symbolic links are not allowed"
                );
            }

            if (!attributes.isDirectory()
                    && !attributes.isRegularFile()) {
                throw new ProjectPathViolationException(
                        "Unsupported filesystem entry type"
                );
            }

            if (!currentPath.equals(requestedPath)
                    && !attributes.isDirectory()) {
                throw new ProjectPathViolationException(
                        "Intermediate path component is not a directory"
                );
            }

            Path realComponentPath = currentPath.toRealPath();

            if (!realComponentPath.startsWith(rootPath)) {
                throw new ProjectPathViolationException(
                        "Path is outside the registered project root"
                );
            }

            if (!realComponentPath.equals(currentPath)) {
                throw new ProjectPathViolationException(
                        "Unsafe path redirection detected"
                );
            }
        }
    }

    private void validateCanonicalPath(
            Path rootPath,
            Path requestedPath,
            Path realPath
    ) throws ProjectPathViolationException {

        if (!realPath.startsWith(rootPath)) {
            throw new ProjectPathViolationException(
                    "Path is outside the registered project root"
            );
        }

        if (!realPath.equals(requestedPath)) {
            throw new ProjectPathViolationException(
                    "Unsafe path redirection detected"
            );
        }
    }
}
