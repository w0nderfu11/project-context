
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

class ReadFileRangeToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateReadFileRangeToolSpecification() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        McpServerFeatures.SyncToolSpecification specification =
                readFileRangeTool.specification();

        assertEquals("read_file_range", specification.tool().name());
        assertEquals("Read File Range", specification.tool().title());
        assertEquals(
                "Reads a specific line range from a text file in a registered Project Context project (maximum 300 lines and 256 KiB)",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.ToolAnnotations annotations =
                readFileRangeTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        Map<String, Object> inputSchema =
                readFileRangeTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(
                List.of(
                        "projectName",
                        "filePath",
                        "startLine",
                        "endLine"
                ),
                inputSchema.get("required")
        );
        assertEquals(false, inputSchema.get("additionalProperties"));

        Map<?, ?> properties =
                (Map<?, ?>) inputSchema.get("properties");

        assertEquals(
                "string",
                ((Map<?, ?>) properties.get("projectName")).get("type")
        );
        assertEquals(
                "string",
                ((Map<?, ?>) properties.get("filePath")).get("type")
        );
        assertEquals(
                "integer",
                ((Map<?, ?>) properties.get("startLine")).get("type")
        );
        assertEquals(
                "integer",
                ((Map<?, ?>) properties.get("endLine")).get("type")
        );
        assertEquals(
                1,
                ((Map<?, ?>) properties.get("startLine")).get("minimum")
        );
        assertEquals(
                1,
                ((Map<?, ?>) properties.get("endLine")).get("minimum")
        );
    }

    @Test
    void shouldExposeOutputSchema() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        Map<String, Object> outputSchema =
                readFileRangeTool.specification().tool().outputSchema();

        assertEquals("object", outputSchema.get("type"));
        assertEquals(
                Map.of(
                        "content",
                        Map.of("type", "string")
                ),
                outputSchema.get("properties")
        );
        assertEquals(
                List.of("content"),
                outputSchema.get("required")
        );
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        Map<String, Object> meta =
                readFileRangeTool.specification().tool().meta();

        assertEquals("public", meta.get("openai/visibility"));
    }

    @Test
    void shouldRejectNullReadFileService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ReadFileRangeTool(null)
        );

        assertEquals(
                "readFileService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnRequestedLineRange() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird\nfourth"
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 2,
                        "endLine", 3
                )
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals("second\nthird", content.text());
        assertEquals(
                Map.of("content", "second\nthird"),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnSingleLine() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 2,
                        "endLine", 2
                )
        );

        assertEquals(
                Map.of("content", "second"),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnAvailableLinesBeyondEndOfFile() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 2,
                        "endLine", 100
                )
        );

        assertEquals(
                Map.of("content", "second\nthird"),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnEmptyContentBeyondFileLength() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond"
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 10,
                        "endLine", 20
                )
        );

        assertEquals(
                Map.of("content", ""),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldAcceptLongLineArguments() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird"
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 2L,
                        "endLine", 3L
                )
        );

        assertEquals(
                Map.of("content", "second\nthird"),
                result.structuredContent()
        );
        assertFalse(result.isError());
    }

    @Test
    void shouldReturnErrorForBlankProjectName() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", " ",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", 10
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: projectName"
        );
    }

    @Test
    void shouldReturnErrorForBlankFilePath() throws IOException {
        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", " ",
                        "startLine", 1,
                        "endLine", 10
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: filePath"
        );
    }

    @Test
    void shouldReturnErrorForMissingStartLine() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "endLine", 10
                )
        );

        assertError(
                result,
                "argument must be a 32-bit integer: startLine"
        );
    }

    @Test
    void shouldReturnErrorForInvalidStartLineType() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", "1",
                        "endLine", 10
                )
        );

        assertError(
                result,
                "argument must be a 32-bit integer: startLine"
        );
    }

    @Test
    void shouldReturnErrorForInvalidEndLineType() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", 10.5
                )
        );

        assertError(
                result,
                "argument must be a 32-bit integer: endLine"
        );
    }

    @Test
    void shouldRejectIntegerOutsideSupportedRange() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", (long) Integer.MAX_VALUE + 1
                )
        );

        assertError(
                result,
                "argument must be a 32-bit integer: endLine"
        );
    }

    @Test
    void shouldReturnErrorForInvalidLineRange() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 10,
                        "endLine", 5
                )
        );

        assertError(
                result,
                "invalid line range: startLine=10, endLine=5"
        );
    }

    @Test
    void shouldReturnErrorForExceedingLineLimit() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", 301
                )
        );

        assertError(
                result,
                "line range exceeds maximum allowed lines: 300"
        );
    }

    @Test
    void shouldReturnErrorForExceedingResultSize() throws IOException {
        Path file = Files.writeString(
                tempDir.resolve("large.txt"),
                "a".repeat(256 * 1024 + 1)
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", 1
                )
        );

        assertError(
                result,
                "file range exceeds maximum allowed size: 262144 bytes"
        );
    }

    @Test
    void shouldHidePathForUnregisteredProject() throws IOException {
        Path file = Files.createFile(
                tempDir.resolve("Example.java")
        );

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "unknown",
                        "filePath", file.toString(),
                        "startLine", 1,
                        "endLine", 10
                )
        );

        assertError(
                result,
                "Unable to read the requested file range"
        );
    }

    @Test
    void shouldHidePathOutsideProjectRoot() throws IOException {
        Path project = Files.createDirectory(
                tempDir.resolve("project")
        );

        Path outsideFile = Files.createFile(
                tempDir.resolve("secret.txt")
        );

        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );

        ReadFileRangeTool readFileRangeTool =
                new ReadFileRangeTool(
                        new ReadFileService(registry)
                );

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", outsideFile.toString(),
                        "startLine", 1,
                        "endLine", 10
                )
        );

        assertError(
                result,
                "Unable to read the requested file range"
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertFalse(content.text().contains(outsideFile.toString()));
    }

    @Test
    void shouldHidePathForMissingFile() throws IOException {
        Path missingFile = tempDir.resolve("missing.txt");

        ReadFileRangeTool readFileRangeTool = tool();

        McpSchema.CallToolResult result = call(
                readFileRangeTool,
                Map.of(
                        "projectName", "project",
                        "filePath", missingFile.toString(),
                        "startLine", 1,
                        "endLine", 10
                )
        );

        assertError(
                result,
                "Unable to read the requested file range"
        );
    }

    private ReadFileRangeTool tool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new ReadFileRangeTool(
                new ReadFileService(registry)
        );
    }

    private static McpSchema.CallToolResult call(
            ReadFileRangeTool readFileRangeTool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("read_file_range")
                        .arguments(arguments)
                        .build();

        return readFileRangeTool.specification()
                .callHandler()
                .apply(null, request);
    }

    private static void assertError(
            McpSchema.CallToolResult result,
            String expectedMessage
    ) {
        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertEquals(expectedMessage, content.text());
        assertTrue(result.isError());
    }
}
