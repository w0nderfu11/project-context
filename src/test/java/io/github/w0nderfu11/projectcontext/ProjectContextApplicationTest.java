package io.github.w0nderfu11.projectcontext;

import io.github.w0nderfu11.projectcontext.policy.ProjectAccessDeniedException;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectContextApplicationTest {

    private static final String VALID_JSON = """
            {
              "global": {
                "fileNames": ["secret.txt"],
                "extensions": [],
                "filePaths": [],
                "directoryPaths": []
              },
              "projects": {}
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadAndApplyConfiguredRule() throws IOException {
        ProjectRegistry registry = registry();
        writeConfiguration(VALID_JSON);

        PolicyConfiguration configuration =
                ProjectContextApplication.loadPolicyConfiguration(registry);

        assertEquals(
                List.of("secret.txt"),
                configuration.global().fileNames()
        );

        ProjectAccessPolicy accessPolicy = new ProjectAccessPolicy(
                registry,
                configuration
        );
        Path file = Files.createFile(
                registry.getRoot("project-context").resolve("secret.txt")
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> accessPolicy.checkAccess("project-context", file)
        );

        assertEquals(
                "Access denied by GLOBAL FILE_NAME rule: secret.txt",
                exception.getMessage()
        );
    }

    @Test
    void shouldLoadEmptyRules() throws IOException {
        ProjectRegistry registry = registry();
        writeConfiguration(VALID_JSON.replace("[\"secret.txt\"]", "[]"));

        assertEquals(
                PolicyConfiguration.empty(),
                ProjectContextApplication.loadPolicyConfiguration(registry)
        );
    }

    @Test
    void shouldRejectMissingConfiguration() throws IOException {
        ProjectRegistry registry = registry();
        Files.createDirectory(tempDir.resolve("config"));

        assertThrows(
                NoSuchFileException.class,
                () -> ProjectContextApplication.loadPolicyConfiguration(registry)
        );
    }

    @Test
    void shouldRejectMalformedConfiguration() throws IOException {
        ProjectRegistry registry = registry();
        writeConfiguration("{");

        assertThrows(
                IOException.class,
                () -> ProjectContextApplication.loadPolicyConfiguration(registry)
        );
    }

    @Test
    void shouldRejectInvalidRule() throws IOException {
        ProjectRegistry registry = registry();
        writeConfiguration(VALID_JSON.replace("secret.txt", "private/secret.txt"));

        assertThrows(
                IOException.class,
                () -> ProjectContextApplication.loadPolicyConfiguration(registry)
        );
    }

    private ProjectRegistry registry() throws IOException {
        return new ProjectRegistry(
                Map.of("project-context", tempDir.toRealPath())
        );
    }

    private void writeConfiguration(String json) throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("config"));

        Files.writeString(
                directory.resolve("policy.json"),
                json
        );
    }
}
