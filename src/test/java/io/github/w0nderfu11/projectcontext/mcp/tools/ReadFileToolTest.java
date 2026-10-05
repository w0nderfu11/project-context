package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.ReadFileService;
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

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateReadFileToolSpecification() throws IOException {
        ReadFileTool readFileTool = tool();

        McpServerFeatures.SyncToolSpecification specification =
                readFileTool.specification();

        assertEquals("read_file", specification.tool().name());
        assertEquals("Read File", specification.tool().title());
        assertEquals(
                "Reads a text file from a registered Project Context project",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() throws IOException {
        ReadFileTool readFileTool = tool();

        McpSchema.ToolAnnotations annotations =
                readFileTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() throws IOException {
        ReadFileTool readFileTool = tool();

        Map<String, Object> inputSchema =
                readFileTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(
                List.of("projectName", "filePath"),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposeOutputSchema() throws IOException {
        ReadFileTool readFileTool = tool();

        Map<String, Object> outputSchema =
                readFileTool.specification().tool().outputSchema();

        assertEquals("object", outputSchema.get("type"));
        assertEquals(
                Map.of(
                        "content",
                        Map.of("type", "string")
                ),
                outputSchema.get("properties")
        );
        assertEquals(List.of("content"), outputSchema.get("required"));
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() throws IOException {
        ReadFileTool readFileTool = tool();

        Map<String, Object> meta =
                readFileTool.specification().tool().meta();

        assertEquals("public", meta.get("openai/visibility"));
    }

    @Test
    void shouldRejectNullReadFileService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ReadFileTool(null)
        );

        assertEquals(
                "readFileService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnFileContent() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "public class Example {}"
        );

        ReadFileTool readFileTool = tool();

        McpSchema.CallToolResult result = call(
                readFileTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString()
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals("public class Example {}", content.text());
        assertEquals(
                Map.of("content", "public class Example {}"),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnErrorForBlankFilePath() throws IOException {
        ReadFileTool readFileTool = tool();

        McpSchema.CallToolResult result = call(
                readFileTool,
                Map.of(
                        "projectName", "project",
                        "filePath", " "
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(
                "argument must be a non-blank string: filePath",
                content.text()
        );
        assertTrue(result.isError());
    }

    @Test
    void shouldReturnErrorForUnregisteredProject() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileTool readFileTool = tool();

        McpSchema.CallToolResult result = call(
                readFileTool,
                Map.of(
                        "projectName", "unknown",
                        "filePath", file.toString()
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

    private ReadFileTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new ReadFileTool(
                new ReadFileService(registry)
        );
    }

    private static McpSchema.CallToolResult call(
            ReadFileTool readFileTool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("read_file")
                        .arguments(arguments)
                        .build();

        return readFileTool.specification()
                .callHandler()
                .apply(null, request);
    }
}
