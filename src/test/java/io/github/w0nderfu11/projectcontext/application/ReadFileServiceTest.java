
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReadFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadFileAndPreserveContent() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                """
                public class Example {

                    public void execute() {
                        System.out.println("hello");
                    }
                }
                """
        );

        ReadFileService service = service(project);

        String content = service.read("project", file);

        assertEquals(
                Files.readString(file),
                content
        );
    }

    @Test
    void shouldReadFileFromNestedDirectory() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path sourceDirectory = Files.createDirectories(
                project.resolve("src/main/java")
        );

        Path file = Files.writeString(
                sourceDirectory.resolve("Example.java"),
                "public class Example {}"
        );

        ReadFileService service = service(project);

        assertEquals(
                "public class Example {}",
                service.read("project", file)
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
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.read(null, file)
        );

        assertEquals(
                "projectName must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFilePath() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        ReadFileService service = service(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.read("project", null)
        );

        assertEquals(
                "filePath must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProject() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("unknown", file)
        );

        assertEquals(
                "project is not registered: unknown",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileOutsideProjectRoot() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path outsideFile = Files.createFile(
                tempDir.resolve("outside.txt")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", outsideFile)
        );

        assertEquals(
                "file path is outside registered project root: " + outsideFile,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNonExistingFile() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path missingFile = project.resolve("missing.txt");

        ReadFileService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.read("project", missingFile)
        );
    }

    @Test
    void shouldRejectDirectoryAsFile() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path directory = Files.createDirectory(
                project.resolve("directory")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", directory)
        );

        assertEquals(
                "file path must point to a regular file: " + directory.toRealPath(),
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSymlinkOutsideProjectRoot() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path outsideDirectory = Files.createDirectory(
                tempDir.resolve("outside")
        );

        Path outsideFile = Files.writeString(
                outsideDirectory.resolve("secret.txt"),
                "secret"
        );

        Path link = project.resolve("external-link");

        try {
            Files.createSymbolicLink(link, outsideDirectory);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "Symbolic links are not available");
        }

        Path linkedFile = link.resolve("secret.txt");

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", linkedFile)
        );

        assertEquals(
                "real file path is outside registered project root: "
                        + outsideFile.toRealPath(),
                exception.getMessage()
        );
    }

    @Test
    void shouldReadSpecificLineRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\nsecond\nthird\nfourth\nfifth"
        );

        ReadFileService service = service(project);

        assertEquals(
                "second\nthird\nfourth",
                service.readRange("project", file, 2, 4)
        );
    }

    @Test
    void shouldReadSingleLine() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "second",
                service.readRange("project", file, 2, 2)
        );
    }

    @Test
    void shouldReadRangeFromNestedDirectory() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path directory = Files.createDirectories(
                project.resolve("src/main/java")
        );

        Path file = Files.writeString(
                directory.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "second\nthird",
                service.readRange("project", file, 2, 3)
        );
    }

    @Test
    void shouldReadRangeBeyondEndOfFile() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "second\nthird",
                service.readRange("project", file, 2, 100)
        );
    }

    @Test
    void shouldReturnEmptyContentWhenStartLineExceedsFileLength() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "",
                service.readRange("project", file, 10, 20)
        );
    }

    @Test
    void shouldReturnEmptyContentForEmptyFile() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("empty.txt")
        );

        ReadFileService service = service(project);

        assertEquals(
                "",
                service.readRange("project", file, 1, 10)
        );
    }

    @Test
    void shouldPreserveEmptyLinesWithinRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\n\nthird\n\nfifth"
        );

        ReadFileService service = service(project);

        assertEquals(
                "\nthird\n",
                service.readRange("project", file, 2, 4)
        );
    }

    @Test
    void shouldNormalizeWindowsLineEndings() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\r\nsecond\r\nthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "first\nsecond\nthird",
                service.readRange("project", file, 1, 3)
        );
    }

    @Test
    void shouldNormalizeCarriageReturnLineEndings() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "first\rsecond\rthird"
        );

        ReadFileService service = service(project);

        assertEquals(
                "first\nsecond\nthird",
                service.readRange("project", file, 1, 3)
        );
    }

    @Test
    void shouldReadUnicodeContent() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("Example.java"),
                "hello\nПривет\n你好\nworld"
        );

        ReadFileService service = service(project);

        assertEquals(
                "Привет\n你好",
                service.readRange("project", file, 2, 3)
        );
    }

    @Test
    void shouldRejectInvalidStartLine() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", file, 0, 10)
        );

        assertEquals(
                "invalid line range: startLine=0, endLine=10",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEndLineBeforeStartLine() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", file, 10, 5)
        );

        assertEquals(
                "invalid line range: startLine=10, endLine=5",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowMaximumLineRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        String content = "line\n".repeat(299) + "line";

        Path file = Files.writeString(
                project.resolve("Example.java"),
                content
        );

        ReadFileService service = service(project);

        assertEquals(
                content,
                service.readRange("project", file, 1, 300)
        );
    }

    @Test
    void shouldRejectLineRangeExceedingMaximum() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", file, 1, 301)
        );

        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectLineRangeOverflow() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange(
                        "project",
                        file,
                        1,
                        Integer.MAX_VALUE
                )
        );

        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowMaximumResultSize() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        String content = "a".repeat(256 * 1024);

        Path file = Files.writeString(
                project.resolve("large.txt"),
                content
        );

        ReadFileService service = service(project);

        assertEquals(
                content,
                service.readRange("project", file, 1, 1)
        );
    }

    @Test
    void shouldRejectResultSizeExceedingMaximum() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("large.txt"),
                "a".repeat(256 * 1024 + 1)
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", file, 1, 1)
        );

        assertEquals(
                "file range exceeds maximum allowed size: 262144 bytes",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUtf8ResultSizeExceedingMaximum() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.writeString(
                project.resolve("unicode.txt"),
                "я".repeat(131_073)
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", file, 1, 1)
        );

        assertEquals(
                "file range exceeds maximum allowed size: 262144 bytes",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectNameForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.readRange(null, file, 1, 10)
        );

        assertEquals(
                "projectName must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFilePathForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        ReadFileService service = service(project);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.readRange("project", null, 1, 10)
        );

        assertEquals(
                "filePath must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredProjectForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path file = Files.createFile(
                project.resolve("Example.java")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("unknown", file, 1, 10)
        );

        assertEquals(
                "project is not registered: unknown",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileOutsideProjectRootForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path outsideFile = Files.createFile(
                tempDir.resolve("outside.txt")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", outsideFile, 1, 10)
        );

        assertEquals(
                "file path is outside registered project root: " + outsideFile,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNonExistingFileForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path missingFile = project.resolve("missing.txt");

        ReadFileService service = service(project);

        assertThrows(
                NoSuchFileException.class,
                () -> service.readRange("project", missingFile, 1, 10)
        );
    }

    @Test
    void shouldRejectDirectoryAsFileForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path directory = Files.createDirectory(
                project.resolve("directory")
        );

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", directory, 1, 10)
        );

        assertEquals(
                "file path must point to a regular file: " + directory.toRealPath(),
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowSymlinkInsideProjectRootForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path target = Files.writeString(
                project.resolve("target.txt"),
                "first\nsecond\nthird"
        );

        Path link = project.resolve("internal-link.txt");

        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "Symbolic links are not available");
        }

        ReadFileService service = service(project);

        assertEquals(
                "second\nthird",
                service.readRange("project", link, 2, 3)
        );
    }

    @Test
    void shouldRejectSymlinkOutsideProjectRootForRange() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path outsideDirectory = Files.createDirectory(
                tempDir.resolve("outside")
        );

        Path outsideFile = Files.writeString(
                outsideDirectory.resolve("secret.txt"),
                "secret"
        );

        Path link = project.resolve("external-link");

        try {
            Files.createSymbolicLink(link, outsideDirectory);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "Symbolic links are not available");
        }

        Path linkedFile = link.resolve("secret.txt");

        ReadFileService service = service(project);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readRange("project", linkedFile, 1, 10)
        );

        assertEquals(
                "real file path is outside registered project root: "
                        + outsideFile.toRealPath(),
                exception.getMessage()
        );
    }

    private ReadFileService service(Path project) throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new ReadFileService(registry);
    }
}
