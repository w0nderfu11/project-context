package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.application.dto.FileMetadata;
import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathResolver;
import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public final class GetFileMetadataService {

    private static final Logger log =
            LoggerFactory.getLogger(GetFileMetadataService.class);

    private final ProjectPathResolver pathResolver;
    private final ProjectAccessPolicy accessPolicy;

    public GetFileMetadataService(ProjectRegistry projectRegistry) {
        this(
                projectRegistry,
                new ProjectAccessPolicy(
                        projectRegistry,
                        PolicyConfiguration.empty()
                )
        );
    }

    public GetFileMetadataService(
            ProjectRegistry projectRegistry,
            ProjectAccessPolicy accessPolicy
    ) {
        this.pathResolver = new ProjectPathResolver(projectRegistry);
        this.accessPolicy = Objects.requireNonNull(
                accessPolicy,
                "accessPolicy must not be null"
        );
    }

    public FileMetadata get(String projectName, Path filePath)
            throws IOException {

        Objects.requireNonNull(
                projectName,
                "projectName must not be null"
        );
        Objects.requireNonNull(
                filePath,
                "filePath must not be null"
        );

        log.debug(
                "Getting file metadata: projectName={}, filePath={}",
                projectName,
                filePath
        );

        Path realPath = pathResolver.resolve(
                projectName,
                filePath
        );

        accessPolicy.checkAccess(projectName, realPath);

        BasicFileAttributes attributes = Files.readAttributes(
                realPath,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );

        if (!attributes.isRegularFile()) {
            throw new IllegalArgumentException(
                    "file path must point to a regular file"
            );
        }

        return new FileMetadata(
                realPath,
                attributes.size(),
                attributes.lastModifiedTime().toInstant()
        );
    }
}
