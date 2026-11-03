
package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.ReadFileService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReadFileRangeTool {

    private static final Logger log =
            LoggerFactory.getLogger(ReadFileRangeTool.class);

    private static final String TOOL_NAME = "read_file_range";
    private static final String TOOL_TITLE = "Read File Range";
    private static final String TOOL_DESCRIPTION =
            "Reads a specific line range from a text file in a registered Project Context project (maximum 300 lines and 256 KiB)";

    private static final String PROJECT_NAME_ARGUMENT = "projectName";
    private static final String FILE_PATH_ARGUMENT = "filePath";
    private static final String START_LINE_ARGUMENT = "startLine";
    private static final String END_LINE_ARGUMENT = "endLine";

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
                    ),
                    START_LINE_ARGUMENT, Map.of(
                            "type", "integer",
                            "minimum", 1,
                            "description", "First line to read (1-based, inclusive)"
                    ),
                    END_LINE_ARGUMENT, Map.of(
                            "type", "integer",
                            "minimum", 1,
                            "description", "Last line to read (1-based, inclusive, maximum 300 lines per request)"
                    )
            ),
            "required", List.of(
                    PROJECT_NAME_ARGUMENT,
                    FILE_PATH_ARGUMENT,
                    START_LINE_ARGUMENT,
                    END_LINE_ARGUMENT
            ),
            "additionalProperties", false
    );

    private static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "content", Map.of(
                            "type", "string"
                    )
            ),
            "required", List.of("content"),
            "additionalProperties", false
    );

    private final ReadFileService readFileService;

    public ReadFileRangeTool(ReadFileService readFileService) {
        this.readFileService = Objects.requireNonNull(
                readFileService,
                "readFileService must not be null"
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

                        int startLine = integerArgument(
                                request,
                                START_LINE_ARGUMENT
                        );

                        int endLine = integerArgument(
                                request,
                                END_LINE_ARGUMENT
                        );

                        String content = readFileService.readRange(
                                projectName,
                                Path.of(filePath),
                                startLine,
                                endLine
                        );

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(
                                        McpSchema.TextContent.builder(content)
                                                .build()
                                ))
                                .structuredContent(Map.of(
                                        "content", content
                                ))
                                .isError(false)
                                .build();
                    } catch (IllegalArgumentException | IOException e) {
                        log.debug(
                                "File range read failed",
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
        Object value = request.arguments().get(name);

        if (!(value instanceof String stringValue)
                || stringValue.isBlank()) {
            throw new IllegalArgumentException(
                    "argument must be a non-blank string: " + name
            );
        }

        return stringValue;
    }

    private static int integerArgument(
            McpSchema.CallToolRequest request,
            String name
    ) {
        Object value = request.arguments().get(name);

        if (value instanceof Integer integerValue) {
            return integerValue;
        }

        if (value instanceof Long longValue
                && longValue >= Integer.MIN_VALUE
                && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }

        throw new IllegalArgumentException(
                "argument must be a 32-bit integer: " + name
        );
    }

    private static String safeErrorMessage(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            String message = exception.getMessage();

            if (message != null && (
                    message.startsWith("argument must be")
                            || message.startsWith("invalid line range:")
                            || message.startsWith("line range exceeds maximum")
                            || message.startsWith("file range exceeds maximum")
            )) {
                return message;
            }
        }

        return "Unable to read the requested file range";
    }
}
