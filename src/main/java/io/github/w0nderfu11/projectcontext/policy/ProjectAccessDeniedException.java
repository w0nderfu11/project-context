package io.github.w0nderfu11.projectcontext.policy;

import java.io.IOException;

public final class ProjectAccessDeniedException extends IOException {

    public ProjectAccessDeniedException(String message) {
        super(message);
    }
}
