package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.application.dto.FileMetadata;
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
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GetFileMetadataServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnCanonicalPathByteSizeAndModificationTime() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.writeString(
                project.resolve("example.txt"),
                "caf\u00e9"
        );
        Files.setLastModifiedTime(
                file,
                FileTime.from(Instant.parse("2024-05-06T07:08:09.123456789Z"))
        );

        FileMetadata metadata = service(project).get(
                "project",
                project.resolve("./example.txt")
        );

        assertEquals(file.toRealPath(), metadata.filePath());
        assertEquals(5L, metadata.sizeBytes());
        assertEquals(
                Files.getLastModifiedTime(file).toInstant(),
                metadata.lastModified()
        );
    }

    @Test
    void shouldReturnSizeOfEmptyFile() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.createFile(project.resolve("empty"));

        FileMetadata metadata = service(project).get("project", file);

        assertEquals(0L, metadata.sizeBytes());
    }

    @Test
    void shouldReturnMetadataForLargeBinaryFile() throws IOException {
        Path project = tempDir.toRealPath();
        byte[] bytes = new byte[256 * 1024 + 1];
        bytes[0] = (byte) 0xff;
        Path file = Files.write(
                project.resolve("data.bin"),
                bytes
        );

        FileMetadata metadata = service(project).get("project", file);

        assertEquals(bytes.length, metadata.sizeBytes());
    }

    @Test
    void shouldRejectNullRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetFileMetadataService(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullAccessPolicy() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir.toRealPath())
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetFileMetadataService(
                        registry,
                        null
                )
        );

        assertEquals("accessPolicy must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectFileExcludedByGlobalExtension() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.createFile(project.resolve("server.log"));

        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".log"),
                List.of(),
                List.of()
        );
        GetFileMetadataService service = service(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.get("project", file)
        );

        assertEquals(
                "Access denied by GLOBAL EXTENSION rule: .log",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileExcludedByProjectPath() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.createFile(project.resolve("secret.txt"));

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("secret.txt"),
                List.of()
        );
        GetFileMetadataService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.get("project", file)
        );

        assertEquals(
                "Access denied by PROJECT FILE_PATH rule: secret.txt",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileInsideExcludedAncestorDirectory() throws IOException {
        Path project = tempDir.toRealPath();
        Files.createDirectories(project.resolve("private/nested"));
        Path file = Files.createFile(project.resolve("private/nested/file.txt"));

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private")
        );
        GetFileMetadataService service = service(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> service.get("project", file)
        );

        assertEquals(
                "Access denied by PROJECT DIRECTORY_PATH rule: private",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectExternalSymlinkFile() throws IOException {
        Path root = tempDir.toRealPath();
        Path project = Files.createDirectory(root.resolve("project"));
        Path file = Files.createFile(root.resolve("outside.txt"));
        Path link = createSymlink(
                project.resolve("linked.txt"),
                file
        );
        GetFileMetadataService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", link)
        );

        assertEquals("Symbolic links are not allowed", exception.getMessage());
    }

    @Test
    void shouldRejectInternalSymlinkFile() throws IOException {
        Path root = tempDir.toRealPath();
        Path project = Files.createDirectory(root.resolve("project"));
        Path file = Files.createFile(project.resolve("internal.txt"));
        Path link = createSymlink(
                project.resolve("linked.txt"),
                file
        );
        GetFileMetadataService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", link)
        );

        assertEquals("Symbolic links are not allowed", exception.getMessage());
    }

    @Test
    void shouldRejectIntermediateSymlinkDirectory() throws IOException {
        Path root = tempDir.toRealPath();
        Path project = Files.createDirectory(root.resolve("project"));
        Files.createFile(root.resolve("outside.txt"));
        Path link = createSymlink(
                project.resolve("linked-directory"),
                root
        );
        GetFileMetadataService service = service(project);

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> service.get("project", link.resolve("outside.txt"))
        );

        assertEquals("Symbolic links are not allowed", exception.getMessage());
    }

    private GetFileMetadataService service(Path project)
            throws IOException {

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        return new GetFileMetadataService(registry);
    }

    private GetFileMetadataService service(
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

        return new GetFileMetadataService(
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
