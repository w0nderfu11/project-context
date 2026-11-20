
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.application.enums.TreeEntryType;
import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathViolationException;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GetCurrentTreeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnCurrentDirectoryTree() throws IOException {
        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path nestedDirectory = Files.createDirectory(
                directory.resolve("main")
        );

        Path javaFile = Files.createFile(
                directory.resolve("Example.java")
        );

        Path textFile = Files.createFile(
                directory.resolve("README.txt")
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", directory);

        assertEquals(
                Map.of(
                        nestedDirectory.toString(),
                        TreeEntryType.DIRECTORY,
                        javaFile.toString(),
                        TreeEntryType.FILE,
                        textFile.toString(),
                        TreeEntryType.FILE
                ),
                entries
        );
    }

    @Test
    void shouldReturnOnlyImmediateChildren() throws IOException {
        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path nested = Files.createDirectory(
                directory.resolve("main")
        );

        Path nestedFile = Files.createFile(
                nested.resolve("Example.java")
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", directory);

        assertEquals(
                Map.of(
                        nested.toString(),
                        TreeEntryType.DIRECTORY
                ),
                entries
        );

        assertFalse(
                entries.containsKey(nestedFile.toString())
        );
    }

    @Test
    void shouldReturnEmptyTreeForEmptyDirectory()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("empty")
        );

        assertTrue(
                service(project)
                        .get("project", directory)
                        .isEmpty()
        );
    }

    @Test
    void shouldListProjectRoot() throws IOException {
        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path file = Files.createFile(
                project.resolve("README.md")
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertEquals(
                Map.of(
                        directory.toString(),
                        TreeEntryType.DIRECTORY,
                        file.toString(),
                        TreeEntryType.FILE
                ),
                entries
        );
    }

    @Test
    void shouldListNestedDirectory() throws IOException {
        Path project = project();

        Path directory = Files.createDirectories(
                project.resolve("src/main/java")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", directory);

        assertEquals(
                Map.of(
                        file.toString(),
                        TreeEntryType.FILE
                ),
                entries
        );
    }

    @Test
    void shouldNormalizeCurrentDirectorySegments()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        Path requested = directory.resolve(".");

        Map<String, TreeEntryType> entries =
                service(project).get("project", requested);

        assertEquals(
                Map.of(
                        file.toString(),
                        TreeEntryType.FILE
                ),
                entries
        );
    }

    @Test
    void shouldRejectNullRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetCurrentTreeService(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectName() throws IOException {
        Path project = project();

        GetCurrentTreeService service = service(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.get(null, project)
        );

        assertEquals(
                "projectName must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullDirectoryPath() throws IOException {
        GetCurrentTreeService service = service(project());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.get("project", null)
        );

        assertEquals(
                "directoryPath must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProject() throws IOException {
        Path project = project();

        GetCurrentTreeService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.get("unknown", project)
        );

        assertEquals(
                "project is not registered: unknown",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDirectoryOutsideProjectRoot()
            throws IOException {

        Path project = project();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        GetCurrentTreeService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", outside)
        );

        assertEquals(
                "Path is outside the registered project root",
                exception.getMessage()
        );

        assertFalse(
                exception.getMessage().contains(outside.toString())
        );
    }

    @Test
    void shouldRejectSiblingWithSimilarPrefix()
            throws IOException {

        Path project = project();

        Path sibling = Files.createDirectory(
                tempDir.toRealPath().resolve("project-backup")
        );

        GetCurrentTreeService service = service(project);

        assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", sibling)
        );
    }

    @Test
    void shouldRejectNonExistingDirectory() throws IOException {
        Path project = project();

        Path missing = project.resolve("missing");

        GetCurrentTreeService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.get("project", missing)
        );
    }

    @Test
    void shouldRejectMissingIntermediateDirectory()
            throws IOException {

        Path project = project();

        Path missing = project.resolve("missing/nested");

        GetCurrentTreeService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.get("project", missing)
        );
    }

    @Test
    void shouldRejectFileInsteadOfDirectory()
            throws IOException {

        Path project = project();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        GetCurrentTreeService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.get("project", file)
        );

        assertEquals(
                "directory path must point to a directory: " + file,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRelativeDirectory() throws IOException {
        GetCurrentTreeService service = service(project());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.get(
                        "project",
                        Path.of("src/main")
                )
        );

        assertEquals(
                "requestedPath must be absolute",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectTraversalSegments() throws IOException {
        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path requested = directory.resolve("../src");

        GetCurrentTreeService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.get("project", requested)
        );

        assertEquals(
                "requestedPath must not contain '..' segments",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectExternalSymlinkDirectoryAsInput()
            throws IOException {

        Path project = project();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        Path link = createSymlink(
                project.resolve("external-link"),
                outside
        );

        assertSymlinkInputRejected(
                service(project),
                link
        );
    }

    @Test
    void shouldRejectInternalSymlinkDirectoryAsInput()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path link = createSymlink(
                project.resolve("internal-link"),
                directory
        );

        assertSymlinkInputRejected(
                service(project),
                link
        );
    }

    @Test
    void shouldRejectIntermediateSymlinkDirectory()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Files.createDirectory(
                directory.resolve("nested")
        );

        Path link = createSymlink(
                project.resolve("linked-src"),
                directory
        );

        assertSymlinkInputRejected(
                service(project),
                link.resolve("nested")
        );
    }

    @Test
    void shouldSkipExternalSymlinkFileEntry()
            throws IOException {

        Path project = project();

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("secret.txt")
        );

        Path link = createSymlink(
                project.resolve("linked-secret.txt"),
                outsideFile
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertFalse(
                entries.containsKey(link.toString())
        );

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldSkipInternalSymlinkFileEntry()
            throws IOException {

        Path project = project();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        Path link = createSymlink(
                project.resolve("linked-example.java"),
                file
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertEquals(
                Map.of(
                        file.toString(),
                        TreeEntryType.FILE
                ),
                entries
        );

        assertFalse(
                entries.containsKey(link.toString())
        );
    }

    @Test
    void shouldSkipExternalSymlinkDirectoryEntry()
            throws IOException {

        Path project = project();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        Path link = createSymlink(
                project.resolve("external-directory"),
                outside
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertFalse(
                entries.containsKey(link.toString())
        );

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldSkipInternalSymlinkDirectoryEntry()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path link = createSymlink(
                project.resolve("linked-src"),
                directory
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertEquals(
                Map.of(
                        directory.toString(),
                        TreeEntryType.DIRECTORY
                ),
                entries
        );

        assertFalse(
                entries.containsKey(link.toString())
        );
    }

    @Test
    void shouldSkipDanglingSymlinkEntry()
            throws IOException {

        Path project = project();

        createSymlink(
                project.resolve("broken-link"),
                project.resolve("missing.txt")
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertTrue(entries.isEmpty());
        assertFalse(false);
    }

    @Test
    void shouldSkipSymbolicLinkCycleEntry()
            throws IOException {

        Path project = project();

        Path link = project.resolve("cycle-link");

        createSymlink(link, link);

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldPreserveValidEntriesAlongsideUnsafeEntries()
            throws IOException {

        Path project = project();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("secret.txt")
        );

        Path link = createSymlink(
                project.resolve("linked-secret.txt"),
                outsideFile
        );

        Map<String, TreeEntryType> entries =
                service(project).get("project", project);

        assertEquals(
                Map.of(
                        file.toString(),
                        TreeEntryType.FILE,
                        directory.toString(),
                        TreeEntryType.DIRECTORY
                ),
                entries
        );

        assertFalse(entries.containsKey(link.toString()));
    }

    @Test
    void shouldRejectProjectRootReplacedWithFile()
            throws IOException {

        Path project = project();
        GetCurrentTreeService service = service(project);

        Files.delete(project);
        Files.createFile(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", project)
        );

        assertEquals(
                "Registered project root is not a directory",
                exception.getMessage()
        );
    }

    private Path project() throws IOException {
        return Files.createDirectory(
                tempDir.toRealPath().resolve("project")
        );
    }

    private GetCurrentTreeService service(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new GetCurrentTreeService(registry);
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

    private void assertSymlinkInputRejected(
            GetCurrentTreeService service,
            Path requestedPath
    ) {
        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", requestedPath)
        );

        assertEquals(
                "Symbolic links are not allowed",
                exception.getMessage()
        );
    }
}