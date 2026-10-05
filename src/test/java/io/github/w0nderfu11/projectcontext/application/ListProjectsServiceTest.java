package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListProjectsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnRegisteredProjects() throws IOException {
        Path firstProject = Files.createDirectory(
                tempDir.resolve("first-project")
        );

        Path secondProject = Files.createDirectory(
                tempDir.resolve("second-project")
        );

        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "second", secondProject,
                        "first", firstProject
                )
        );

        ListProjectsService listProjectsService =
                new ListProjectsService(registry);

        assertEquals(
                List.of("first", "second"),
                listProjectsService.list()
        );
    }

    @Test
    void shouldRejectNullProjectRegistry() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ListProjectsService(null)
        );

        assertEquals(
                "projectRegistry must not be null",
                exception.getMessage()
        );
    }
}