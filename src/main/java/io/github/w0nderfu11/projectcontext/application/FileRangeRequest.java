
package io.github.w0nderfu11.projectcontext.application;

import java.nio.file.Path;
import java.util.Objects;

public record FileRangeRequest(
        Path filePath,
        int startLine,
        int endLine
) {

    public FileRangeRequest {
        Objects.requireNonNull(
                filePath,
                "filePath must not be null"
        );
    }
}
