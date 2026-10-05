package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.ListProjectsService;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListProjectsToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateListProjectsToolSpecification() throws IOException {
        ListProjectsTool listProjectsTool = tool();

        McpServerFeatures.SyncToolSpecification specification =
                listProjectsTool.specification();

        assertEquals("list_projects", specification.tool().name());
        assertEquals("List Projects", specification.tool().title());
        assertEquals(
                "Lists registered Project Context projects",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() throws IOException {
        ListProjectsTool listProjectsTool = tool();

        McpSchema.ToolAnnotations annotations =
                listProjectsTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() throws IOException {
        ListProjectsTool listProjectsTool = tool();

        Map<String, Object> inputSchema =
                listProjectsTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(Map.of(), inputSchema.get("properties"));
        assertEquals(false, inputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposeOutputSchema() throws IOException {
        ListProjectsTool listProjectsTool = tool();

        Map<String, Object> outputSchema =
                listProjectsTool.specification().tool().outputSchema();

        assertEquals("object", outputSchema.get("type"));
        assertEquals(
                Map.of(
                        "projects",
                        Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "string"
                                )
                        )
                ),
                outputSchema.get("properties")
        );
        assertEquals(
                List.of("projects"),
                outputSchema.get("required")
        );
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() throws IOException {
        ListProjectsTool listProjectsTool = tool();

        Map<String, Object> meta =
                listProjectsTool.specification().tool().meta();

        assertEquals(
                "public",
                meta.get("openai/visibility")
        );
    }

    @Test
    void shouldRejectNullListProjectsService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ListProjectsTool(null)
        );

        assertEquals(
                "listProjectsService must not be null",
                exception.getMessage()
        );
    }

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

        ListProjectsTool listProjectsTool =
                new ListProjectsTool(
                        new ListProjectsService(registry)
                );

        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("list_projects")
                        .arguments(Map.of())
                        .build();

        McpSchema.CallToolResult result =
                listProjectsTool.specification()
                        .callHandler()
                        .apply(null, request);

        assertEquals(
                Map.of(
                        "projects",
                        List.of("first", "second")
                ),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    private ListProjectsTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new ListProjectsTool(
                new ListProjectsService(registry)
        );
    }
}