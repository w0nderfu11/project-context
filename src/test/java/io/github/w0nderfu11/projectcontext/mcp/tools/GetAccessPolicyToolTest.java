package io.github.w0nderfu11.projectcontext.mcp.tools;

import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetAccessPolicyToolTest {

    @Test
    void shouldCreateGetAccessPolicyToolSpecification() {
        GetAccessPolicyTool getAccessPolicyTool = new GetAccessPolicyTool(PolicyConfiguration.empty());

        McpServerFeatures.SyncToolSpecification specification =
                getAccessPolicyTool.specification();

        assertEquals("get_access_policy", specification.tool().name());
        assertEquals("Get Access Policy", specification.tool().title());
        assertEquals(
                "Returns the currently active global and per-project filesystem access exclusions",
                specification.tool().description()
        );
    }

    @Test
    void shouldExposeToolAnnotations() {
        GetAccessPolicyTool getAccessPolicyTool = new GetAccessPolicyTool(PolicyConfiguration.empty());

        McpSchema.ToolAnnotations annotations =
                getAccessPolicyTool.specification().tool().annotations();

        assertTrue(annotations.readOnlyHint());
        assertFalse(annotations.destructiveHint());
        assertFalse(annotations.openWorldHint());
        assertTrue(annotations.idempotentHint());
    }

    @Test
    void shouldExposeInputSchema() {
        GetAccessPolicyTool getAccessPolicyTool = new GetAccessPolicyTool(PolicyConfiguration.empty());

        Map<String, Object> inputSchema =
                getAccessPolicyTool.specification().tool().inputSchema();

        assertEquals("object", inputSchema.get("type"));
        assertEquals(Map.of(), inputSchema.get("properties"));
        assertEquals(false, inputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposeOutputSchema() {
        GetAccessPolicyTool getAccessPolicyTool = new GetAccessPolicyTool(PolicyConfiguration.empty());

        Map<String, Object> outputSchema =
                getAccessPolicyTool.specification().tool().outputSchema();

        Map<String, Object> strings = Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
        Map<String, Object> rules = Map.of(
                "type", "object",
                "properties", Map.of(
                        "fileNames", strings,
                        "extensions", strings,
                        "filePaths", strings,
                        "directoryPaths", strings
                ),
                "required", List.of("fileNames", "extensions", "filePaths", "directoryPaths"),
                "additionalProperties", false
        );

        assertEquals("object", outputSchema.get("type"));
        assertEquals(
                Map.of(
                        "global", rules,
                        "projects", Map.of(
                                "type", "object",
                                "additionalProperties", rules
                        )
                ),
                outputSchema.get("properties")
        );
        assertEquals(List.of("global", "projects"), outputSchema.get("required"));
        assertEquals(false, outputSchema.get("additionalProperties"));
    }

    @Test
    void shouldExposePublicVisibility() {
        GetAccessPolicyTool getAccessPolicyTool = new GetAccessPolicyTool(PolicyConfiguration.empty());

        Map<String, Object> meta =
                getAccessPolicyTool.specification().tool().meta();

        assertEquals(
                "public",
                meta.get("openai/visibility")
        );
    }

    @Test
    void shouldRejectNullConfiguration() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new GetAccessPolicyTool(null)
        );

        assertEquals(
                "configuration must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnEmptyConfiguration() {
        GetAccessPolicyTool tool = new GetAccessPolicyTool(PolicyConfiguration.empty());
        McpSchema.CallToolResult result = call(tool);

        assertFalse(result.isError());
        assertEquals(
                Map.of(
                        "global", Map.of(
                                "fileNames", List.of(),
                                "extensions", List.of(),
                                "filePaths", List.of(),
                                "directoryPaths", List.of()
                        ),
                        "projects", Map.of()
                ),
                result.structuredContent()
        );
    }

    @Test
    void shouldReturnNormalizedGlobalAndSeparateProjectRules() {
        PolicyRules global = new PolicyRules(
                List.of(".env"),
                List.of(".log"),
                List.of("./config/local.json"),
                List.of("private/")
        );
        PolicyRules first = new PolicyRules(
                List.of("secret.txt"),
                List.of(),
                List.of(),
                List.of()
        );
        PolicyRules second = new PolicyRules(
                List.of(),
                List.of(".tmp"),
                List.of(),
                List.of()
        );
        GetAccessPolicyTool tool = new GetAccessPolicyTool(
                new PolicyConfiguration(
                        global,
                        Map.of("first", first, "second", second)
                )
        );

        assertEquals(
                Map.of(
                        "global", Map.of(
                                "fileNames", List.of(".env"),
                                "extensions", List.of(".log"),
                                "filePaths", List.of("config/local.json"),
                                "directoryPaths", List.of("private")
                        ),
                        "projects", Map.of(
                                "first", Map.of(
                                        "fileNames", List.of("secret.txt"),
                                        "extensions", List.of(),
                                        "filePaths", List.of(),
                                        "directoryPaths", List.of()
                                ),
                                "second", Map.of(
                                        "fileNames", List.of(),
                                        "extensions", List.of(".tmp"),
                                        "filePaths", List.of(),
                                        "directoryPaths", List.of()
                                )
                        )
                ),
                call(tool).structuredContent()
        );
    }

    @Test
    void shouldKeepOriginalConfigurationSnapshot() {
        GetAccessPolicyTool original = new GetAccessPolicyTool(PolicyConfiguration.empty());
        Object snapshot = call(original).structuredContent();

        GetAccessPolicyTool updated = new GetAccessPolicyTool(
                new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of("project", PolicyRules.empty())
                )
        );

        assertNotEquals(snapshot, call(updated).structuredContent());
        assertEquals(snapshot, call(original).structuredContent());
    }

    private McpSchema.CallToolResult call(GetAccessPolicyTool tool) {
        McpSchema.CallToolRequest request =
                McpSchema.CallToolRequest.builder("get_access_policy")
                        .arguments(Map.of())
                        .build();

        return tool.specification()
                .callHandler()
                .apply(null, request);
    }
}
