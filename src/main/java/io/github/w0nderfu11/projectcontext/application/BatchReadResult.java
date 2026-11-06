
package io.github.w0nderfu11.projectcontext.application;

import java.util.Objects;

public record BatchReadResult(
        String content,
        String error
) {

    public BatchReadResult {
        if ((content == null) == (error == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of content or error must be present"
            );
        }

        if (error != null && error.isBlank()) {
            throw new IllegalArgumentException(
                    "error must not be blank"
            );
        }
    }

    public static BatchReadResult success(String content) {
        return new BatchReadResult(
                Objects.requireNonNull(content, "content must not be null"),
                null
        );
    }

    public static BatchReadResult failure(String error) {
        return new BatchReadResult(
                null,
                Objects.requireNonNull(error, "error must not be null")
        );
    }
}
