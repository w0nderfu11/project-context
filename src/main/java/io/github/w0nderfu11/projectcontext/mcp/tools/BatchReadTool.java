
package io.github.w0nderfu11.projectcontext.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.w0nderfu11.projectcontext.application.dto.BatchReadResult;
import io.github.w0nderfu11.projectcontext.application.BatchReadService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BatchReadTool {

    private static final Logger log =
            LoggerFactory.getLogger(BatchReadTool.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TOOL_NAME = "read_files";
    private static final String TOOL_TITLE = "Read Files";
    private static final String TOOL_DESCRIPTION =
            "Reads up to 10 text files from a registered project "
                    + "(maximum 256 KiB per file). "
                    + "Returns independent results for each file.";

    private static final String PROJECT_NAME_ARGUMENT = "projectName";
    private static final String FILE_PATHS_ARGUMENT = "filePaths";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    PROJECT_NAME_ARGUMENT, Map.of(
                            "type", "string",
                            "description", "Name of the registered project"
                    ),
                    FILE_PATHS_ARGUMENT, Map.of(
                            "type", "array",
                            "minItems", 1,
                            "maxItems", 10,
                            "uniqueItems", true,
                            "items", Map.of(
                                    "type", "string",
                                    "minLength", 1,
                                    "description", "Absolute path to a file"
                            )
                    )
            ),
            "required", List.of(
                    PROJECT_NAME_ARGUMENT,
                    FILE_PATHS_ARGUMENT
            ),
            "additionalProperties", false
    );

    private static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "results", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "filePath", Map.of(
                                                    "type", "string"
                                            ),
                                            "content", Map.of(
                                                    "type", List.of("string", "null")
                                            ),
                                            "error", Map.of(
                                                    "type", List.of("string", "null")
                                            )
                                    ),
                                    "required", List.of(
                                            "filePath",
                                            "content",
                                            "error"
                                    ),
                                    "additionalProperties", false
                            )
                    )
            ),
            "required", List.of("results"),
            "additionalProperties", false
    );

    private final BatchReadService batchReadService;

    public BatchReadTool(BatchReadService batchReadService) {
        this.batchReadService = Objects.requireNonNull(
                batchReadService,
                "batchReadService must not be null"
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
                        String projectName = projectName(request);
                        List<Path> filePaths = filePaths(request);

                        Map<Path, BatchReadResult> results =
                                batchReadService.read(
                                        projectName,
                                        filePaths
                                );

                        Map<String, Object> output =
                                buildOutput(results);

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(
                                        McpSchema.TextContent.builder(
                                                JSON.valueToTree(output).toString()
                                        ).build()
                                ))
                                .structuredContent(output)
                                .isError(false)
                                .build();

                    } catch (IllegalArgumentException e) {
                        log.debug("Batch read request failed", e);

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

    private static Map<String, Object> buildOutput(
            Map<Path, BatchReadResult> results
    ) {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (Map.Entry<Path, BatchReadResult> entry : results.entrySet()) {
            entries.add(
                    buildResultItem(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return Map.of("results", entries);
    }

    private static Map<String, Object> buildResultItem(
            Path filePath,
            BatchReadResult result
    ) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("filePath", filePath.toString());
        item.put("content", result.content());
        item.put("error", result.error());

        return item;
    }

    private static String projectName(
            McpSchema.CallToolRequest request
    ) {
        Object value = request.arguments().get(
                PROJECT_NAME_ARGUMENT
        );

        if (!(value instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException(
                    "argument must be a non-blank string: projectName"
            );
        }

        return name;
    }

    private static List<Path> filePaths(
            McpSchema.CallToolRequest request
    ) {
        Object value = request.arguments().get(
                FILE_PATHS_ARGUMENT
        );

        if (!(value instanceof List<?> rawPaths)) {
            throw new IllegalArgumentException(
                    "argument must be an array: filePaths"
            );
        }

        List<Path> paths = new ArrayList<>();

        for (Object rawPath : rawPaths) {
            if (!(rawPath instanceof String path) || path.isBlank()) {
                throw new IllegalArgumentException(
                        "argument must contain non-blank paths: filePaths"
                );
            }

            paths.add(Path.of(path));
        }

        return paths;
    }

    private static String safeErrorMessage(
            IllegalArgumentException exception
    ) {
        String message = exception.getMessage();

        if (message != null && (
                message.startsWith("argument must")
                        || message.startsWith("Batch ")
        )) {
            return message;
        }

        return "Invalid batch read request";
    }
}
