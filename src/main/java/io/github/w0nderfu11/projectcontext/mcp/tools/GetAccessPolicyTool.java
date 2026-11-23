package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GetAccessPolicyTool {

    private static final String TOOL_NAME = "get_access_policy";
    private static final String TOOL_TITLE = "Get Access Policy";
    private static final String TOOL_DESCRIPTION =
            "Returns the currently active global and per-project filesystem access exclusions";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false
    );

    private static final Map<String, Object> STRING_ARRAY_SCHEMA = Map.of(
            "type", "array",
            "items", Map.of("type", "string")
    );

    private static final Map<String, Object> RULES_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "fileNames", STRING_ARRAY_SCHEMA,
                    "extensions", STRING_ARRAY_SCHEMA,
                    "filePaths", STRING_ARRAY_SCHEMA,
                    "directoryPaths", STRING_ARRAY_SCHEMA
            ),
            "required", List.of("fileNames", "extensions", "filePaths", "directoryPaths"),
            "additionalProperties", false
    );

    private static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "global", RULES_SCHEMA,
                    "projects", Map.of(
                            "type", "object",
                            "additionalProperties", RULES_SCHEMA
                    )
            ),
            "required", List.of("global", "projects"),
            "additionalProperties", false
    );

    private final PolicyConfiguration configuration;

    public GetAccessPolicyTool(PolicyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null"
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
                    Map<String, Map<String, List<String>>> projects = new HashMap<>();
                    configuration.projects().forEach((name, rules) ->
                            projects.put(name, ruleValues(rules))
                    );

                    Map<String, Object> policy = Map.of(
                            "global", ruleValues(configuration.global()),
                            "projects", Map.copyOf(projects)
                    );

                    return McpSchema.CallToolResult.builder()
                            .content(List.of(
                                    McpSchema.TextContent.builder(
                                            policy.toString()
                                    ).build()
                            ))
                            .structuredContent(policy)
                            .isError(false)
                            .build();
                })
                .build();
    }

    private static Map<String, List<String>> ruleValues(PolicyRules rules) {
        return Map.of(
                "fileNames", rules.fileNames(),
                "extensions", rules.extensions(),
                "filePaths", rules.filePaths(),
                "directoryPaths", rules.directoryPaths()
        );
    }
}
