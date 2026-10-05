package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.SearchService;
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

class SearchToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateSearchToolSpecification() throws IOException {
        SearchTool searchTool = tool();

        McpServerFeatures.SyncToolSpecification specification =
                searchTool.specification();

        assertEquals("search", specification.tool().name());
        assertEquals("Search", specification.tool().title());
        assertEquals(
                "Searches for files by name and extension in a registered Project Context project",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() throws IOException {
        SearchTool searchTool = tool();

        McpSchema.ToolAnnotations annotations =
                searchTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() throws IOException {
        SearchTool searchTool = tool();

        Map<String, Object> inputSchema =
                searchTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(
                List.of(
                        "projectName",
                        "fileName",
                        "extension"
                ),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposeOutputSchema() throws IOException {
        SearchTool searchTool = tool();

        Map<String, Object> outputSchema =
                searchTool.specification().tool().outputSchema();

        assertEquals("object", outputSchema.get("type"));
        assertEquals(
                Map.of(
                        "paths",
                        Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "string"
                                )
                        )
                ),
                outputSchema.get("properties")
        );
        assertEquals(List.of("paths"), outputSchema.get("required"));
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() throws IOException {
        SearchTool searchTool = tool();

        Map<String, Object> meta =
                searchTool.specification().tool().meta();

        assertEquals("public", meta.get("openai/visibility"));
    }

    @Test
    void shouldRejectNullSearchService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new SearchTool(null)
        );

        assertEquals(
                "searchService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnMatchingPaths() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("ProjectContextApplication.java")
        );

        SearchTool searchTool = tool();

        McpSchema.CallToolResult result = call(
                searchTool,
                Map.of(
                        "projectName", "project",
                        "fileName", "contextapp",
                        "extension", "java"
                )
        );

        assertEquals(
                Map.of(
                        "paths",
                        List.of(file.toRealPath().toString())
                ),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldLimitSearchToOptionalDirectory() throws IOException {
        Path mainDirectory = Files.createDirectories(
                tempDir.resolve("src/main")
        );

        Path testDirectory = Files.createDirectories(
                tempDir.resolve("src/test")
        );

        Path mainFile = Files.createFile(
                mainDirectory.resolve("Example.java")
        );

        Files.createFile(
                testDirectory.resolve("Example.java")
        );

        SearchTool searchTool = tool();

        McpSchema.CallToolResult result = call(
                searchTool,
                Map.of(
                        "projectName", "project",
                        "fileName", "example",
                        "extension", "java",
                        "directoryPath", mainDirectory.toString()
                )
        );

        assertEquals(
                Map.of(
                        "paths",
                        List.of(mainFile.toRealPath().toString())
                ),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnErrorForBlankFileName() throws IOException {
        SearchTool searchTool = tool();

        McpSchema.CallToolResult result = call(
                searchTool,
                Map.of(
                        "projectName", "project",
                        "fileName", " ",
                        "extension", "java"
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(
                "argument must be a non-blank string: fileName",
                content.text()
        );
        assertTrue(result.isError());
    }

    @Test
    void shouldReturnErrorForBlankOptionalDirectoryPath() throws IOException {
        SearchTool searchTool = tool();

        McpSchema.CallToolResult result = call(
                searchTool,
                Map.of(
                        "projectName", "project",
                        "fileName", "example",
                        "extension", "java",
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
        SearchTool searchTool = tool();

        McpSchema.CallToolResult result = call(
                searchTool,
                Map.of(
                        "projectName", "unknown",
                        "fileName", "example",
                        "extension", "java"
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

    private SearchTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new SearchTool(
                new SearchService(registry)
        );
    }

    private static McpSchema.CallToolResult call(
            SearchTool searchTool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("search")
                        .arguments(arguments)
                        .build();

        return searchTool.specification()
                .callHandler()
                .apply(null, request);
    }
}
