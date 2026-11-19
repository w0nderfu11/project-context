
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchReadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadMultipleFilesInOrder() throws IOException {
        Path project = project();
        Path a = Files.writeString(project.resolve("A.java"), "A");
        Path b = Files.writeString(project.resolve("B.java"), "B");

        Map<Path, BatchReadResult> result =
                service(project).read("project", List.of(b, a));

        assertEquals(List.of(b, a), new ArrayList<>(result.keySet()));
        assertEquals("B", result.get(b).content());
        assertEquals("A", result.get(a).content());
        assertNull(result.get(a).error());
    }

    @Test
    void shouldContinueAfterMissingFile() throws IOException {
        Path project = project();
        Path missing = project.resolve("Missing.java");
        Path valid = Files.writeString(project.resolve("Valid.java"), "ok");

        Map<Path, BatchReadResult> result =
                service(project).read("project", List.of(missing, valid));

        assertEquals(2, result.size());
        assertNull(result.get(missing).content());
        assertEquals("File not found", result.get(missing).error());
        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldTreatEmptyFileAsSuccess() throws IOException {
        Path project = project();
        Path empty = Files.createFile(project.resolve("Empty.java"));

        BatchReadResult result =
                service(project).read("project", List.of(empty)).get(empty);

        assertEquals("", result.content());
        assertNull(result.error());
    }

    @Test
    void shouldRejectDuplicatePaths() throws IOException {
        Path project = project();
        Path file = Files.createFile(project.resolve("A.java"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("project", List.of(file, file))
        );
    }

    @Test
    void shouldRejectMoreThanTenFiles() throws IOException {
        Path project = project();
        List<Path> paths = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            paths.add(project.resolve("File" + i + ".java"));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("project", paths)
        );
    }

    @Test
    void shouldRejectUnknownProjectForWholeBatch() throws IOException {
        Path project = project();
        Path file = Files.createFile(project.resolve("A.java"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("unknown", List.of(file))
        );
    }

    @Test
    void shouldNotExposeOutsidePathInError() throws IOException {
        Path project = project();
        Path outside = Files.createFile(tempDir.resolve("Secret.java"));

        BatchReadResult result =
                service(project).read("project", List.of(outside)).get(outside);

        assertNull(result.content());
        assertNotNull(result.error());
        assertFalse(result.error().contains(outside.toString()));
    }

    @Test
    void shouldRejectOversizedFileWithoutStoppingBatch() throws IOException {
        Path project = project();
        Path large = Files.writeString(
                project.resolve("Large.java"),
                "x".repeat(256 * 1024 + 1)
        );
        Path valid = Files.writeString(project.resolve("Valid.java"), "ok");

        Map<Path, BatchReadResult> result =
                service(project).read("project", List.of(large, valid));

        assertNull(result.get(large).content());
        assertTrue(result.get(large).error().contains("maximum"));
        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldAllowDifferentRangesOfSameFile() throws IOException {
        Path project = project();
        Path file = Files.writeString(
                project.resolve("A.java"),
                "first\nsecond\nthird\nfourth\n"
        );

        FileRangeRequest first = new FileRangeRequest(file, 1, 2);
        FileRangeRequest second = new FileRangeRequest(file, 3, 4);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(first, second)
                );

        assertEquals("first\nsecond", result.get(first).content());
        assertEquals("third\nfourth", result.get(second).content());
    }

    @Test
    void shouldRejectIdenticalRangeRequests() throws IOException {
        Path project = project();
        Path file = Files.createFile(project.resolve("A.java"));
        FileRangeRequest request = new FileRangeRequest(file, 1, 10);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRanges(
                        "project",
                        List.of(request, request)
                )
        );
    }

    @Test
    void shouldHandleInvalidRangeIndependently() throws IOException {
        Path project = project();
        Path file = Files.writeString(project.resolve("A.java"), "hello");

        FileRangeRequest invalid = new FileRangeRequest(file, 10, 5);
        FileRangeRequest valid = new FileRangeRequest(file, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(invalid, valid)
                );

        assertNull(result.get(invalid).content());
        assertTrue(result.get(invalid).error().contains("invalid line range"));
        assertEquals("hello", result.get(valid).content());
    }

    private Path project() throws IOException {
        return Files.createDirectory(tempDir.resolve("project"));
    }

    private BatchReadService service(Path project) throws IOException {
        ProjectRegistry registry =
                new ProjectRegistry(Map.of("project", project));

        return new BatchReadService(new ReadFileService(registry));
    }
}
