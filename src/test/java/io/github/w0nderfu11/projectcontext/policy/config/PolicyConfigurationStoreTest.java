package io.github.w0nderfu11.projectcontext.policy.config;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PolicyConfigurationStoreTest {

    private static final String VALID_JSON = """
            {
              "global": {
                "fileNames": ["README"],
                "extensions": [".env"],
                "filePaths": ["app/config"],
                "directoryPaths": ["secret/"]
              },
              "projects": {
                "project": {
                  "fileNames": [],
                  "extensions": [".log"],
                  "filePaths": [],
                  "directoryPaths": []
                }
              }
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadExplicitGlobalAndProjectRules() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Files.writeString(file, VALID_JSON);

        PolicyConfiguration configuration = store.load();
        PolicyRules expected = new PolicyRules(
                List.of("README"),
                List.of(".env"),
                List.of("app/config"),
                List.of("secret")
        );

        assertEquals(expected, configuration.global());
        assertEquals(
                List.of(".log"),
                configuration.projects().get("project").extensions()
        );
    }

    @Test
    void shouldSaveStableJsonAndReplaceExistingConfiguration() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        PolicyRules project = new PolicyRules(
                List.of("z", "A"),
                List.of(".env"),
                List.of("app" + File.separator + "config"),
                List.of("private/")
        );
        PolicyConfiguration configuration = new PolicyConfiguration(
                PolicyRules.empty(),
                Map.of("project", project)
        );

        store.save(PolicyConfiguration.empty());
        store.save(configuration);
        byte[] saved = Files.readAllBytes(file);
        PolicyConfiguration loaded = store.load();

        assertEquals(configuration, loaded);
        assertEquals(
                List.of("A", "z"),
                loaded.projects().get("project").fileNames()
        );
        assertEquals(
                List.of("app/config"),
                loaded.projects().get("project").filePaths()
        );
        store.save(loaded);
        assertArrayEquals(saved, Files.readAllBytes(file));
        assertOnlyTargetRemains(file);
    }

    @Test
    void shouldUseEmptyPolicyOnlyWhenLocationIsUnspecified() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        PolicyConfigurationStore unspecified = new PolicyConfigurationStore(registry());

        assertEquals(PolicyConfiguration.empty(), unspecified.load());
        assertThrows(
                IOException.class,
                () -> unspecified.save(PolicyConfiguration.empty())
        );
        assertThrows(NoSuchFileException.class, store::load);

        Files.createDirectory(file);
        assertThrows(IOException.class, store::load);
    }

    @Test
    void shouldRejectMalformedJsonAndTrailingContent() throws IOException {
        assertInvalidConfigurations(
                "",
                "{",
                "null",
                "[]",
                "{}",
                VALID_JSON + " {}"
        );
    }

    @Test
    void shouldRejectMissingAndUnknownProperties() throws IOException {
        assertInvalidConfigurations(
                VALID_JSON.replace("\"global\":", "\"unexpected\":"),
                VALID_JSON.replace("\"projects\":", "\"extra\": 7, \"projects\":"),
                VALID_JSON.replace("\"fileNames\": [],", ""),
                VALID_JSON.replace("\"fileNames\": []", "\"names\": []"),
                VALID_JSON.replace("\"fileNames\": []", "\"fileNames\": [], \"other\": []")
        );
    }

    @Test
    void shouldRejectDuplicateJsonPropertiesAndRules() throws IOException {
        assertInvalidConfigurations(
                VALID_JSON.replace("\"projects\":", "\"projects\": {}, \"projects\":"),
                VALID_JSON.replace("[\"README\"]", "[\"README\", \"README\"]"),
                VALID_JSON.replace("[\"README\"]", "[\"ReadMe\", \"README\"]")
        );
    }

    @Test
    void shouldRejectInvalidConfigurationTypes() throws IOException {
        assertInvalidConfigurations(
                """
                {
                  "global": {
                    "fileNames": [],
                    "extensions": [],
                    "filePaths": [],
                    "directoryPaths": []
                  },
                  "projects": false
                }
                """,
                VALID_JSON.replace("\"fileNames\": []", "\"fileNames\": null"),
                VALID_JSON.replace("[\"README\"]", "\"README\""),
                VALID_JSON.replace("[\"README\"]", "[null]"),
                VALID_JSON.replace("[\"README\"]", "[true]"),
                VALID_JSON.replace("[\"README\"]", "[17]"),
                VALID_JSON.replace("[\"README\"]", "[1.5]")
        );
    }

    @Test
    void shouldRejectInvalidProjectNamesAndRuleValues() throws IOException {
        assertInvalidConfigurations(
                VALID_JSON.replace("\"project\":", "\" \":"),
                VALID_JSON.replace("\"project\":", "\"unregistered\":"),
                VALID_JSON.replace("\".env\"", "\"env\""),
                VALID_JSON.replace("app/config", "app/../config"),
                VALID_JSON.replace("secret/", "/secret/")
        );
    }

    @Test
    void shouldPreserveExistingFileWhenProposedConfigurationIsInvalid()
            throws IOException {

        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Files.writeString(file, VALID_JSON);
        PolicyConfiguration invalid = new PolicyConfiguration(
                PolicyRules.empty(),
                Map.of("unknown", PolicyRules.empty())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> store.save(invalid)
        );
        assertEquals(VALID_JSON, Files.readString(file));
        assertOnlyTargetRemains(file);
    }

    @Test
    void shouldRejectDirectoryAsConfigurationTarget() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Files.createDirectory(file);
        Path marker = Files.writeString(file.resolve("marker"), "original");

        assertThrows(
                IOException.class,
                () -> store.save(PolicyConfiguration.empty())
        );
        assertEquals("original", Files.readString(marker));
        assertOnlyTargetRemains(file);
    }

    @Test
    void shouldPreserveReadOnlyConfigurationOnWindows() throws IOException {
        assumeTrue(File.separatorChar == '\\', "Windows file attributes required");

        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);
        Files.writeString(file, VALID_JSON);

        Files.setAttribute(file, "dos:readonly", true);
        try {
            assertThrows(
                    IOException.class,
                    () -> store.save(PolicyConfiguration.empty())
            );
        } finally {
            Files.setAttribute(file, "dos:readonly", false);
        }

        assertEquals(VALID_JSON, Files.readString(file));
        assertOnlyTargetRemains(file);
    }

    @Test
    void shouldRejectSymbolicLinkInsteadOfJson() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Path original = Files.writeString(tempDir.resolve("original.json"), VALID_JSON);
        createSymbolicLink(file, original);

        IOException exception = assertThrows(IOException.class, store::load);
        assertEquals(
                "Symbolic links are not allowed in the configuration path",
                exception.getMessage()
        );
        assertThrows(
                IOException.class,
                () -> store.save(PolicyConfiguration.empty())
        );
        assertTrue(Files.isSymbolicLink(file));
        assertEquals(VALID_JSON, Files.readString(original));

        Files.delete(original);
        assertThrows(
                IOException.class,
                () -> store.save(PolicyConfiguration.empty())
        );
        assertTrue(Files.isSymbolicLink(file));
        assertFalse(Files.exists(original));
    }

    @Test
    void shouldRejectSymbolicLinkInAnAncestorOfTheConfigurationFile()
            throws IOException {

        Path realDirectory = Files.createDirectory(tempDir.resolve("real"));
        Path nested = Files.createDirectory(realDirectory.resolve("nested"));
        Path original = Files.writeString(nested.resolve("policy.json"), VALID_JSON);
        Path link = tempDir.resolve("alias");

        createSymbolicLink(link, realDirectory);
        PolicyConfigurationStore linkedStore = new PolicyConfigurationStore(
                registry(),
                link.resolve("nested/policy.json")
        );

        IOException exception = assertThrows(IOException.class, linkedStore::load);
        assertEquals(
                "Symbolic links are not allowed in the configuration path",
                exception.getMessage()
        );
        assertThrows(
                IOException.class,
                () -> linkedStore.save(PolicyConfiguration.empty())
        );
        assertEquals(VALID_JSON, Files.readString(original));
        try (Stream<Path> entries = Files.list(nested)) {
            assertEquals(List.of(original), entries.toList());
        }
    }

    @Test
    void shouldRejectParentTraversalBeforeNormalization() throws IOException {
        ProjectRegistry registry = registry();
        Path path = tempDir.resolve("alias/../policy.json");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyConfigurationStore(registry, path)
        );
    }

    @Test
    void shouldFailWhenTemporaryFileParentIsMissingOrNotADirectory()
            throws IOException {

        Path parent = tempDir.resolve("missing");
        PolicyConfigurationStore nestedStore = new PolicyConfigurationStore(
                registry(),
                parent.resolve("policy.json")
        );

        assertThrows(
                IOException.class,
                () -> nestedStore.save(PolicyConfiguration.empty())
        );
        assertFalse(Files.exists(parent));

        Files.writeString(parent, "original");
        assertThrows(
                IOException.class,
                () -> nestedStore.save(PolicyConfiguration.empty())
        );
        assertEquals("original", Files.readString(parent));
    }

    @Test
    void shouldPropagateReadPermissionFailure() throws IOException {
        assumeTrue(
                Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
                "POSIX file permissions required"
        );

        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Files.writeString(file, VALID_JSON);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, Set.of());
            assumeTrue(
                    !Files.isReadable(file),
                    "File is readable by the current user"
            );
            assertThrows(IOException.class, store::load);
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }

        assertEquals(VALID_JSON, Files.readString(file));
    }

    @Test
    void shouldPreserveJsonWhenTemporaryFileCannotBeCreated() throws IOException {
        assumeTrue(
                Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
                "POSIX file permissions required"
        );

        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        Files.writeString(file, VALID_JSON);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(tempDir);
        try {
            Files.setPosixFilePermissions(tempDir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE
            ));
            assumeTrue(
                    !Files.isWritable(tempDir),
                    "Directory is writable by the current user"
            );
            assertThrows(
                    IOException.class,
                    () -> store.save(PolicyConfiguration.empty())
            );
        } finally {
            Files.setPosixFilePermissions(tempDir, permissions);
        }

        assertEquals(VALID_JSON, Files.readString(file));
        assertOnlyTargetRemains(file);
    }

    private ProjectRegistry registry() throws IOException {
        return new ProjectRegistry(Map.of("project", tempDir));
    }

    private PolicyConfigurationStore store(Path file) throws IOException {
        return new PolicyConfigurationStore(registry(), file);
    }

    private void assertInvalidConfigurations(String... configurations)
            throws IOException {

        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(file);

        for (String json : configurations) {
            Files.writeString(file, json);

            assertThrows(IOException.class, store::load, json);
            assertEquals(json, Files.readString(file));
        }
    }

    private void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
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
        }
    }

    private void assertOnlyTargetRemains(Path file) throws IOException {
        try (Stream<Path> entries = Files.list(file.getParent())) {
            assertEquals(List.of(file), entries.toList());
        }
    }
}
