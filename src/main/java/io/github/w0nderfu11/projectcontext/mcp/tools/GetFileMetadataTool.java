package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.GetFileMetadataService;
import io.github.w0nderfu11.projectcontext.application.dto.FileMetadata;
import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathViolationException;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessDeniedException;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GetFileMetadataTool {

    private static final Logger log =
            LoggerFactory.getLogger(GetFileMetadataTool.class);

    private static final String TOOL_NAME = "get_file_metadata";
    private static final String TOOL_TITLE = "Get File Metadata";
    private static final String TOOL_DESCRIPTION =
            "Returns the canonical path, size in bytes, and last modification time of a registered project file";

    private static final String PROJECT_NAME_ARGUMENT = "projectName";
    private static final String FILE_PATH_ARGUMENT = "filePath";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    PROJECT_NAME_ARGUMENT, Map.of(
                            "type", "string",
                            "description", "Name of the registered project"
                    ),
                    FILE_PATH_ARGUMENT, Map.of(
                            "type", "string",
                            "description", "Full path to the file"
                    )
            ),
            "required", List.of(
                    PROJECT_NAME_ARGUMENT,
                    FILE_PATH_ARGUMENT
            ),
            "additionalProperties", false
    );

    private static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "filePath", Map.of(
                            "type", "string"
                    ),
                    "sizeBytes", Map.of(
                            "type", "integer",
                            "minimum", 0
                    ),
                    "lastModified", Map.of(
                            "type", "string",
                            "format", "date-time"
                    )
            ),
            "required", List.of("filePath", "sizeBytes", "lastModified"),
            "additionalProperties", false
    );

    private final GetFileMetadataService getFileMetadataService;

    public GetFileMetadataTool(GetFileMetadataService getFileMetadataService) {
        this.getFileMetadataService = Objects.requireNonNull(
                getFileMetadataService,
                "getFileMetadataService must not be null"
        );
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        McpSchema.Tool tool = McpSchema.Tool.builder(
                        TOOL_NAME,
                        INPUT_SCHEMA
                )
                .title(TOOL_TITLE)
                .description(TOOL_DESCRIPTION)
                .outputSchema(OUTPUT_SCHEMA)
                .annotations(
                        McpSchema.ToolAnnotations.builder()
                                .readOnlyHint(true)
                                .destructiveHint(false)
                                .openWorldHint(false)
                                .idempotentHint(true)
                                .build()
                )
                .meta(Map.of(
                        "openai/visibility", "public"
                ))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        String projectName = argument(
                                request,
                                PROJECT_NAME_ARGUMENT
                        );

                        String filePath = argument(
                                request,
                                FILE_PATH_ARGUMENT
                        );

                        FileMetadata metadata = getFileMetadataService.get(
                                projectName,
                                Path.of(filePath)
                        );

                        Map<String, Object> result = Map.of(
                                "filePath", metadata.filePath().toString(),
                                "sizeBytes", metadata.sizeBytes(),
                                "lastModified", metadata.lastModified().toString()
                        );

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(
                                        McpSchema.TextContent.builder(result.toString())
                                                .build()
                                ))
                                .structuredContent(result)
                                .isError(false)
                                .build();
                    } catch (IllegalArgumentException | IOException e) {
                        log.debug(
                                "File metadata retrieval failed",
                                e
                        );

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(
                                        McpSchema.TextContent.builder(
                                                safeErrorMessage(e)
                                        ).build()
                                ))
                                .isError(true)
                                .build();
                    }
                })
                .build();
    }

    private static String argument(
            McpSchema.CallToolRequest request,
            String name
    ) {
        Object value = request.arguments() == null
                ? null
                : request.arguments().get(name);

        if (!(value instanceof String stringValue)
                || stringValue.isBlank()) {
            throw new IllegalArgumentException(
                    "argument must be a non-blank string: " + name
            );
        }

        return stringValue;
    }

    private static String safeErrorMessage(Exception exception) {
        if (exception instanceof ProjectAccessDeniedException
                || exception instanceof ProjectPathViolationException) {
            return exception.getMessage();
        }

        if (exception instanceof IllegalArgumentException) {
            String message = exception.getMessage();

            if (message != null && (
                    message.startsWith("argument must be a non-blank string:")
                            || message.equals("requestedPath must be absolute")
                            || message.equals("requestedPath must not contain '..' segments")
                            || message.equals("file path must point to a regular file")
            )) {
                return message;
            }

            if (message != null && message.startsWith("project is not registered:")) {
                return "project is not registered";
            }
        }

        return "Unable to get the requested file metadata";
    }
}
