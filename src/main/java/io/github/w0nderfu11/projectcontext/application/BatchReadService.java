
package io.github.w0nderfu11.projectcontext.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BatchReadService {

    private static final int MAX_FILES = 10;

    private static final Logger log =
            LoggerFactory.getLogger(BatchReadService.class);

    private final ReadFileService readFileService;

    public BatchReadService(ReadFileService readFileService) {
        this.readFileService = Objects.requireNonNull(
                readFileService,
                "readFileService must not be null"
        );
    }

    public Map<Path, BatchReadResult> read(
            String projectName,
            List<Path> filePaths
    ) {
        validateRequest(projectName, filePaths);

        log.debug(
                "Starting batch file read: projectName={}, files={}",
                projectName,
                filePaths.size()
        );

        Map<Path, BatchReadResult> results = new LinkedHashMap<>();

        for (Path filePath : filePaths) {
            try {
                String content = readFileService.read(
                        projectName,
                        filePath
                );

                results.put(
                        filePath,
                        BatchReadResult.success(content)
                );

            } catch (IOException | IllegalArgumentException e) {
                log.debug(
                        "Batch file read failed: projectName={}, filePath={}",
                        projectName,
                        filePath,
                        e
                );

                results.put(
                        filePath,
                        BatchReadResult.failure(safeErrorMessage(e))
                );
            }
        }

        log.debug(
                "Batch file read completed: projectName={}, files={}",
                projectName,
                results.size()
        );

        return results;
    }

    public Map<FileRangeRequest, BatchReadResult> readRanges(
            String projectName,
            List<FileRangeRequest> requests
    ) {
        validateRequest(projectName, requests);

        log.debug(
                "Starting batch range read: projectName={}, ranges={}",
                projectName,
                requests.size()
        );

        Map<FileRangeRequest, BatchReadResult> results =
                new LinkedHashMap<>();

        for (FileRangeRequest request : requests) {
            try {
                String content = readFileService.readRange(
                        projectName,
                        request.filePath(),
                        request.startLine(),
                        request.endLine()
                );

                results.put(
                        request,
                        BatchReadResult.success(content)
                );

            } catch (IOException | IllegalArgumentException e) {
                log.debug(
                        "Batch range read failed: projectName={}, request={}",
                        projectName,
                        request,
                        e
                );

                results.put(
                        request,
                        BatchReadResult.failure(safeErrorMessage(e))
                );
            }
        }

        log.debug(
                "Batch range read completed: projectName={}, ranges={}",
                projectName,
                results.size()
        );

        return results;
    }

    private <T> void validateRequest(
            String projectName,
            List<T> requests
    ) {
        Objects.requireNonNull(
                projectName,
                "projectName must not be null"
        );

        Objects.requireNonNull(
                requests,
                "requests must not be null"
        );

        if (requests.isEmpty() || requests.size() > MAX_FILES) {
            throw new IllegalArgumentException(
                    "Batch size must be between 1 and " + MAX_FILES
            );
        }

        Set<T> uniqueRequests = new HashSet<>();

        for (T request : requests) {
            if (request == null) {
                throw new IllegalArgumentException(
                        "Batch must not contain null entries"
                );
            }

            if (!uniqueRequests.add(request)) {
                throw new IllegalArgumentException(
                        "Batch must not contain duplicate entries"
                );
            }
        }

        readFileService.validateProject(projectName);
    }

    private static String safeErrorMessage(Exception exception) {
        if (exception instanceof NoSuchFileException) {
            return "File not found";
        }

        if (exception instanceof IllegalArgumentException) {
            String message = exception.getMessage();

            if (message != null && (
                    message.startsWith("invalid line range:")
                            || message.startsWith("line range exceeds maximum")
                            || message.startsWith("file range exceeds maximum")
                            || message.startsWith("file exceeds maximum")
            )) {
                return message;
            }
        }

        return "Unable to read the requested file";
    }
}
