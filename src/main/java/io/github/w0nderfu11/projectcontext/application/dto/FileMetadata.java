package io.github.w0nderfu11.projectcontext.application.dto;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record FileMetadata(
        Path filePath,
        long sizeBytes,
        Instant lastModified
) {

    public FileMetadata {
        Objects.requireNonNull(
                filePath,
                "filePath must not be null"
        );
        Objects.requireNonNull(
                lastModified,
                "lastModified must not be null"
        );

        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must not be negative"
            );
        }
    }
}
