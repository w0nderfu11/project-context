
package io.github.w0nderfu11.projectcontext.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BatchReadService {

    private static final int MAX_FILES = 10;

    private final ReadFileService readFileService;

    public BatchReadService(ReadFileService readFileService) {
        this.readFileService = Objects.requireNonNull(readFileService);
    }

    public Map<Path, String> read(
            String projectName,
            List<Path> filePaths
    ) throws IOException {

        Objects.requireNonNull(projectName, "projectName must not be null");
        Objects.requireNonNull(filePaths, "filePaths must not be null");

        if (filePaths.isEmpty() || filePaths.size() > MAX_FILES) {
            throw new IllegalArgumentException(
                    "Batch size must be between 1 and " + MAX_FILES
            );
        }

        Map<Path, String> results = new LinkedHashMap<>();

        for (Path filePath : filePaths) {
            String content = readFileService.read(projectName, filePath);
            results.put(filePath, content);
        }

        return results;
    }
}
