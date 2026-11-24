
package io.github.w0nderfu11.projectcontext.mcp;

import io.github.w0nderfu11.projectcontext.application.*;
import io.github.w0nderfu11.projectcontext.mcp.tools.*;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import io.github.w0nderfu11.projectcontext.server.JettyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectContextMcpServerTest {

    private static final String VERSION_RESOURCE = "/project-context.properties";
    private static final String VERSION_PROPERTY = "project.version";

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndCloseMcpServer() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        assertDoesNotThrow(() ->
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
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                ).close()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullTransport() throws IOException {
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        null,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "transport must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullPingTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        null,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "pingTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullListProjectsTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        null,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "listProjectsTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullReadFileTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        null,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "readFileTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullReadFileRangeTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        null,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "readFileRangeTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullBatchReadTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        null,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "batchReadTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullBatchReadRangeTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        null,
                        getCurrentTreeTool,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "batchReadRangeTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullGetCurrentTreeTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        SearchTool searchTool = searchTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        null,
                        searchTool,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "getCurrentTreeTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("resource")
    void shouldRejectNullSearchTool() throws IOException {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        transport,
                        pingTool,
                        listProjectsTool,
                        readFileTool,
                        readFileRangeTool,
                        batchReadTool,
                        batchReadRangeTool,
                        getCurrentTreeTool,
                        null,
                        getAccessPolicyTool(),
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "searchTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullGetAccessPolicyTool() throws IOException {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        new McpHttpTransport(),
                        new PingTool(new PingService()),
                        listProjectsTool(),
                        readFileTool(),
                        readFileRangeTool(),
                        batchReadTool(),
                        batchReadRangeTool(),
                        getCurrentTreeTool(),
                        searchTool(),
                        null,
                        getFileMetadataTool()
                )
        );

        assertEquals(
                "getAccessPolicyTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullGetFileMetadataTool() throws IOException {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ProjectContextMcpServer(
                        new McpHttpTransport(),
                        new PingTool(new PingService()),
                        listProjectsTool(),
                        readFileTool(),
                        readFileRangeTool(),
                        batchReadTool(),
                        batchReadRangeTool(),
                        getCurrentTreeTool(),
                        searchTool(),
                        getAccessPolicyTool(),
                        null
                )
        );

        assertEquals(
                "getFileMetadataTool must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldInitializeMcpServerOverHttp() throws Exception {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpResponse<String> response = client.send(
                            initializeRequest(endpoint),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains("\"project-context\"")
                    );
                    assertTrue(
                            response.body().contains(
                                    "\"" + projectVersion() + "\""
                            )
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallPingToolOverHttp() throws Exception {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    HttpResponse<String> pingResponse = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    """
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 2,
                                      "method": "tools/call",
                                      "params": {
                                        "name": "ping",
                                        "arguments": {}
                                      }
                                    }
                                    """
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, pingResponse.statusCode());
                    assertTrue(
                            pingResponse.body().contains(
                                    "hello from Project Context"
                            )
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallListProjectsToolOverHttp() throws Exception {
        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    """
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 3,
                                      "method": "tools/call",
                                      "params": {
                                        "name": "list_projects",
                                        "arguments": {}
                                      }
                                    }
                                    """
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains("project")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }


    @Test
    void shouldCallReadFileToolOverHttp() throws Exception {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                """
                public class Example {

                    public void execute() {
                        System.out.println("hello");
                    }
                }
                """
        );

        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 4,
                              "method": "tools/call",
                              "params": {
                                "name": "read_file",
                                "arguments": {
                                  "projectName": "project",
                                  "filePath": "%s"
                                }
                              }
                            }
                            """.formatted(
                            jsonPath(file.toRealPath())
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains(
                                    "public class Example"
                            )
                    );
                    assertTrue(
                            response.body().contains(
                                    "System.out.println"
                            )
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallReadFileRangeToolOverHttp() throws Exception {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird\nfourth"
        );

        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 7,
                              "method": "tools/call",
                              "params": {
                                "name": "read_file_range",
                                "arguments": {
                                  "projectName": "project",
                                  "filePath": "%s",
                                  "startLine": 2,
                                  "endLine": 3
                                }
                              }
                            }
                            """.formatted(
                            jsonPath(file.toRealPath())
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains(
                                    "second\\nthird"
                            )
                    );
                    assertFalse(
                            response.body().contains(
                                    "fourth"
                            )
                    );
                    assertTrue(
                            response.body().contains(
                                    "\"isError\":false"
                            )
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallGetCurrentTreeToolOverHttp() throws Exception {
        Path directory = Files.createDirectory(
                tempDir.resolve("src")
        );

        Path nestedDirectory = Files.createDirectory(
                directory.resolve("main")
        );

        Path file = Files.createFile(
                directory.resolve("Example.java")
        );

        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 5,
                              "method": "tools/call",
                              "params": {
                                "name": "get_current_tree",
                                "arguments": {
                                  "projectName": "project",
                                  "directoryPath": "%s"
                                }
                              }
                            }
                            """.formatted(
                            jsonPath(directory.toRealPath())
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains(
                                    jsonPath(nestedDirectory.toRealPath())
                            )
                    );
                    assertTrue(
                            response.body().contains(
                                    jsonPath(file.toRealPath())
                            )
                    );
                    assertTrue(
                            response.body().contains("DIRECTORY")
                    );
                    assertTrue(
                            response.body().contains("FILE")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallSearchToolOverHttp() throws Exception {
        Path directory = Files.createDirectories(
                tempDir.resolve("src/main/java")
        );

        Path file = Files.createFile(
                directory.resolve("ProjectContextApplication.java")
        );

        McpHttpTransport transport = new McpHttpTransport();
        PingTool pingTool = new PingTool(new PingService());
        ListProjectsTool listProjectsTool = listProjectsTool();
        ReadFileTool readFileTool = readFileTool();
        ReadFileRangeTool readFileRangeTool = readFileRangeTool();
        BatchReadTool batchReadTool = batchReadTool();
        BatchReadRangeTool batchReadRangeTool = batchReadRangeTool();
        GetCurrentTreeTool getCurrentTreeTool = getCurrentTreeTool();
        SearchTool searchTool = searchTool();

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
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 6,
                              "method": "tools/call",
                              "params": {
                                "name": "search",
                                "arguments": {
                                  "projectName": "project",
                                  "fileName": "contextapp",
                                  "extension": "java"
                                }
                              }
                            }
                            """;

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains(
                                    jsonPath(file.toRealPath())
                            )
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldExposeBatchToolsOverHttp() throws Exception {
        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             new PingTool(new PingService()),
                             listProjectsTool(),
                             readFileTool(),
                             readFileRangeTool(),
                             batchReadTool(),
                             batchReadRangeTool(),
                             getCurrentTreeTool(),
                             searchTool(),
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 20,
                              "method": "tools/list",
                              "params": {}
                            }
                            """;

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(response.body().contains("\"get_access_policy\""));
                    assertTrue(response.body().contains("\"get_file_metadata\""));
                    assertTrue(
                            response.body().contains("\"read_files\"")
                    );
                    assertTrue(
                            response.body().contains("\"read_file_ranges\"")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldGetFileMetadataAndRejectExcludedFileOverHttp() throws Exception {
        Path root = tempDir.toRealPath();
        Path file = Files.writeString(
                root.resolve("example.txt"),
                "test"
        );
        Path excluded = Files.createFile(root.resolve("secret.txt"));

        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             new PingTool(new PingService()),
                             listProjectsTool(),
                             readFileTool(),
                             readFileRangeTool(),
                             batchReadTool(),
                             batchReadRangeTool(),
                             getCurrentTreeTool(),
                             searchTool(),
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": %d,
                              "method": "tools/call",
                              "params": {
                                "name": "get_file_metadata",
                                "arguments": {"projectName": "project", "filePath": "%s"}
                              }
                            }
                            """;

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody.formatted(21, jsonPath(file))
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains("\"isError\":false")
                    );
                    assertTrue(
                            response.body().contains("\"filePath\":\"" + jsonPath(file) + "\"")
                    );
                    assertTrue(
                            response.body().contains("\"sizeBytes\":4")
                    );
                    assertTrue(response.body().contains(
                            "\"lastModified\":\"" + Files.getLastModifiedTime(file).toInstant() + "\""
                    ));

                    HttpResponse<String> denied = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody.formatted(22, jsonPath(excluded))
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, denied.statusCode());
                    assertFalse(
                            denied.body().contains("\"structuredContent\"")
                    );
                    assertTrue(
                            denied.body().contains("\"isError\":true")
                    );
                    assertTrue(
                            denied.body().contains("Access denied by PROJECT FILE_NAME rule: secret.txt")
                    );
                    assertFalse(
                            denied.body().contains("\"sizeBytes\"")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallGetAccessPolicyToolOverHttp() throws Exception {
        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             new PingTool(new PingService()),
                             listProjectsTool(),
                             readFileTool(),
                             readFileRangeTool(),
                             batchReadTool(),
                             batchReadRangeTool(),
                             getCurrentTreeTool(),
                             searchTool(),
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 20,
                              "method": "tools/call",
                              "params": {
                                "name": "get_access_policy",
                                "arguments": {}
                              }
                            }
                            """;

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(response.body().contains("\"isError\":false"));
                    assertTrue(response.body().contains("\"structuredContent\":{"));
                    assertTrue(response.body().contains("\"projects\":{\"project\":{"));
                    assertTrue(response.body().contains("\"extensions\":[\".log\"]"));
                    assertTrue(response.body().contains("\"fileNames\":[\"secret.txt\"]"));
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallBatchReadToolOverHttp() throws Exception {
        Path first = Files.writeString(
                tempDir.resolve("First.java"),
                "first batch content"
        );

        Path second = Files.writeString(
                tempDir.resolve("Second.java"),
                "second batch content"
        );

        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             new PingTool(new PingService()),
                             listProjectsTool(),
                             readFileTool(),
                             readFileRangeTool(),
                             batchReadTool(),
                             batchReadRangeTool(),
                             getCurrentTreeTool(),
                             searchTool(),
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 21,
                              "method": "tools/call",
                              "params": {
                                "name": "read_files",
                                "arguments": {
                                  "projectName": "project",
                                  "filePaths": ["%s", "%s"]
                                }
                              }
                            }
                            """.formatted(
                            jsonPath(first.toRealPath()),
                            jsonPath(second.toRealPath())
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains("first batch content")
                    );
                    assertTrue(
                            response.body().contains("second batch content")
                    );
                    assertTrue(
                            response.body().contains("\"isError\":false")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    @Test
    void shouldCallBatchReadRangeToolOverHttp() throws Exception {
        Path file = Files.writeString(
                tempDir.resolve("Example.java"),
                "first\nsecond\nthird\nfourth"
        );

        McpHttpTransport transport = new McpHttpTransport();

        try (ProjectContextMcpServer ignored =
                     new ProjectContextMcpServer(
                             transport,
                             new PingTool(new PingService()),
                             listProjectsTool(),
                             readFileTool(),
                             readFileRangeTool(),
                             batchReadTool(),
                             batchReadRangeTool(),
                             getCurrentTreeTool(),
                             searchTool(),
                             getAccessPolicyTool(),
                             getFileMetadataTool()
                     )) {

            JettyServer httpServer = new JettyServer(
                    "127.0.0.1",
                    0,
                    transport.handler()
            );

            try {
                httpServer.start();

                URI endpoint = endpoint(httpServer);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    String sessionId = initializeSession(
                            client,
                            endpoint
                    );

                    String requestBody = """
                            {
                              "jsonrpc": "2.0",
                              "id": 22,
                              "method": "tools/call",
                              "params": {
                                "name": "read_file_ranges",
                                "arguments": {
                                  "projectName": "project",
                                  "ranges": [
                                    {
                                      "filePath": "%s",
                                      "startLine": 1,
                                      "endLine": 2
                                    },
                                    {
                                      "filePath": "%s",
                                      "startLine": 3,
                                      "endLine": 4
                                    }
                                  ]
                                }
                              }
                            }
                            """.formatted(
                            jsonPath(file.toRealPath()),
                            jsonPath(file.toRealPath())
                    );

                    HttpResponse<String> response = client.send(
                            toolRequest(
                                    endpoint,
                                    sessionId,
                                    requestBody
                            ),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(200, response.statusCode());
                    assertTrue(
                            response.body().contains("first\\nsecond")
                    );
                    assertTrue(
                            response.body().contains("third\\nfourth")
                    );
                    assertTrue(
                            response.body().contains("\"isError\":false")
                    );
                }
            } finally {
                httpServer.stop();
            }
        }
    }

    private GetFileMetadataTool getFileMetadataTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir.toRealPath())
        );
        ProjectAccessPolicy accessPolicy = new ProjectAccessPolicy(
                registry,
                policyConfiguration()
        );
        GetFileMetadataService service = new GetFileMetadataService(
                registry,
                accessPolicy
        );

        return new GetFileMetadataTool(service);
    }

    private GetAccessPolicyTool getAccessPolicyTool() {
        return new GetAccessPolicyTool(policyConfiguration());
    }

    private PolicyConfiguration policyConfiguration() {
        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(".log"),
                List.of(),
                List.of()
        );
        PolicyRules project = new PolicyRules(
                List.of("secret.txt"),
                List.of(),
                List.of(),
                List.of()
        );

        return new PolicyConfiguration(
                global,
                Map.of("project", project)
        );
    }

    private ListProjectsTool listProjectsTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "project",
                        tempDir
                )
        );

        ListProjectsService listProjectsService =
                new ListProjectsService(registry);

        return new ListProjectsTool(listProjectsService);
    }

    private ReadFileTool readFileTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "project",
                        tempDir
                )
        );

        ReadFileService readFileService =
                new ReadFileService(registry);

        return new ReadFileTool(readFileService);
    }

    private ReadFileRangeTool readFileRangeTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "project",
                        tempDir
                )
        );

        ReadFileService readFileService =
                new ReadFileService(registry);

        return new ReadFileRangeTool(readFileService);
    }

    private BatchReadService batchReadService() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of("project", tempDir)
        );

        return new BatchReadService(
                new ReadFileService(registry)
        );
    }

    private BatchReadTool batchReadTool() throws IOException {
        return new BatchReadTool(batchReadService());
    }

    private BatchReadRangeTool batchReadRangeTool() throws IOException {
        return new BatchReadRangeTool(batchReadService());
    }

    private GetCurrentTreeTool getCurrentTreeTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "project",
                        tempDir
                )
        );

        GetCurrentTreeService getCurrentTreeService =
                new GetCurrentTreeService(registry);

        return new GetCurrentTreeTool(getCurrentTreeService);
    }

    private SearchTool searchTool() throws IOException {
        ProjectRegistry registry = new ProjectRegistry(
                Map.of(
                        "project",
                        tempDir
                )
        );

        SearchService searchService =
                new SearchService(registry);

        return new SearchTool(searchService);
    }

    private static String initializeSession(
            HttpClient client,
            URI endpoint
    ) throws Exception {
        HttpResponse<String> initializeResponse = client.send(
                initializeRequest(endpoint),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, initializeResponse.statusCode());

        String sessionId = initializeResponse.headers()
                .firstValue("Mcp-Session-Id")
                .orElseThrow();

        HttpResponse<String> initializedResponse = client.send(
                initializedRequest(
                        endpoint,
                        sessionId
                ),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(202, initializedResponse.statusCode());

        return sessionId;
    }

    private static HttpRequest initializeRequest(URI endpoint) {
        return HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .header(
                        "Accept",
                        "application/json, text/event-stream"
                )
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2025-11-25",
                            "capabilities": {},
                            "clientInfo": {
                              "name": "project-context-test",
                              "version": "1.0"
                            }
                          }
                        }
                        """))
                .build();
    }

    private static HttpRequest initializedRequest(
            URI endpoint,
            String sessionId
    ) {
        //noinspection UastIncorrectHttpHeaderInspection
        return HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .header(
                        "Accept",
                        "application/json, text/event-stream"
                )
                .header("Mcp-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "jsonrpc": "2.0",
                          "method": "notifications/initialized"
                        }
                        """))
                .build();
    }

    private static HttpRequest toolRequest(
            URI endpoint,
            String sessionId,
            String body
    ) {
        //noinspection UastIncorrectHttpHeaderInspection
        return HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .header(
                        "Accept",
                        "application/json, text/event-stream"
                )
                .header("Mcp-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static URI endpoint(JettyServer server) {
        return URI.create(
                "http://127.0.0.1:"
                        + server.localPort()
                        + McpEndpoint.MCP
        );
    }

    private static String jsonPath(Path path) {
        return path.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String projectVersion() {
        Properties properties = new Properties();

        try (InputStream input =
                     ProjectContextMcpServerTest.class
                             .getResourceAsStream(VERSION_RESOURCE)) {

            if (input == null) {
                throw new IllegalStateException(
                        "Project version resource not found: "
                                + VERSION_RESOURCE
                );
            }

            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load project version",
                    e
            );
        }

        String version = properties.getProperty(VERSION_PROPERTY);

        if (version == null || version.isBlank()) {
            throw new IllegalStateException(
                    "Project version is missing or blank"
            );
        }

        return version;
    }
}