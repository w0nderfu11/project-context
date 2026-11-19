
package io.github.w0nderfu11.projectcontext.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.w0nderfu11.projectcontext.application.BatchReadResult;
import io.github.w0nderfu11.projectcontext.application.BatchReadService;
import io.github.w0nderfu11.projectcontext.application.FileRangeRequest;
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

public final class BatchReadRangeTool {

    private static final Logger log =
            LoggerFactory.getLogger(BatchReadRangeTool.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TOOL_NAME = "read_file_ranges";
    private static final String TOOL_TITLE = "Read File Ranges";
    private static final String TOOL_DESCRIPTION =
            "Reads up to 10 text file ranges from a registered project "
                    + "(maximum 300 lines and 256 KiB per range). "
                    + "Returns independent results for each range.";

    private static final String PROJECT_NAME_ARGUMENT = "projectName";
    private static final String RANGES_ARGUMENT = "ranges";

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
                    RANGES_ARGUMENT, Map.of(
                            "type", "array",
                            "minItems", 1,
                            "maxItems", 10,
                            "uniqueItems", true,
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            FILE_PATH_ARGUMENT, Map.of(
                                                    "type", "string",
                                                    "minLength", 1,
                                                    "description", "Absolute path to the file"
                                            ),
                                            START_LINE_ARGUMENT, Map.of(
                                                    "type", "integer",
                                                    "description", "First line (1-based, inclusive)"
                                            ),
                                            END_LINE_ARGUMENT, Map.of(
                                                    "type", "integer",
                                                    "description", "Last line (1-based, inclusive)"
                                            )
                                    ),
                                    "required", List.of(
                                            FILE_PATH_ARGUMENT,
                                            START_LINE_ARGUMENT,
                                            END_LINE_ARGUMENT
                                    ),
                                    "additionalProperties", false
                            )
                    )
            ),
            "required", List.of(
                    PROJECT_NAME_ARGUMENT,
                    RANGES_ARGUMENT
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
                                            "startLine", Map.of(
                                                    "type", "integer"
                                            ),
                                            "endLine", Map.of(
                                                    "type", "integer"
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
                                            "startLine",
                                            "endLine",
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

    public BatchReadRangeTool(BatchReadService batchReadService) {
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
                        List<FileRangeRequest> ranges = ranges(request);

                        Map<FileRangeRequest, BatchReadResult> results =
                                batchReadService.readRanges(
                                        projectName,
                                        ranges
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
                        log.debug("Batch range read request failed", e);

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
            Map<FileRangeRequest, BatchReadResult> results
    ) {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (Map.Entry<FileRangeRequest, BatchReadResult> entry
                : results.entrySet()) {

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
            FileRangeRequest request,
            BatchReadResult result
    ) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("filePath", request.filePath().toString());
        item.put("startLine", request.startLine());
        item.put("endLine", request.endLine());
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

    private static List<FileRangeRequest> ranges(
            McpSchema.CallToolRequest request
    ) {
        Object value = request.arguments().get(
                RANGES_ARGUMENT
        );

        if (!(value instanceof List<?> rawRanges)) {
            throw new IllegalArgumentException(
                    "argument must be an array: ranges"
            );
        }

        List<FileRangeRequest> requests = new ArrayList<>();

        for (Object rawRange : rawRanges) {
            requests.add(parseRange(rawRange));
        }

        return requests;
    }

    private static FileRangeRequest parseRange(Object rawRange) {
        if (!(rawRange instanceof Map<?, ?> range)) {
            throw new IllegalArgumentException(
                    "argument must contain range objects: ranges"
            );
        }

        Object rawPath = range.get(FILE_PATH_ARGUMENT);

        if (!(rawPath instanceof String filePath)
                || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "argument must contain a non-blank filePath"
            );
        }

        int startLine = integerArgument(
                range,
                START_LINE_ARGUMENT
        );

        int endLine = integerArgument(
                range,
                END_LINE_ARGUMENT
        );

        return new FileRangeRequest(
                Path.of(filePath),
                startLine,
                endLine
        );
    }

    private static int integerArgument(
            Map<?, ?> range,
            String name
    ) {
        Object value = range.get(name);

        if (!(value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(
                    "argument must be a 32-bit integer: " + name
            );
        }

        long number = ((Number) value).longValue();

        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "argument must be a 32-bit integer: " + name
            );
        }

        return (int) number;
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

        return "Invalid batch range read request";
    }
}
