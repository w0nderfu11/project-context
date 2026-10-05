package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.application.ListProjectsService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ListProjectsTool {

    private static final String TOOL_NAME = "list_projects";
    private static final String TOOL_TITLE = "List Projects";
    private static final String TOOL_DESCRIPTION =
            "Lists registered Project Context projects";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false
    );

    private static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "projects", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "string"
                            )
                    )
            ),
            "required", List.of("projects"),
            "additionalProperties", false
    );

    private final ListProjectsService listProjectsService;

    public ListProjectsTool(ListProjectsService listProjectsService) {
        this.listProjectsService = Objects.requireNonNull(
                listProjectsService,
                "listProjectsService must not be null"
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
                    List<String> projects =
                            listProjectsService.list();

                    return McpSchema.CallToolResult.builder()
                            .content(List.of(
                                    McpSchema.TextContent.builder(
                                            projects.toString()
                                    ).build()
                            ))
                            .structuredContent(Map.of(
                                    "projects",
                                    projects
                            ))
                            .isError(false)
                            .build();
                })
                .build();
    }
}