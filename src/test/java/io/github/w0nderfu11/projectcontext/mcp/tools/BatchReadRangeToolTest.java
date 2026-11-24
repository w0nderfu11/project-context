
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

class BatchReadRangeToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeToolSpecification() throws IOException {
        McpSchema.Tool tool = tool().specification().tool();

        assertEquals("read_file_ranges", tool.name());
        assertEquals("Read File Ranges", tool.title());
        assertEquals("public", tool.meta().get("openai/visibility"));

        assertTrue(tool.annotations().readOnlyHint());
        assertFalse(tool.annotations().destructiveHint());
        assertFalse(tool.annotations().openWorldHint());
        assertTrue(tool.annotations().idempotentHint());

        Map<String, Object> inputSchema = tool.inputSchema();

        assertEquals(
                List.of("projectName", "ranges"),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));

        Map<?, ?> properties =
                (Map<?, ?>) inputSchema.get("properties");

        Map<?, ?> ranges =
                (Map<?, ?>) properties.get("ranges");

        assertEquals("array", ranges.get("type"));
        assertEquals(1, ranges.get("minItems"));
        assertEquals(10, ranges.get("maxItems"));
        assertEquals(true, ranges.get("uniqueItems"));

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
                () -> new BatchReadRangeTool(null)
        );

        assertEquals(
                "batchReadService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReadMultipleRangesOfSameFile() throws IOException {
        Path file = Files.writeString(
                tempDir.toRealPath().resolve("Example.java"),
                "first\nsecond\nthird\nfourth"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(file, 1, 2),
                        range(file, 3, 4)
                )
        );

        assertFalse(result.isError());

        Map<?, ?> firstResult = resultAt(result, 0);
        Map<?, ?> secondResult = resultAt(result, 1);

        assertEquals(file.toString(), firstResult.get("filePath"));
        assertEquals(1, firstResult.get("startLine"));
        assertEquals(2, firstResult.get("endLine"));
        assertEquals("first\nsecond", firstResult.get("content"));
        assertNull(firstResult.get("error"));

        assertEquals(file.toString(), secondResult.get("filePath"));
        assertEquals(3, secondResult.get("startLine"));
        assertEquals(4, secondResult.get("endLine"));
        assertEquals("third\nfourth", secondResult.get("content"));
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
    void shouldHandleInvalidRangeIndependently() throws IOException {
        Path file = Files.writeString(
                tempDir.toRealPath().resolve("Example.java"),
                "first\nsecond\nthird"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(file, 10, 5),
                        range(file, 1, 2)
                )
        );

        assertFalse(result.isError());

        Map<?, ?> invalidResult = resultAt(result, 0);
        Map<?, ?> validResult = resultAt(result, 1);

        assertNull(invalidResult.get("content"));
        assertEquals(
                "invalid line range: startLine=10, endLine=5",
                invalidResult.get("error")
        );

        assertEquals(
                "first\nsecond",
                validResult.get("content")
        );
        assertNull(validResult.get("error"));
    }

    @Test
    void shouldHandleMissingFileIndependently() throws IOException {
        Path missing = tempDir.toRealPath().resolve("Missing.java");
        Path valid = Files.writeString(
                tempDir.toRealPath().resolve("Valid.java"),
                "hello"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(missing, 1, 2),
                        range(valid, 1, 1)
                )
        );

        assertFalse(result.isError());

        Map<?, ?> missingResult = resultAt(result, 0);
        Map<?, ?> validResult = resultAt(result, 1);

        assertNull(missingResult.get("content"));
        assertEquals("File not found", missingResult.get("error"));

        assertEquals("hello", validResult.get("content"));
        assertNull(validResult.get("error"));
    }

    @Test
    void shouldAcceptLongLineArguments() throws IOException {
        Path file = Files.writeString(
                tempDir.toRealPath().resolve("Example.java"),
                "first\nsecond\nthird"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(file, 2L, 3L)
                )
        );

        assertFalse(result.isError());

        Map<?, ?> item = resultAt(result, 0);

        assertEquals(2, item.get("startLine"));
        assertEquals(3, item.get("endLine"));
        assertEquals("second\nthird", item.get("content"));
    }

    @Test
    void shouldRejectInvalidIntegerArgument() throws IOException {
        Path file = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(file, 1.5, 3)
                )
        );

        assertError(
                result,
                "argument must be a 32-bit integer: startLine"
        );
    }

    @Test
    void shouldRejectDuplicateRanges() throws IOException {
        Path file = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        Map<String, Object> request = range(file, 1, 2);

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(request, request)
        );

        assertError(
                result,
                "Batch must not contain duplicate entries"
        );
    }

    @Test
    void shouldRejectMoreThanTenRanges() throws IOException {
        Path file = Files.createFile(
                tempDir.toRealPath().resolve("Example.java")
        );

        List<Map<String, Object>> ranges = new ArrayList<>();

        for (int i = 1; i <= 11; i++) {
            ranges.add(range(file, i, i));
        }

        McpSchema.CallToolResult result = call(tool(), ranges);

        assertError(
                result,
                "Batch size must be between 1 and 10"
        );
    }

    @Test
    void shouldEnforceLineLimitIndependently() throws IOException {
        Path file = Files.writeString(
                tempDir.toRealPath().resolve("Example.java"),
                "first\nsecond"
        );

        McpSchema.CallToolResult result = call(
                tool(),
                List.of(
                        range(file, 1, 301),
                        range(file, 2, 2)
                )
        );

        assertFalse(result.isError());

        Map<?, ?> invalidResult = resultAt(result, 0);
        Map<?, ?> validResult = resultAt(result, 1);

        assertNull(invalidResult.get("content"));
        assertEquals(
                "line range exceeds maximum allowed lines: 300",
                invalidResult.get("error")
        );

        assertEquals("second", validResult.get("content"));
        assertNull(validResult.get("error"));
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
                        "ranges", List.of(range(file, 1, 2))
                )
        );

        assertError(
                result,
                "Invalid batch range read request"
        );
    }

    private BatchReadRangeTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        BatchReadService service = new BatchReadService(
                new ReadFileService(registry)
        );

        return new BatchReadRangeTool(service);
    }

    private static Map<String, Object> range(
            Path filePath,
            Object startLine,
            Object endLine
    ) {
        return Map.of(
                "filePath", filePath.toString(),
                "startLine", startLine,
                "endLine", endLine
        );
    }

    private static McpSchema.CallToolResult call(
            BatchReadRangeTool tool,
            List<Map<String, Object>> ranges
    ) {
        return call(
                tool,
                Map.of(
                        "projectName", "project",
                        "ranges", ranges
                )
        );
    }

    private static McpSchema.CallToolResult call(
            BatchReadRangeTool tool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("read_file_ranges")
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
