
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ReadFileService {

    private static final int MAX_RANGE_LINES = 300;
    private static final int MAX_RANGE_BYTES = 256 * 1024;

    private static final Logger log =
            LoggerFactory.getLogger(ReadFileService.class);

    private final ProjectRegistry projectRegistry;

    public ReadFileService(ProjectRegistry projectRegistry) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );
    }

    public String read(String projectName, Path filePath) throws IOException {
        log.debug(
                "Starting file read: projectName={}, filePath={}",
                projectName,
                filePath
        );

        Path realPath = resolveReadableFile(projectName, filePath);

        String content = Files.readString(realPath);

        log.debug(
                "File read completed: projectName={}, filePath={}",
                projectName,
                realPath
        );

        return content;
    }

    public String readRange(
            String projectName,
            Path filePath,
            int startLine,
            int endLine
    ) throws IOException {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException(
                    "invalid line range: startLine=" + startLine
                            + ", endLine=" + endLine
            );
        }

        if ((long) endLine - startLine + 1 > MAX_RANGE_LINES) {
            throw new IllegalArgumentException(
                    "line range exceeds maximum allowed lines: " + MAX_RANGE_LINES
            );
        }

        log.debug(
                "Starting file range read: projectName={}, filePath={}, startLine={}, endLine={}",
                projectName,
                filePath,
                startLine,
                endLine
        );

        Path realPath = resolveReadableFile(projectName, filePath);

        StringBuilder content = new StringBuilder();

        try (var reader = Files.newBufferedReader(realPath)) {
            int currentLine = 1;
            int character;

            boolean previousCarriageReturn = false;
            boolean lineInitialized = false;
            boolean lineAdded = false;

            while ((character = reader.read()) != -1) {
                if (previousCarriageReturn && character == '\n') {
                    previousCarriageReturn = false;
                    continue;
                }

                previousCarriageReturn = false;

                if (currentLine > endLine) {
                    break;
                }

                boolean lineSeparator =
                        character == '\n' || character == '\r';

                if (currentLine >= startLine) {
                    if (!lineInitialized) {
                        if (lineAdded) {
                            content.append('\n');
                        }

                        lineInitialized = true;
                        lineAdded = true;
                    }

                    if (!lineSeparator) {
                        content.append((char) character);
                    }

                    if (content.length() > MAX_RANGE_BYTES) {
                        throw new IllegalArgumentException(
                                "file range exceeds maximum allowed size: "
                                        + MAX_RANGE_BYTES + " bytes"
                        );
                    }
                }

                if (lineSeparator) {
                    currentLine++;
                    lineInitialized = false;
                    previousCarriageReturn = character == '\r';
                }
            }
        }

        if (content.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RANGE_BYTES) {
            throw new IllegalArgumentException(
                    "file range exceeds maximum allowed size: "
                            + MAX_RANGE_BYTES + " bytes"
            );
        }

        log.debug(
                "File range read completed: projectName={}, filePath={}, startLine={}, endLine={}",
                projectName,
                realPath,
                startLine,
                endLine
        );

        return content.toString();
    }

    private Path resolveReadableFile(String projectName, Path filePath)
            throws IOException {
        Objects.requireNonNull(projectName, "projectName must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        Path rootPath = projectRegistry.getRoot(projectName);

        if (!filePath.startsWith(rootPath)) {
            throw new IllegalArgumentException(
                    "file path is outside registered project root: " + filePath
            );
        }

        Path realPath = filePath.toRealPath();

        if (!realPath.startsWith(rootPath)) {
            throw new IllegalArgumentException(
                    "real file path is outside registered project root: " + realPath
            );
        }

        if (!Files.isRegularFile(realPath)) {
            throw new IllegalArgumentException(
                    "file path must point to a regular file: " + realPath
            );
        }

        if (!Files.isReadable(realPath)) {
            throw new IllegalArgumentException(
                    "file is not readable: " + realPath
            );
        }

        return realPath;
    }
}
