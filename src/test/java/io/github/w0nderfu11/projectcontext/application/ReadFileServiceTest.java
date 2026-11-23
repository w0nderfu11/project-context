
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
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReadFileServiceTest {

    private static final int MAX_BYTES = 256 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void shouldReadFileAndPreserveContent() throws IOException {
        Path project = project();

        String content = """
                public class Example {

                    public void execute() {
                        System.out.println("hello");
                    }
                }
                """;

        Path file = write(project, "Example.java", content);
        ReadFileService service = service(project);

        assertEquals(content, service.read("project", file));
    }

    @Test
    void shouldReadNestedFileAndRange() throws IOException {
        Path project = project();

        Path directory = Files.createDirectories(
                project.resolve("src/main/java")
        );

        Path file = Files.writeString(
                directory.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "first\nsecond\nthird",
                service.read("project", file)
        );

        assertEquals(
                "second\nthird",
                service.readRange("project", file, 2, 3)
        );
    }

    @Test
    void shouldReadEmptyFile() throws IOException {
        Path project = project();

        Path file = Files.createFile(
                project.resolve("empty.txt")
        );

        ReadFileService service = service(project);

        assertEquals("", service.read("project", file));
        assertEquals("", service.readRange("project", file, 1, 10));
    }

    @Test
    void shouldAllowMaximumFileSize() throws IOException {
        Path project = project();
        String content = "a".repeat(MAX_BYTES);

        Path file = write(project, "large.txt", content);

        assertEquals(
                content,
                service(project).read("project", file)
        );
    }

    @Test
    void shouldRejectFileExceedingMaximumSize() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "large.txt",
                "a".repeat(MAX_BYTES + 1)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("project", file)
        );

        assertEquals(
                "file exceeds maximum allowed size: 262144 bytes",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMalformedUtf8() throws IOException {
        Path project = project();

        Path file = Files.write(
                project.resolve("invalid.txt"),
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        ReadFileService service = service(project);

        assertThrows(
                MalformedInputException.class,
                () -> service.read("project", file)
        );

        assertThrows(
                MalformedInputException.class,
                () -> service.readRange("project", file, 1, 1)
        );
    }

    @Test
    void shouldRejectNullRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ReadFileService(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectName() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        ReadFileService service = service(project);

        NullPointerException readError = assertThrows(
                NullPointerException.class,
                () -> service.read(null, file)
        );

        NullPointerException rangeError = assertThrows(
                NullPointerException.class,
                () -> service.readRange(null, file, 1, 1)
        );

        assertEquals(
                "projectName must not be null",
                readError.getMessage()
        );

        assertEquals(
                "projectName must not be null",
                rangeError.getMessage()
        );
    }

    @Test
    void shouldRejectNullFilePath() throws IOException {
        ReadFileService service = service(project());

        NullPointerException readError = assertThrows(
                NullPointerException.class,
                () -> service.read("project", null)
        );

        NullPointerException rangeError = assertThrows(
                NullPointerException.class,
                () -> service.readRange("project", null, 1, 1)
        );

        assertEquals(
                "filePath must not be null",
                readError.getMessage()
        );

        assertEquals(
                "filePath must not be null",
                rangeError.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProject() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        ReadFileService service = service(project);

        IllegalArgumentException readError = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("unknown", file)
        );

        IllegalArgumentException rangeError = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("unknown", file, 1, 1)
        );

        assertEquals(
                "project is not registered: unknown",
                readError.getMessage()
        );

        assertEquals(
                "project is not registered: unknown",
                rangeError.getMessage()
        );
    }

    @Test
    void shouldRejectRelativePath() throws IOException {
        ReadFileService service = service(project());
        Path relative = Path.of("Example.java");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", relative)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", relative, 1, 1)
        );
    }

    @Test
    void shouldRejectTraversalSegments() throws IOException {
        Path project = project();
        write(project, "Example.java", "hello");

        Path requested = project.resolve("src/../Example.java");

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", requested)
        );

        assertEquals(
                "requestedPath must not contain '..' segments",
                exception.getMessage()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", requested, 1, 1)
        );
    }

    @Test
    void shouldRejectFileOutsideProjectRoot() throws IOException {
        Path project = project();

        Path outside = Files.writeString(
                tempDir.toRealPath().resolve("outside.txt"),
                "secret"
        );

        ReadFileService service = service(project);

        ProjectPathViolationException readError = assertThrows(
                ProjectPathViolationException.class,
                () -> service.read("project", outside)
        );

        ProjectPathViolationException rangeError = assertThrows(
                ProjectPathViolationException.class,
                () -> service.readRange("project", outside, 1, 1)
        );

        assertEquals(
                "Path is outside the registered project root",
                readError.getMessage()
        );

        assertEquals(
                readError.getMessage(),
                rangeError.getMessage()
        );

        assertFalse(readError.getMessage().contains(outside.toString()));
    }

    @Test
    void shouldRejectMissingFile() throws IOException {
        Path project = project();
        Path missing = project.resolve("missing.txt");

        ReadFileService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.read("project", missing)
        );

        assertThrows(
                NoSuchFileException.class,
                () -> service.readRange("project", missing, 1, 1)
        );
    }

    @Test
    void shouldRejectDirectoryAsFile() throws IOException {
        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("directory")
        );

        ReadFileService service = service(project);

        IllegalArgumentException readError = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", directory)
        );

        IllegalArgumentException rangeError = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", directory, 1, 1)
        );

        assertEquals(
                "file path must point to a regular file: " + directory,
                readError.getMessage()
        );

        assertEquals(
                readError.getMessage(),
                rangeError.getMessage()
        );
    }

    @Test
    void shouldRejectInternalSymlinkForReadAndRange()
            throws IOException {

        Path project = project();
        Path target = write(project, "target.txt", "first\nsecond");

        Path link = createSymlink(
                project.resolve("internal-link.txt"),
                target
        );

        ReadFileService service = service(project);

        assertSymlinkRejected(service, link);
    }

    @Test
    void shouldRejectExternalSymlinkForReadAndRange()
            throws IOException {

        Path project = project();

        Path outside = Files.writeString(
                tempDir.toRealPath().resolve("secret.txt"),
                "secret"
        );

        Path link = createSymlink(
                project.resolve("external-link.txt"),
                outside
        );

        ReadFileService service = service(project);

        assertSymlinkRejected(service, link);
    }

    @Test
    void shouldRejectIntermediateSymlinkDirectory()
            throws IOException {

        Path project = project();

        Path directory = Files.createDirectory(
                project.resolve("src")
        );

        write(directory, "Example.java", "hello");

        Path link = createSymlink(
                project.resolve("linked-src"),
                directory
        );

        Path requested = link.resolve("Example.java");

        assertSymlinkRejected(service(project), requested);
    }

    @Test
    void shouldReadSpecificLineRange() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\nsecond\nthird\nfourth\nfifth"
        );

        assertEquals(
                "second\nthird\nfourth",
                service(project).readRange("project", file, 2, 4)
        );
    }

    @Test
    void shouldReadSingleLine() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\nsecond\nthird"
        );

        assertEquals(
                "second",
                service(project).readRange("project", file, 2, 2)
        );
    }

    @Test
    void shouldReadRangeBeyondEndOfFile() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "second\nthird",
                service.readRange("project", file, 2, 100)
        );

        assertEquals(
                "",
                service.readRange("project", file, 10, 20)
        );
    }

    @Test
    void shouldPreserveEmptyLinesWithinRange() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\n\nthird\n\nfifth"
        );

        assertEquals(
                "\nthird\n",
                service(project).readRange("project", file, 2, 4)
        );
    }

    @Test
    void shouldNormalizeCrLfLineEndings() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\r\nsecond\r\nthird"
        );

        assertEquals(
                "first\nsecond\nthird",
                service(project).readRange("project", file, 1, 3)
        );
    }

    @Test
    void shouldNormalizeCrLineEndings() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "first\rsecond\rthird"
        );

        assertEquals(
                "first\nsecond\nthird",
                service(project).readRange("project", file, 1, 3)
        );
    }

    @Test
    void shouldReadUnicodeContent() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "Example.java",
                "hello\nПривет\n你好\nworld"
        );

        assertEquals(
                "Привет\n你好",
                service(project).readRange("project", file, 2, 3)
        );
    }

    @Test
    void shouldRejectInvalidStartLine() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 0, 10
                )
        );

        assertEquals(
                "invalid line range: startLine=0, endLine=10",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEndLineBeforeStartLine() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 10, 5
                )
        );

        assertEquals(
                "invalid line range: startLine=10, endLine=5",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowMaximumLineRange() throws IOException {
        Path project = project();
        String content = "line\n".repeat(299) + "line";

        Path file = write(project, "Example.java", content);

        assertEquals(
                content,
                service(project).readRange("project", file, 1, 300)
        );
    }

    @Test
    void shouldRejectLineRangeExceedingMaximum() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 1, 301
                )
        );

        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectLineRangeOverflow() throws IOException {
        Path project = project();
        Path file = write(project, "Example.java", "hello");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 1, Integer.MAX_VALUE
                )
        );

        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowMaximumRangeResultSize() throws IOException {
        Path project = project();
        String content = "a".repeat(MAX_BYTES);

        Path file = write(project, "large.txt", content);

        assertEquals(
                content,
                service(project).readRange("project", file, 1, 1)
        );
    }

    @Test
    void shouldRejectOversizedRangeResult() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "large.txt",
                "a".repeat(MAX_BYTES + 1)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 1, 1
                )
        );

        assertEquals(
                "file range exceeds maximum allowed size: 262144 bytes",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectOversizedUtf8RangeResult() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "unicode.txt",
                "я".repeat(131_073)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRange(
                        "project", file, 1, 1
                )
        );

        assertEquals(
                "file range exceeds maximum allowed size: 262144 bytes",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowSmallRangeFromLargeFile() throws IOException {
        Path project = project();

        Path file = write(
                project,
                "large.txt",
                "first\n" + "ignored\n".repeat(40_000)
        );

        assertEquals(
                "first",
                service(project).readRange("project", file, 1, 1)
        );
    }

    @Test
    void shouldRejectNullAccessPolicy() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project())
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ReadFileService(
                        registry,
                        null
                )
        );

        assertEquals("accessPolicy must not be null", exception.getMessage());
    }

    @Test
    void shouldReadFileAndRangeWithEmptyPolicy() throws IOException {
        Path project = project();
        Path file = write(project, ".env", "first\nsecond");
        ReadFileService service = service(
                project,
                PolicyConfiguration.empty()
        );

        assertEquals("first\nsecond", service.read("project", file));
        assertEquals("second", service.readRange("project", file, 2, 2));
    }

    @Test
    void shouldApplyGlobalFileNameRuleToFullAndRangeReads() throws IOException {
        Path project = project();
        Path excluded = Files.write(
                project.resolve(".env"),
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );
        Path allowed = write(project, "docker.env", "first\nsecond");
        PolicyRules global = new PolicyRules(
                List.of(".env"),
                List.of(),
                List.of(),
                List.of()
        );
        ReadFileService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        assertAccessDenied(
                service,
                excluded,
                "Access denied by GLOBAL FILE_NAME rule: .env"
        );
        assertEquals("first\nsecond", service.read("project", allowed));
        assertEquals("second", service.readRange("project", allowed, 2, 2));
    }

    @Test
    void shouldRejectFileExcludedByGlobalExtension() throws IOException {
        Path project = project();
        Path file = write(project, "server.LOG", "log content");
        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".log"),
                List.of(),
                List.of()
        );
        ReadFileService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        assertAccessDenied(
                service,
                file,
                "Access denied by GLOBAL EXTENSION rule: .log"
        );
    }

    @Test
    void shouldRejectFileExcludedByProjectPath() throws IOException {
        Path project = project();
        Path config = Files.createDirectory(project.resolve("config"));
        Path file = write(config, "local.json", "configuration");
        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("config/local.json"),
                List.of()
        );
        ReadFileService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        assertAccessDenied(
                service,
                file,
                "Access denied by PROJECT FILE_PATH rule: config/local.json"
        );
    }

    @Test
    void shouldRejectDirectReadInsideExcludedDirectory() throws IOException {
        Path project = project();
        Path nested = Files.createDirectories(project.resolve("private/nested"));
        Path file = write(nested, "key.txt", "key content");
        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private")
        );
        ReadFileService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        assertAccessDenied(
                service,
                file,
                "Access denied by GLOBAL DIRECTORY_PATH rule: private"
        );
    }

    private Path project() throws IOException {
        return Files.createDirectory(
                tempDir.toRealPath().resolve("project")
        );
    }

    private Path write(Path directory, String name, String content)
            throws IOException {

        return Files.writeString(
                directory.resolve(name),
                content
        );
    }

    private ReadFileService service(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new ReadFileService(registry);
    }

    private ReadFileService service(
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

        return new ReadFileService(
                registry,
                accessPolicy
        );
    }

    private void assertAccessDenied(
            ReadFileService service,
            Path file,
            String message
    ) {
        ProjectAccessDeniedException readError = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.read("project", file)
        );
        ProjectAccessDeniedException rangeError = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.readRange("project", file, 1, 1)
        );

        assertEquals(message, readError.getMessage());
        assertEquals(message, rangeError.getMessage());
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
            ReadFileService service,
            Path filePath
    ) {
        ProjectPathViolationException readError = assertThrows(
                ProjectPathViolationException.class,
                () -> service.read("project", filePath)
        );

        ProjectPathViolationException rangeError = assertThrows(
                ProjectPathViolationException.class,
                () -> service.readRange("project", filePath, 1, 1)
        );

        assertEquals(
                "Symbolic links are not allowed",
                readError.getMessage()
        );

        assertEquals(
                readError.getMessage(),
                rangeError.getMessage()
        );
    }
}