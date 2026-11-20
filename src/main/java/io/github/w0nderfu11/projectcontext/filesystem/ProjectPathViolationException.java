package io.github.w0nderfu11.projectcontext.filesystem;

import java.io.IOException;

public final class ProjectPathViolationException extends IOException {

    public ProjectPathViolationException(String message) {
        super(message);
    }
}
