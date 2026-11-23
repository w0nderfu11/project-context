
package io.github.w0nderfu11.projectcontext;

import io.github.w0nderfu11.projectcontext.application.BatchReadService;
import io.github.w0nderfu11.projectcontext.application.GetCurrentTreeService;
import io.github.w0nderfu11.projectcontext.application.ListProjectsService;
import io.github.w0nderfu11.projectcontext.application.PingService;
import io.github.w0nderfu11.projectcontext.application.ReadFileService;
import io.github.w0nderfu11.projectcontext.application.SearchService;
import io.github.w0nderfu11.projectcontext.mcp.McpHttpTransport;
import io.github.w0nderfu11.projectcontext.mcp.ProjectContextMcpServer;
import io.github.w0nderfu11.projectcontext.mcp.tools.BatchReadRangeTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.BatchReadTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.GetAccessPolicyTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.GetCurrentTreeTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.ListProjectsTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.PingTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.ReadFileRangeTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.ReadFileTool;
import io.github.w0nderfu11.projectcontext.mcp.tools.SearchTool;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfigurationStore;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import io.github.w0nderfu11.projectcontext.server.JettyServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class ProjectContextApplication {

    public static void main(String[] args) throws Exception {
        ProjectRegistry projectRegistry = new ProjectRegistry(
                Map.of(
                        "project-context",
                        Path.of("D:\\project-context")
                )
        );

        PolicyConfiguration configuration =
                loadPolicyConfiguration(projectRegistry);

        ProjectAccessPolicy accessPolicy = new ProjectAccessPolicy(
                projectRegistry,
                configuration
        );

        PingService pingService = new PingService();

        ListProjectsService listProjectsService =
                new ListProjectsService(projectRegistry);

        ReadFileService readFileService =
                new ReadFileService(
                        projectRegistry,
                        accessPolicy
                );

        BatchReadService batchReadService =
                new BatchReadService(readFileService);

        GetCurrentTreeService getCurrentTreeService =
                new GetCurrentTreeService(
                        projectRegistry,
                        accessPolicy
                );

        SearchService searchService =
                new SearchService(
                        projectRegistry,
                        accessPolicy
                );

        PingTool pingTool = new PingTool(pingService);

        ListProjectsTool listProjectsTool =
                new ListProjectsTool(listProjectsService);

        ReadFileTool readFileTool =
                new ReadFileTool(readFileService);

        ReadFileRangeTool readFileRangeTool =
                new ReadFileRangeTool(readFileService);

        BatchReadTool batchReadTool =
                new BatchReadTool(batchReadService);

        BatchReadRangeTool batchReadRangeTool =
                new BatchReadRangeTool(batchReadService);

        GetCurrentTreeTool getCurrentTreeTool =
                new GetCurrentTreeTool(getCurrentTreeService);

        SearchTool searchTool =
                new SearchTool(searchService);

        GetAccessPolicyTool getAccessPolicyTool =
                new GetAccessPolicyTool(configuration);

        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             pingTool,
                             listProjectsTool,
                             readFileTool,
                             readFileRangeTool,
                             batchReadTool,
                             batchReadRangeTool,
                             getCurrentTreeTool,
                             searchTool,
                             getAccessPolicyTool
                     )) {

            JettyServer server = new JettyServer(
                    "127.0.0.1",
                    8765,
                    transport.handler()
            );

            server.start();
            server.join();
        }
    }

    static PolicyConfiguration loadPolicyConfiguration(
            ProjectRegistry projectRegistry
    ) throws IOException {
        Path configurationPath = projectRegistry.getRoot("project-context")
                .resolve("config/policy.json");

        PolicyConfigurationStore store = new PolicyConfigurationStore(
                projectRegistry,
                configurationPath
        );

        return store.load();
    }
}
