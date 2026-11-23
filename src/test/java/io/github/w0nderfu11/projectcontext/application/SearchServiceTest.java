
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathViolationException;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessDeniedException;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFindFileByNameAndExtension() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("ProjectContextApplication.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "ProjectContextApplication",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldFindFileByPartialName() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("ProjectContextApplication.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "ntex",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldIgnoreCaseInFileName() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("ProjectContextApplication.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "CONTEXT",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldMatchExtensionExactlyIgnoringCase() throws IOException {
        Path project = createProject();

        Path javaFile = Files.createFile(
                project.resolve("Example.JAVA")
        );

        Files.createFile(
                project.resolve("Example.javascript")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "JaVa",
                null
        );

        assertEquals(
                List.of(javaFile.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSearchRecursivelyFromProjectRoot() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectories(
                project.resolve("src/main/java/application")
        );

        Path file = Files.createFile(
                directory.resolve("SearchService.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "search",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSearchOnlyInsideSpecifiedDirectory() throws IOException {
        Path project = createProject();

        Path mainDirectory = Files.createDirectories(
                project.resolve("src/main")
        );

        Path testDirectory = Files.createDirectories(
                project.resolve("src/test")
        );

        Path mainFile = Files.createFile(
                mainDirectory.resolve("Example.java")
        );

        Path testFile = Files.createFile(
                testDirectory.resolve("Example.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                mainDirectory
        );

        assertEquals(
                List.of(mainFile.toRealPath().toString()),
                result
        );

        assertFalse(
                result.contains(testFile.toRealPath().toString())
        );
    }

    @Test
    void shouldReturnMultipleMatches() throws IOException {
        Path project = createProject();

        Path firstFile = Files.createFile(
                project.resolve("ProjectContextApplication.java")
        );

        Path secondFile = Files.createFile(
                project.resolve("ProjectContextMcpServer.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "context",
                "java",
                null
        );

        assertEquals(
                List.of(
                        firstFile.toRealPath().toString(),
                        secondFile.toRealPath().toString()
                ),
                result
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchesFound() throws IOException {
        Path project = createProject();

        Files.createFile(
                project.resolve("Example.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "missing",
                "java",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnSortedMatches() throws IOException {
        Path project = createProject();

        Path second = Files.createFile(
                project.resolve("BExample.java")
        );

        Path first = Files.createFile(
                project.resolve("AExample.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(
                        first.toRealPath().toString(),
                        second.toRealPath().toString()
                ),
                result
        );
    }

    @Test
    void shouldRejectNullRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new SearchService(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectName() throws IOException {
        SearchService service = service(createProject());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.search(
                        null,
                        "example",
                        "java",
                        null
                )
        );

        assertEquals(
                "projectName must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFileName() throws IOException {
        SearchService service = service(createProject());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.search(
                        "project",
                        null,
                        "java",
                        null
                )
        );

        assertEquals(
                "fileName must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankFileName() throws IOException {
        SearchService service = service(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "project",
                        " ",
                        "java",
                        null
                )
        );

        assertEquals(
                "fileName must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullExtension() throws IOException {
        SearchService service = service(createProject());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.search(
                        "project",
                        "example",
                        null,
                        null
                )
        );

        assertEquals(
                "extension must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankExtension() throws IOException {
        SearchService service = service(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "project",
                        "example",
                        " ",
                        null
                )
        );

        assertEquals(
                "extension must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProject() throws IOException {
        SearchService service = service(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "unknown",
                        "example",
                        "java",
                        null
                )
        );

        assertEquals(
                "project is not registered: unknown",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDirectoryOutsideProjectRoot() throws IOException {
        Path project = createProject();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        SearchService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        outside
                )
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
    void shouldRejectNonExistingDirectory() throws IOException {
        Path project = createProject();

        Path missing = project.resolve("missing");

        SearchService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        missing
                )
        );
    }

    @Test
    void shouldRejectFileInsteadOfDirectory() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        SearchService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        file
                )
        );

        assertEquals(
                "directory path must point to a directory: "
                        + file.toRealPath(),
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRelativeDirectory() throws IOException {
        SearchService service = service(createProject());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        Path.of("src/main")
                )
        );

        assertEquals(
                "requestedPath must be absolute",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDirectoryTraversal() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path requestedPath = directory
                .resolve("..")
                .resolve("src");

        SearchService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        requestedPath
                )
        );

        assertEquals(
                "requestedPath must not contain '..' segments",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSymlinkDirectoryOutsideProjectRoot()
            throws IOException {

        Path project = createProject();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        Path link = createSymlink(
                project.resolve("outside-link"),
                outside
        );

        SearchService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        link
                )
        );

        assertEquals(
                "Symbolic links are not allowed",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSymlinkDirectoryInsideProject()
            throws IOException {

        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path link = createSymlink(
                project.resolve("src-link"),
                directory
        );

        SearchService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        link
                )
        );

        assertEquals(
                "Symbolic links are not allowed",
                exception.getMessage()
        );
    }

    @Test
    void shouldSkipExternalSymlinkDirectoryDuringSearch()
            throws IOException {

        Path project = createProject();

        Path outside = Files.createDirectory(
                tempDir.toRealPath().resolve("outside")
        );

        Files.createFile(
                outside.resolve("Example.java")
        );

        createSymlink(
                project.resolve("external-directory"),
                outside
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipInternalSymlinkDirectoryDuringSearch()
            throws IOException {

        Path project = createProject();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        createSymlink(
                project.resolve("linked-src"),
                directory
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSkipExternalSymlinkFileDuringSearch()
            throws IOException {

        Path project = createProject();

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        createSymlink(
                project.resolve("Example.java"),
                outsideFile
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipInternalSymlinkFileDuringSearch()
            throws IOException {

        Path project = createProject();

        Path original = Files.createFile(
                project.resolve("Original.java")
        );

        createSymlink(
                project.resolve("OriginalCopy.java"),
                original
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "original",
                "java",
                null
        );

        assertEquals(
                List.of(original.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSkipDanglingSymlinkDuringSearch()
            throws IOException {

        Path project = createProject();

        createSymlink(
                project.resolve("Example.java"),
                project.resolve("missing.java")
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldContinueSearchingAfterUnsafeEntry()
            throws IOException {

        Path project = createProject();

        Path validFile = Files.createFile(
                project.resolve("Example.java")
        );

        Path outsideFile = Files.createFile(
                tempDir.toRealPath().resolve("ExternalExample.java")
        );

        createSymlink(
                project.resolve("LinkedExample.java"),
                outsideFile
        );

        SearchService service = service(project);

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(validFile.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldRejectDeletedProjectRoot() throws IOException {
        Path project = createProject();

        SearchService service = service(project);

        Files.delete(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        null
                )
        );
    }

    @Test
    void shouldRejectNullAccessPolicy() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", createProject())
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new SearchService(
                        registry,
                        null
                )
        );

        assertEquals(
                "accessPolicy must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnSortedRecursiveMatchesWithEmptyPolicy() throws IOException {
        Path project = createProject();

        Files.createDirectories(
                project.resolve("src")
        );
        Path second = Files.createFile(
                project.resolve("src/Example.java")
        );
        Path first = Files.createFile(
                project.resolve("AExample.java")
        );

        SearchService service = service(
                project,
                PolicyConfiguration.empty()
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(
                        first.toRealPath().toString(),
                        second.toRealPath().toString()
                ),
                result
        );
    }

    @Test
    void shouldReturnMatchesWhenRulesDoNotApply() throws IOException {
        Path project = createProject();

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        PolicyRules global = new PolicyRules(
                List.of("Other.java"),
                List.of(),
                List.of(),
                List.of()
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSkipFileExcludedByGlobalName() throws IOException {
        Path project = createProject();

        Path second = Files.createFile(
                project.resolve("ZExample.java")
        );
        Files.createFile(
                project.resolve("PrivateExample.java")
        );
        Path first = Files.createFile(
                project.resolve("AExample.java")
        );

        PolicyRules global = new PolicyRules(
                List.of("PrivateExample.java"),
                List.of(),
                List.of(),
                List.of()
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(
                        first.toRealPath().toString(),
                        second.toRealPath().toString()
                ),
                result
        );
    }

    @Test
    void shouldSkipFilesExcludedByGlobalExtension() throws IOException {
        Path project = createProject();

        Files.createFile(
                project.resolve("Example.JAVA")
        );
        Files.createFile(
                project.resolve("AnotherExample.java")
        );

        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".java"),
                List.of(),
                List.of()
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipOnlyFileExcludedByProjectPath() throws IOException {
        Path project = createProject();

        Files.createDirectories(
                project.resolve("src")
        );
        Files.createFile(
                project.resolve("src/Example.java")
        );
        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("src/Example.java"),
                List.of()
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(file.toRealPath().toString()),
                result
        );
    }

    @Test
    void shouldSkipExcludedDirectoryAndItsDescendants() throws IOException {
        Path project = createProject();

        Files.createDirectories(
                project.resolve("private/nested")
        );
        Files.createDirectories(
                project.resolve("src")
        );
        Files.createDirectories(
                project.resolve("public")
        );
        Files.createFile(
                project.resolve("private/Example.java")
        );
        Files.createFile(
                project.resolve("private/nested/Example.java")
        );
        Path sourceFile = Files.createFile(
                project.resolve("src/Example.java")
        );
        Path publicFile = Files.createFile(
                project.resolve("public/Example.java")
        );

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private")
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        List<String> result = service.search(
                "project",
                "example",
                "java",
                null
        );

        assertEquals(
                List.of(
                        publicFile.toRealPath().toString(),
                        sourceFile.toRealPath().toString()
                ),
                result
        );
    }

    @Test
    void shouldRejectSearchInsideExcludedDirectory() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectories(
                project.resolve("private")
        );

        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private")
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        directory
                )
        );

        assertEquals(
                "Access denied by GLOBAL DIRECTORY_PATH rule: private",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSearchInsideDescendantOfExcludedDirectory() throws IOException {
        Path project = createProject();

        Path directory = Files.createDirectories(
                project.resolve("private/nested")
        );

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private")
        );
        SearchService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.search(
                        "project",
                        "example",
                        "java",
                        directory
                )
        );

        assertEquals(
                "Access denied by PROJECT DIRECTORY_PATH rule: private",
                exception.getMessage()
        );
    }

    private Path createProject() throws IOException {
        return Files.createDirectory(
                tempDir.toRealPath().resolve("project")
        );
    }

    private SearchService service(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new SearchService(registry);
    }

    private SearchService service(
            Path project,
            PolicyConfiguration configuration
    ) throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );
        ProjectAccessPolicy accessPolicy = new ProjectAccessPolicy(
                registry,
                configuration
        );

        return new SearchService(
                registry,
                accessPolicy
        );
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
}