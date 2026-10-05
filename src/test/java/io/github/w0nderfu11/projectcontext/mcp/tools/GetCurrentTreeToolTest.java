package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.GetCurrentTreeService;
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

class GetCurrentTreeToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateGetCurrentTreeToolSpecification() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        McpServerFeatures.SyncToolSpecification specification =
                getCurrentTreeTool.specification();

        assertEquals("get_current_tree", specification.tool().name());
        assertEquals("Get Current Tree", specification.tool().title());
        assertEquals(
                "Lists files and directories one level below a directory in a registered Project Context project",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        McpSchema.ToolAnnotations annotations =
                getCurrentTreeTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        Map<String, Object> inputSchema =
                getCurrentTreeTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(
                List.of("projectName", "directoryPath"),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposeOutputSchema() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        Map<String, Object> outputSchema =
                getCurrentTreeTool.specification().tool().outputSchema();

        assertEquals("object", outputSchema.get("type"));
        assertEquals(List.of("entries"), outputSchema.get("required"));
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        Map<String, Object> meta =
                getCurrentTreeTool.specification().tool().meta();

        assertEquals("public", meta.get("openai/visibility"));
    }

    @Test
    void shouldRejectNullGetCurrentTreeService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetCurrentTreeTool(null)
        );

        assertEquals(
                "getCurrentTreeService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnDirectoryEntries() throws IOException {
        Path directory = Files.createDirectory(
                tempDir.resolve("src")
        );

        Path nestedDirectory = Files.createDirectory(
                directory.resolve("main")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        GetCurrentTreeTool getCurrentTreeTool = tool();

        McpSchema.CallToolResult result = call(
                getCurrentTreeTool,
                Map.of(
                        "projectName", "project",
                        "directoryPath", directory.toString()
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, String> entries =
                (Map<String, String>) result.structuredContent()
                        .get("entries");

        assertEquals(2, entries.size());
        assertEquals(
                "DIRECTORY",
                entries.get(nestedDirectory.toString())
        );
        assertEquals(
                "FILE",
                entries.get(file.toString())
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnErrorForBlankDirectoryPath() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        McpSchema.CallToolResult result = call(
                getCurrentTreeTool,
                Map.of(
                        "projectName", "project",
                        "directoryPath", " "
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(
                "argument must be a non-blank string: directoryPath",
                content.text()
        );
        assertTrue(result.isError());
    }

    @Test
    void shouldReturnErrorForUnregisteredProject() throws IOException {
        GetCurrentTreeTool getCurrentTreeTool = tool();

        McpSchema.CallToolResult result = call(
                getCurrentTreeTool,
                Map.of(
                        "projectName", "unknown",
                        "directoryPath", tempDir.toString()
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(
                "project is not registered: unknown",
                content.text()
        );
        assertTrue(result.isError());
    }

    private GetCurrentTreeTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new GetCurrentTreeTool(
                new GetCurrentTreeService(registry)
        );
    }

    private static McpSchema.CallToolResult call(
            GetCurrentTreeTool getCurrentTreeTool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("get_current_tree")
                        .arguments(arguments)
                        .build();

        return getCurrentTreeTool.specification()
                .callHandler()
                .apply(null, request);
    }
}
