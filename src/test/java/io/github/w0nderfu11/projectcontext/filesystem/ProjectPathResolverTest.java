
package io.github.w0nderfu11.projectcontext.filesystem;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveProjectRoot() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        assertEquals(
                project,
                resolver.getRoot("project")
        );

        assertEquals(
                project,
                resolver.resolve("project", project)
        );
    }

    @Test
    void shouldResolveRegularFile() throws IOException {
        Path project = createProject();

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "class Example {}"
        );

        ProjectPathResolver resolver = resolver(project);

        assertEquals(
                file.toRealPath(),
                resolver.resolve("project", file)
        );
    }

    @Test
    void shouldResolveNestedFile() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectories(
                project.resolve("src/main/java")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        ProjectPathResolver resolver = resolver(project);

        assertEquals(
                file.toRealPath(),
                resolver.resolve("project", file)
        );
    }

    @Test
    void shouldResolveDirectory() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectories(
                project.resolve("src/main")
        );

        ProjectPathResolver resolver = resolver(project);

        assertEquals(
                directory.toRealPath(),
                resolver.resolve("project", directory)
        );
    }

    @Test
    void shouldNormalizeCurrentDirectorySegments() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        Path requestedPath = directory
                .resolve(".")
                .resolve("Example.java");

        ProjectPathResolver resolver = resolver(project);

        assertEquals(
                file.toRealPath(),
                resolver.resolve("project", requestedPath)
        );
    }

    @Test
    void shouldResolvePathsForMultipleProjects() throws IOException {
        Path first = createProject();

        Path second = Files.createDirectory(
                tempDir.toRealPath().resolve("second")
        );

        Path firstFile = Files.createFile(
                first.resolve("first.txt")
        );

        Path secondFile = Files.createFile(
                second.resolve("second.txt")
        );

        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "first", first,
                        "second", second
                )
        );

        ProjectPathResolver resolver =
                new ProjectPathResolver(registry);

        assertEquals(
                firstFile.toRealPath(),
                resolver.resolve("first", firstFile)
        );

        assertEquals(
                secondFile.toRealPath(),
                resolver.resolve("second", secondFile)
        );

        assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("first", secondFile)
        );
    }

    @Test
    void shouldRejectNullRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectPathResolver(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectName() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> resolver.resolve(null, project)
        );

        assertEquals(
                "project name must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankProjectName() throws IOException {
        ProjectPathResolver resolver = resolver(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.getRoot(" ")
        );

        assertEquals(
                "project name must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProject() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("unknown", project)
        );

        assertEquals(
                "project is not registered: unknown",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRequestedPath() throws IOException {
        ProjectPathResolver resolver = resolver(createProject());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> resolver.resolve("project", null)
        );

        assertEquals(
                "requestedPath must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRelativePath() throws IOException {
        ProjectPathResolver resolver = resolver(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        "project",
                        Path.of("src/Example.java")
                )
        );

        assertEquals(
                "requestedPath must be absolute",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectTraversalInsideProject() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        Path requestedPath = project
                .resolve("src")
                .resolve("..")
                .resolve(file.getFileName());

        ProjectPathResolver resolver = resolver(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("project", requestedPath)
        );

        assertEquals(
                "requestedPath must not contain '..' segments",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectTraversalOutsideProject() throws IOException {
        Path project = createProject();

        Path requestedPath = project
                .resolve("..")
                .resolve("outside.txt");

        ProjectPathResolver resolver = resolver(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("project", requestedPath)
        );

        assertEquals(
                "requestedPath must not contain '..' segments",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileOutsideProjectRoot() throws IOException {
        Path project = createProject();

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("outside.txt")
        );

        ProjectPathResolver resolver = resolver(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", outsideFile)
        );

        assertEquals(
                "Path is outside the registered project root",
                exception.getMessage()
        );

        assertFalse(
                exception.getMessage().contains(
                        outsideFile.toString()
                )
        );
    }

    @Test
    void shouldRejectDirectoryOutsideProjectRoot() throws IOException {
        Path project = createProject();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        ProjectPathResolver resolver = resolver(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", outside)
        );

        assertEquals(
                "Path is outside the registered project root",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSiblingWithSimilarPrefix() throws IOException {
        Path project = createProject();

        Path sibling = Files.createDirectory(
                tempDir.toRealPath().resolve("project-backup")
        );

        Path file = Files.createFile(
                sibling.resolve("secret.txt")
        );

        ProjectPathResolver resolver = resolver(project);

        assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", file)
        );
    }

    @Test
    void shouldRejectMissingFile() throws IOException {
        Path project = createProject();
        Path missing = project.resolve("missing.txt");

        ProjectPathResolver resolver = resolver(project);

        assertThrows(
                NoSuchFileException.class,
                () -> resolver.resolve("project", missing)
        );
    }

    @Test
    void shouldRejectMissingIntermediateDirectory() throws IOException {
        Path project = createProject();

        Path missing = project
                .resolve("missing")
                .resolve("Example.java");

        ProjectPathResolver resolver = resolver(project);

        assertThrows(
                NoSuchFileException.class,
                () -> resolver.resolve("project", missing)
        );
    }

    @Test
    void shouldRejectFileAsIntermediateComponent() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("file.txt")
        );

        Path requestedPath = file.resolve("child.txt");

        ProjectPathResolver resolver = resolver(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", requestedPath)
        );

        assertEquals(
                "Intermediate path component is not a directory",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSymlinkToExternalFile() throws IOException {
        Path project = createProject();

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("secret.txt")
        );

        Path link = createSymlink(
                project.resolve("external-file-link"),
                outsideFile
        );

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectSymlinkToInternalFile() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        Path link = createSymlink(
                project.resolve("internal-file-link"),
                file
        );

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectSymlinkToExternalDirectory() throws IOException {
        Path project = createProject();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        Path link = createSymlink(
                project.resolve("external-directory-link"),
                outside
        );

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectSymlinkToInternalDirectory() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path link = createSymlink(
                project.resolve("internal-directory-link"),
                directory
        );

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectIntermediateSymlink() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("real-directory")
        );

        Files.createFile(
                directory.resolve("Example.java")
        );

        Path link = createSymlink(
                project.resolve("linked-directory"),
                directory
        );

        Path requestedPath = link.resolve("Example.java");

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, requestedPath);
    }

    @Test
    void shouldRejectDanglingSymlink() throws IOException {
        Path project = createProject();

        Path missingTarget = project.resolve("missing.txt");

        Path link = createSymlink(
                project.resolve("broken-link"),
                missingTarget
        );

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectSymbolicLinkCycle() throws IOException {
        Path project = createProject();

        Path link = project.resolve("cycle-link");

        createSymlink(link, link);

        ProjectPathResolver resolver = resolver(project);

        assertSymlinkRejected(resolver, link);
    }

    @Test
    void shouldRejectRootReplacedWithFile() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        Files.delete(project);
        Files.createFile(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", project)
        );

        assertEquals(
                "Registered project root is not a directory",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRootReplacedWithSymlink() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        Path replacement = Files.createDirectory(
                tempDir.toRealPath().resolve("replacement")
        );

        Files.delete(project);
        createSymlink(project, replacement);

        assertSymlinkRejected(resolver, project);
    }

    @Test
    void shouldRejectDeletedProjectRoot() throws IOException {
        Path project = createProject();
        ProjectPathResolver resolver = resolver(project);

        Files.delete(project);

        assertThrows(
                NoSuchFileException.class,
                () -> resolver.resolve("project", project)
        );
    }

    private Path createProject() throws IOException {
        return Files.createDirectory(
                tempDir.toRealPath().resolve("project")
        );
    }

    private ProjectPathResolver resolver(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new ProjectPathResolver(registry);
    }

    private Path createSymlink(Path link, Path target) {

        try {
            return Files.createSymbolicLink(link, target);
        } catch (
                UnsupportedOperationException
                | IOException
                | SecurityException exception
        ) {
            assumeTrue(
                    false,
                    "Symbolic links are not available: "
                            + exception.getClass().getSimpleName()
            );

            throw new AssertionError("Unreachable");
        }
    }

    private void assertSymlinkRejected(
            ProjectPathResolver resolver,
            Path requestedPath
    ) {
        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> resolver.resolve("project", requestedPath)
        );

        assertEquals(
                "Symbolic links are not allowed",
                exception.getMessage()
        );
    }
}
