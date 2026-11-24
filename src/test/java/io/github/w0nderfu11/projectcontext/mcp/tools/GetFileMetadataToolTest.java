package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.GetFileMetadataService;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetFileMetadataToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeMetadataToolSpecification() throws IOException {
        GetFileMetadataTool getFileMetadataTool = tool(tempDir.toRealPath());

        McpSchema.Tool specification =
                getFileMetadataTool.specification().tool();

        assertEquals("get_file_metadata", specification.name());
        assertEquals(
                List.of("projectName", "filePath"),
                specification.inputSchema().get("required")
        );
        assertEquals(
                List.of("filePath", "sizeBytes", "lastModified"),
                specification.outputSchema().get("required")
        );
        assertEquals(false, specification.outputSchema().get("additionalProperties"));
        assertTrue(specification.annotations().readOnlyHint());
        assertTrue(specification.annotations().idempotentHint());
        assertFalse(specification.annotations().destructiveHint());
        assertFalse(specification.annotations().openWorldHint());
        assertEquals("public", specification.meta().get("openai/visibility"));
    }

    @Test
    void shouldRejectNullService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetFileMetadataTool(null)
        );

        assertEquals(
                "getFileMetadataService must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnMetadata() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.writeString(
                project.resolve("example.txt"),
                "test"
        );

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString()
                )
        );

        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertEquals(
                Map.of(
                        "filePath", file.toString(),
                        "sizeBytes", 4L,
                        "lastModified", Files.getLastModifiedTime(file).toInstant().toString()
                ),
                result.structuredContent()
        );
        assertEquals(
                result.structuredContent().toString(),
                ((McpSchema.TextContent) result.content().getFirst()).text()
        );
    }

    @Test
    void shouldRejectMissingProjectName() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of("filePath", "example")
        );

        assertError(
                result,
                "argument must be a non-blank string: projectName"
        );
    }

    @Test
    void shouldRejectMissingFilePath() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of("projectName", "project")
        );

        assertError(
                result,
                "argument must be a non-blank string: filePath"
        );
    }

    @Test
    void shouldRejectBlankProjectName() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", " ",
                        "filePath", project.resolve("example").toString()
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: projectName"
        );
    }

    @Test
    void shouldRejectBlankFilePath() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", " "
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: filePath"
        );
    }

    @Test
    void shouldRejectIncorrectProjectNameType() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", 42,
                        "filePath", project.resolve("example").toString()
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: projectName"
        );
    }

    @Test
    void shouldRejectIncorrectFilePathType() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", 42
                )
        );

        assertError(
                result,
                "argument must be a non-blank string: filePath"
        );
    }

    @Test
    void shouldRejectRelativePath() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", "relative"
                )
        );

        assertError(
                result,
                "requestedPath must be absolute"
        );
    }

    @Test
    void shouldRejectTraversalSegments() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", project.resolve("../outside").toString()
                )
        );

        assertError(
                result,
                "requestedPath must not contain '..' segments"
        );
    }

    @Test
    void shouldRejectPathOutsideProject() throws IOException {
        Path root = tempDir.toRealPath();
        Path project = Files.createDirectory(root.resolve("project"));
        Path outside = Files.createFile(root.resolve("outside"));

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", outside.toString()
                )
        );

        assertError(
                result,
                "Path is outside the registered project root"
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertFalse(content.text().contains(outside.toString()));
    }

    @Test
    void shouldRejectDirectory() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", project.toString()
                )
        );

        assertError(
                result,
                "file path must point to a regular file"
        );
    }

    @Test
    void shouldRejectMissingFile() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", project.resolve("missing").toString()
                )
        );

        assertError(
                result,
                "Unable to get the requested file metadata"
        );
    }

    @Test
    void shouldRejectInvalidPath() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "project",
                        "filePath", "invalid\0path"
                )
        );

        assertError(
                result,
                "Unable to get the requested file metadata"
        );
    }

    @Test
    void shouldRejectUnknownProject() throws IOException {
        Path project = tempDir.toRealPath();

        McpSchema.CallToolResult result = call(
                tool(project),
                Map.of(
                        "projectName", "unknown",
                        "filePath", project.toString()
                )
        );

        assertError(
                result,
                "project is not registered"
        );
    }

    @Test
    void shouldRejectFileExcludedByGlobalExtension() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.createFile(project.resolve("server.log"));

        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".log"),
                List.of(),
                List.of()
        );
        GetFileMetadataTool getFileMetadataTool = tool(
                project,
                new PolicyConfiguration(
                        global,
                        Map.of()
                )
        );
        McpSchema.CallToolResult result = call(
                getFileMetadataTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString()
                )
        );

        assertError(
                result,
                "Access denied by GLOBAL EXTENSION rule: .log"
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertFalse(content.text().contains(file.toString()));
    }

    @Test
    void shouldRejectFileExcludedByProjectPath() throws IOException {
        Path project = tempDir.toRealPath();
        Path file = Files.createFile(project.resolve("secret.txt"));

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("secret.txt"),
                List.of()
        );
        GetFileMetadataTool getFileMetadataTool = tool(
                project,
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", rules)
                )
        );
        McpSchema.CallToolResult result = call(
                getFileMetadataTool,
                Map.of(
                        "projectName", "project",
                        "filePath", file.toString()
                )
        );

        assertError(
                result,
                "Access denied by PROJECT FILE_PATH rule: secret.txt"
        );

        McpSchema.TextContent content =
                (McpSchema.TextContent) result.content().getFirst();

        assertFalse(content.text().contains(file.toString()));
    }

    private GetFileMetadataTool tool(Path project) throws IOException {
        return tool(
                project,
                PolicyConfiguration.empty()
        );
    }

    private GetFileMetadataTool tool(
            Path project,
            PolicyConfiguration configuration
    ) throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", project)
        );
        ProjectAccessPolicy accessPolicy = new ProjectAccessPolicy(
                registry,
                configuration
        );
        GetFileMetadataService service = new GetFileMetadataService(
                registry,
                accessPolicy
        );

        return new GetFileMetadataTool(service);
    }

    private static McpSchema.CallToolResult call(
            GetFileMetadataTool getFileMetadataTool,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("get_file_metadata")
                        .arguments(arguments)
                        .build();

        return getFileMetadataTool.specification()
                .callHandler()
                .apply(null, request);
    }

    private static void assertError(
            McpSchema.CallToolResult result,
            String expectedMessage
    ) {
        assertEquals(1, result.content().size());
        assertTrue(result.isError());
        assertNull(result.structuredContent());
        assertEquals(
                expectedMessage,
                ((McpSchema.TextContent) result.content().getFirst()).text()
        );
    }
}
