
package io.github.w0nderfu11.projectcontext.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.w0nderfu11.projectcontext.application.BatchReadService;
import io.github.w0nderfu11.projectcontext.application.ReadFileService;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchReadToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeToolSpecification() throws IOException {
        McpSchema.Tool tool = tool().specification().tool();

        assertEquals("read_files", tool.name());
        assertEquals("Read Files", tool.title());
        assertEquals("public", tool.meta().get("openai/visibility"));

        assertTrue(tool.annotations().readOnlyHint());
        assertFalse(tool.annotations().destructiveHint());
        assertFalse(tool.annotations().openWorldHint());
        assertTrue(tool.annotations().idempotentHint());

        Map<String, Object> inputSchema = tool.inputSchema();

        assertEquals(
                List.of("projectName", "filePaths"),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));

        Map<?, ?> properties =
                (Map<?, ?>) inputSchema.get("properties");

        Map<?, ?> paths =
                (Map<?, ?>) properties.get("filePaths");

        assertEquals("array", paths.get("type"));
        assertEquals(1, paths.get("minItems"));
        assertEquals(10, paths.get("maxItems"));
        assertEquals(true, paths.get("uniqueItems"));

        Map<String, Object> outputSchema = tool.outputSchema();

        assertEquals(
                List.of("results"),
                outputSchema.get("required")
        );
    }

    @Test
    void shouldRejectNullBatchReadService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BatchReadTool(null)
        );

        assertEquals(
                "batchReadService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReadMultipleFilesInOrder() throws IOException {
        Path first = Files.writeString(
                tempDir.toRealPath().resolve("First.java"),
                "first content"
        );
        Path second = Files.writeString(
                tempDir.toRealPath().resolve("Second.java"),
                "second content"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(second.toString(), first.toString())
        );

        assertFalse(result.isError());

        Map<?, ?> firstResult = resultAt(result, 0);
        Map<?, ?> secondResult = resultAt(result, 1);

        assertEquals(second.toString(), firstResult.get("filePath"));
        assertEquals("second content", firstResult.get("content"));
        assertNull(firstResult.get("error"));

        assertEquals(first.toString(), secondResult.get("filePath"));
        assertEquals("first content", secondResult.get("content"));
        assertNull(secondResult.get("error"));

        McpSchema.TextContent text =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(
                2,
                new ObjectMapper()
                        .readTree(text.text())
                        .get("results")
                        .size()
        );
    }

    @Test
    void shouldContinueAfterMissingFile() throws IOException {
        Path missing = tempDir.toRealPath().resolve("Missing.java");
        Path valid = Files.writeString(
                tempDir.toRealPath().resolve("Valid.java"),
                "valid content"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(missing.toString(), valid.toString())
        );

        assertFalse(result.isError());

        Map<?, ?> missingResult = resultAt(result, 0);
        Map<?, ?> validResult = resultAt(result, 1);

        assertNull(missingResult.get("content"));
        assertEquals("File not found", missingResult.get("error"));

        assertEquals("valid content", validResult.get("content"));
        assertNull(validResult.get("error"));
    }

    @Test
    void shouldHandleOversizedFileIndependently() throws IOException {
        Path large = Files.writeString(
                tempDir.toRealPath().resolve("Large.java"),
                "x".repeat(256 * 1024 + 1)
        );

        Path valid = Files.writeString(
                tempDir.toRealPath().resolve("Valid.java"),
                "ok"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(large.toString(), valid.toString())
        );

        assertFalse(result.isError());

        Map<?, ?> largeResult = resultAt(result, 0);
        Map<?, ?> validResult = resultAt(result, 1);

        assertNull(largeResult.get("content"));
        assertEquals(
                "file exceeds maximum allowed size: 262144 bytes",
                largeResult.get("error")
        );

        assertEquals("ok", validResult.get("content"));
        assertNull(validResult.get("error"));
    }

    @Test
    void shouldRejectDuplicatePaths() throws IOException {
        Path file = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(file.toString(), file.toString())
        );

        assertError(
                result,
                "Batch must not contain duplicate entries"
        );
    }

    @Test
    void shouldRejectMoreThanTenFiles() throws IOException {
        List<String> paths = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            paths.add(
                    tempDir.toRealPath().resolve("File" + i + ".java").toString()
            );
        }

        McpSchema.CallToolResult result = call(tool(), paths);

        assertError(
                result,
                "Batch size must be between 1 and 10"
        );
    }

    @Test
    void shouldRejectBlankProjectName() throws IOException {
        McpSchema.CallToolResult result = call(
                tool(),
                Map.of(
                        "projectName", " ",
                        "filePaths", List.of(
                                tempDir.toRealPath().resolve("Example.java").toString()
                        )
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: projectName"
        );
    }

    @Test
    void shouldHideInternalErrorsForUnknownProject() throws IOException {
        Path file = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        McpSchema.CallToolResult result = call(
                tool(),
                Map.of(
                        "projectName", "unknown",
                        "filePaths", List.of(file.toString())
                )
        );

        assertError(
                result,
                "Invalid batch read request"
        );
    }

    private BatchReadTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        BatchReadService service = new BatchReadService(
                new ReadFileService(registry)
        );

        return new BatchReadTool(service);
    }

    private static McpSchema.CallToolResult call(
            BatchReadTool tool,
            List<String> filePaths
    ) {
        return call(
                tool,
                Map.of(
                        "projectName", "project",
                        "filePaths", filePaths
                )
        );
    }

    private static McpSchema.CallToolResult call(
            BatchReadTool tool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("read_files")
                        .arguments(arguments)
                        .build();

        return tool.specification()
                .callHandler()
                .apply(null, request);
    }

    private static Map<?, ?> resultAt(
            McpSchema.CallToolResult result,
            int index
    ) {
        Map<?, ?> body = (Map<?, ?>) result.structuredContent();
        List<?> results = (List<?>) body.get("results");

        return (Map<?, ?>) results.get(index);
    }

    private static void assertError(
            McpSchema.CallToolResult result,
            String expectedMessage
    ) {
        assertTrue(result.isError());

        McpSchema.TextContent text =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(expectedMessage, text.text());
    }
}
