package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;

import java.util.List;
import java.util.Objects;

public final class ListProjectsService {

    private final ProjectRegistry projectRegistry;

    public ListProjectsService(ProjectRegistry projectRegistry) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );
    }

    public List<String> list() {
        return projectRegistry.getProjectNames();
    }
}