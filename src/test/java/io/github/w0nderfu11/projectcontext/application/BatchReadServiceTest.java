
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.application.dto.BatchReadResult;
import io.github.w0nderfu11.projectcontext.application.dto.FileRangeRequest;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BatchReadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadMultipleFilesInOrder() throws IOException {
        Path project = project();

        Path first = write(project, "A.java", "A");
        Path second = write(project, "B.java", "B");

        Map<Path, BatchReadResult> result = service(project).read(
                "project",
                List.of(second, first)
        );

        assertEquals(
                List.of(second, first),
                new ArrayList<>(result.keySet())
        );

        assertEquals("B", result.get(second).content());
        assertEquals("A", result.get(first).content());

        assertNull(result.get(second).error());
        assertNull(result.get(first).error());
    }

    @Test
    void shouldAllowTenFiles() throws IOException {
        Path project = project();
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            files.add(
                    write(project, "File" + i + ".java", "content" + i)
            );
        }

        Map<Path, BatchReadResult> result =
                service(project).read("project", files);

        assertEquals(10, result.size());

        for (int i = 0; i < files.size(); i++) {
            assertEquals(
                    "content" + i,
                    result.get(files.get(i)).content()
            );
        }
    }

    @Test
    void shouldContinueAfterMissingFile() throws IOException {
        Path project = project();

        Path missing = project.resolve("Missing.java");
        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result =
                service(project).read(
                        "project",
                        List.of(missing, valid)
                );

        assertEquals(2, result.size());

        assertNull(result.get(missing).content());
        assertEquals(
                "File not found",
                result.get(missing).error()
        );

        assertEquals("ok", result.get(valid).content());
        assertNull(result.get(valid).error());
    }

    @Test
    void shouldTreatEmptyFileAsSuccess() throws IOException {
        Path project = project();

        Path empty = Files.createFile(
                project.resolve("Empty.java")
        );

        BatchReadResult result = service(project)
                .read("project", List.of(empty))
                .get(empty);

        assertEquals("", result.content());
        assertNull(result.error());
    }

    @Test
    void shouldRejectOversizedFileWithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path large = write(
                project,
                "Large.java",
                "x".repeat(256 * 1024 + 1)
        );

        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result =
                service(project).read(
                        "project",
                        List.of(large, valid)
                );

        assertNull(result.get(large).content());

        assertEquals(
                "file exceeds maximum allowed size: 262144 bytes",
                result.get(large).error()
        );

        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldRejectMalformedUtf8WithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path invalid = Files.write(
                project.resolve("Invalid.java"),
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result = service(project).read(
                "project",
                List.of(invalid, valid)
        );

        assertNull(result.get(invalid).content());

        assertEquals(
                "Unable to read the requested file",
                result.get(invalid).error()
        );

        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldRejectOutsideFileWithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path outside = Files.writeString(
                tempDir.toRealPath().resolve("Secret.java"),
                "secret"
        );

        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result = service(project).read(
                "project",
                List.of(outside, valid)
        );

        assertNull(result.get(outside).content());

        assertEquals(
                "Unable to read the requested file",
                result.get(outside).error()
        );

        assertFalse(
                result.get(outside).error().contains(outside.toString())
        );

        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldRejectTraversalWithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path invalid = project.resolve("src/../Valid.java");
        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result = service(project).read(
                "project",
                List.of(invalid, valid)
        );

        assertNull(result.get(invalid).content());

        assertEquals(
                "Unable to read the requested file",
                result.get(invalid).error()
        );

        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldRejectSymlinkWithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path target = write(project, "Target.java", "target");

        Path link = createSymlink(
                project.resolve("Linked.java"),
                target
        );

        Path valid = write(project, "Valid.java", "ok");

        Map<Path, BatchReadResult> result = service(project).read(
                "project",
                List.of(link, valid)
        );

        assertNull(result.get(link).content());

        assertEquals(
                "Unable to read the requested file",
                result.get(link).error()
        );

        assertEquals("ok", result.get(valid).content());
    }

    @Test
    void shouldRejectNullReadFileService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BatchReadService(null)
        );

        assertEquals(
                "readFileService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullProjectName() throws IOException {
        Path project = project();
        Path file = write(project, "A.java", "A");

        BatchReadService service = service(project);

        assertThrows(
                NullPointerException.class,
                () -> service.read(null, List.of(file))
        );

        assertThrows(
                NullPointerException.class,
                () -> service.readRanges(
                        null,
                        List.of(new FileRangeRequest(file, 1, 1))
                )
        );
    }

    @Test
    void shouldRejectNullBatch() throws IOException {
        BatchReadService service = service(project());

        assertThrows(
                NullPointerException.class,
                () -> service.read("project", null)
        );

        assertThrows(
                NullPointerException.class,
                () -> service.readRanges("project", null)
        );
    }

    @Test
    void shouldRejectEmptyBatch() throws IOException {
        BatchReadService service = service(project());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.read("project", List.of())
        );

        assertEquals(
                "Batch size must be between 1 and 10",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMoreThanTenFiles() throws IOException {
        Path project = project();
        List<Path> paths = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            paths.add(project.resolve("File" + i + ".java"));
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("project", paths)
        );

        assertEquals(
                "Batch size must be between 1 and 10",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicatePaths() throws IOException {
        Path project = project();

        Path file = write(project, "A.java", "A");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read(
                        "project",
                        List.of(file, file)
                )
        );

        assertEquals(
                "Batch must not contain duplicate entries",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullEntry() throws IOException {
        Path project = project();
        Path file = write(project, "A.java", "A");

        List<Path> paths = Arrays.asList(file, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).read("project", paths)
        );

        assertEquals(
                "Batch must not contain null entries",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnknownProjectForWholeBatch()
            throws IOException {

        Path project = project();
        Path file = write(project, "A.java", "A");

        BatchReadService service = service(project);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.read("unknown", List.of(file))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.readRanges(
                        "unknown",
                        List.of(new FileRangeRequest(file, 1, 1))
                )
        );
    }

    @Test
    void shouldAllowDifferentRangesOfSameFile()
            throws IOException {

        Path project = project();

        Path file = write(
                project,
                "A.java",
                "first\nsecond\nthird\nfourth\n"
        );

        FileRangeRequest first =
                new FileRangeRequest(file, 1, 2);

        FileRangeRequest second =
                new FileRangeRequest(file, 3, 4);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(first, second)
                );

        assertEquals(
                List.of(first, second),
                new ArrayList<>(result.keySet())
        );

        assertEquals(
                "first\nsecond",
                result.get(first).content()
        );

        assertEquals(
                "third\nfourth",
                result.get(second).content()
        );
    }

    @Test
    void shouldHandleInvalidRangeIndependently()
            throws IOException {

        Path project = project();
        Path file = write(project, "A.java", "hello");

        FileRangeRequest invalid =
                new FileRangeRequest(file, 10, 5);

        FileRangeRequest valid =
                new FileRangeRequest(file, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(invalid, valid)
                );

        assertNull(result.get(invalid).content());

        assertEquals(
                "invalid line range: startLine=10, endLine=5",
                result.get(invalid).error()
        );

        assertEquals(
                "hello",
                result.get(valid).content()
        );
    }

    @Test
    void shouldRejectRangeOver300LinesIndependently()
            throws IOException {

        Path project = project();
        Path file = write(project, "A.java", "hello");

        FileRangeRequest invalid =
                new FileRangeRequest(file, 1, 301);

        FileRangeRequest valid =
                new FileRangeRequest(file, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(invalid, valid)
                );

        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                result.get(invalid).error()
        );

        assertEquals(
                "hello",
                result.get(valid).content()
        );
    }

    @Test
    void shouldRejectOversizedRangeResultIndependently()
            throws IOException {

        Path project = project();

        Path large = write(
                project,
                "Large.java",
                "x".repeat(256 * 1024 + 1)
        );

        Path valid = write(project, "Valid.java", "ok");

        FileRangeRequest invalidRequest =
                new FileRangeRequest(large, 1, 1);

        FileRangeRequest validRequest =
                new FileRangeRequest(valid, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(invalidRequest, validRequest)
                );

        assertEquals(
                "file range exceeds maximum allowed size: 262144 bytes",
                result.get(invalidRequest).error()
        );

        assertEquals(
                "ok",
                result.get(validRequest).content()
        );
    }

    @Test
    void shouldHandleMissingRangeFileIndependently()
            throws IOException {

        Path project = project();

        Path missing = project.resolve("Missing.java");
        Path valid = write(project, "Valid.java", "ok");

        FileRangeRequest missingRequest =
                new FileRangeRequest(missing, 1, 1);

        FileRangeRequest validRequest =
                new FileRangeRequest(valid, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(missingRequest, validRequest)
                );

        assertEquals(
                "File not found",
                result.get(missingRequest).error()
        );

        assertEquals(
                "ok",
                result.get(validRequest).content()
        );
    }

    @Test
    void shouldRejectOutsideRangeWithoutExposingPath()
            throws IOException {

        Path project = project();

        Path outside = Files.writeString(
                tempDir.toRealPath().resolve("Secret.java"),
                "secret"
        );

        Path valid = write(project, "Valid.java", "ok");

        FileRangeRequest outsideRequest =
                new FileRangeRequest(outside, 1, 1);

        FileRangeRequest validRequest =
                new FileRangeRequest(valid, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(outsideRequest, validRequest)
                );

        assertNull(result.get(outsideRequest).content());

        assertEquals(
                "Unable to read the requested file",
                result.get(outsideRequest).error()
        );

        assertFalse(
                result.get(outsideRequest).error()
                        .contains(outside.toString())
        );

        assertEquals(
                "ok",
                result.get(validRequest).content()
        );
    }

    @Test
    void shouldRejectSymlinkRangeWithoutStoppingBatch()
            throws IOException {

        Path project = project();

        Path target = write(project, "Target.java", "target");

        Path link = createSymlink(
                project.resolve("Linked.java"),
                target
        );

        Path valid = write(project, "Valid.java", "ok");

        FileRangeRequest linkedRequest =
                new FileRangeRequest(link, 1, 1);

        FileRangeRequest validRequest =
                new FileRangeRequest(valid, 1, 1);

        Map<FileRangeRequest, BatchReadResult> result =
                service(project).readRanges(
                        "project",
                        List.of(linkedRequest, validRequest)
                );

        assertEquals(
                "Unable to read the requested file",
                result.get(linkedRequest).error()
        );

        assertEquals(
                "ok",
                result.get(validRequest).content()
        );
    }

    @Test
    void shouldRejectIdenticalRangeRequests()
            throws IOException {

        Path project = project();

        Path file = write(project, "A.java", "A");

        FileRangeRequest request =
                new FileRangeRequest(file, 1, 10);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRanges(
                        "project",
                        List.of(request, request)
                )
        );

        assertEquals(
                "Batch must not contain duplicate entries",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMoreThanTenRangeRequests()
            throws IOException {

        Path project = project();
        Path file = write(project, "A.java", "A");

        List<FileRangeRequest> requests = new ArrayList<>();

        for (int i = 1; i <= 11; i++) {
            requests.add(
                    new FileRangeRequest(file, i, i)
            );
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(project).readRanges(
                        "project",
                        requests
                )
        );

        assertEquals(
                "Batch size must be between 1 and 10",
                exception.getMessage()
        );
    }

    @Test
    void shouldContinueAfterFileExcludedByPolicy() throws IOException {
        Path project = project();
        Path first = write(project, "first.txt", "first");
        Path excluded = write(project, "server.log", "excluded content");
        Path last = write(project, "last.txt", "last");

        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".log"),
                List.of(),
                List.of()
        );
        BatchReadService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        List<Path> files = List.of(first, excluded, last);
        Map<Path, BatchReadResult> result = service.read("project", files);

        assertEquals(files, new ArrayList<>(result.keySet()));
        assertEquals("first", result.get(first).content());
        assertNull(result.get(first).error());
        assertNull(result.get(excluded).content());
        assertEquals(
                "Access denied by GLOBAL EXTENSION rule: .log",
                result.get(excluded).error()
        );
        assertEquals("last", result.get(last).content());
        assertNull(result.get(last).error());
    }

    @Test
    void shouldContinueAfterRangeExcludedByPolicy() throws IOException {
        Path project = project();
        Path allowed = write(project, "allowed.txt", "first\nsecond\nthird");
        Path excluded = write(project, "private.txt", "excluded content");

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("private.txt"),
                List.of()
        );
        BatchReadService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        FileRangeRequest first = new FileRangeRequest(allowed, 1, 1);
        FileRangeRequest denied = new FileRangeRequest(excluded, 1, 1);
        FileRangeRequest last = new FileRangeRequest(allowed, 2, 3);
        List<FileRangeRequest> requests = List.of(first, denied, last);

        Map<FileRangeRequest, BatchReadResult> result =
                service.readRanges("project", requests);

        assertEquals(requests, new ArrayList<>(result.keySet()));
        assertEquals("first", result.get(first).content());
        assertNull(result.get(first).error());
        assertNull(result.get(denied).content());
        assertEquals(
                "Access denied by PROJECT FILE_PATH rule: private.txt",
                result.get(denied).error()
        );
        assertEquals("second\nthird", result.get(last).content());
        assertNull(result.get(last).error());
    }

    @Test
    void shouldReadFilesAndRangesWithEmptyPolicy() throws IOException {
        Path project = project();
        Path file = write(project, ".env", "first\nsecond");
        FileRangeRequest request = new FileRangeRequest(file, 2, 2);
        BatchReadService service = service(
                project,
                PolicyConfiguration.empty()
        );

        BatchReadResult full = service.read("project", List.of(file))
                .get(file);
        BatchReadResult range = service.readRanges("project", List.of(request))
                .get(request);

        assertEquals("first\nsecond", full.content());
        assertNull(full.error());
        assertEquals("second", range.content());
        assertNull(range.error());
    }

    private Path project() throws IOException {
        return Files.createDirectory(
                tempDir.toRealPath().resolve("project")
        );
    }

    private Path write(
            Path directory,
            String fileName,
            String content
    ) throws IOException {

        return Files.writeString(
                directory.resolve(fileName),
                content
        );
    }

    private BatchReadService service(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        ReadFileService readFileService =
                new ReadFileService(registry);

        return new BatchReadService(readFileService);
    }

    private BatchReadService service(
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
        ReadFileService readFileService = new ReadFileService(
                registry,
                accessPolicy
        );

        return new BatchReadService(readFileService);
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
