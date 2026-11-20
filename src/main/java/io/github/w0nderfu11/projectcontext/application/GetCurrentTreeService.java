
package io.github.w0nderfu11.projectcontext.application;

import io.github.w0nderfu11.projectcontext.application.enums.TreeEntryType;
import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathResolver;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GetCurrentTreeService {

    private static final Logger log =
            LoggerFactory.getLogger(GetCurrentTreeService.class);

    private final ProjectPathResolver pathResolver;

    public GetCurrentTreeService(ProjectRegistry projectRegistry) {
        this.pathResolver = new ProjectPathResolver(projectRegistry);
    }

    public Map<String, TreeEntryType> get(
            String projectName,
            Path directoryPath
    ) throws IOException {
        Objects.requireNonNull(
                projectName,
                "projectName must not be null"
        );
        Objects.requireNonNull(
                directoryPath,
                "directoryPath must not be null"
        );

        log.debug(
                "Starting current tree retrieval: projectName={}, directoryPath={}",
                projectName,
                directoryPath
        );

        Path realDirectory = pathResolver.resolve(
                projectName,
                directoryPath
        );

        if (!Files.isDirectory(
                realDirectory,
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw new IllegalArgumentException(
                    "directory path must point to a directory: "
                            + realDirectory
            );
        }

        Map<String, TreeEntryType> entries = new HashMap<>();

        try (DirectoryStream<Path> children =
                     Files.newDirectoryStream(realDirectory)) {

            for (Path entry : children) {
                try {
                    Path realEntry = pathResolver.resolve(
                            projectName,
                            entry
                    );

                    BasicFileAttributes attributes =
                            Files.readAttributes(
                                    realEntry,
                                    BasicFileAttributes.class,
                                    LinkOption.NOFOLLOW_LINKS
                            );

                    TreeEntryType type;

                    if (attributes.isDirectory()) {
                        type = TreeEntryType.DIRECTORY;
                    } else if (attributes.isRegularFile()) {
                        type = TreeEntryType.FILE;
                    } else {
                        continue;
                    }

                    entries.put(
                            realEntry.toString(),
                            type
                    );

                } catch (IOException e) {
                    log.debug(
                            "Skipping unsafe or inaccessible directory entry: filePath={}",
                            entry,
                            e
                    );
                }
            }

        } catch (DirectoryIteratorException e) {
            throw e.getCause();
        }

        log.debug(
                "Current tree retrieval completed: projectName={}, directoryPath={}, entries={}",
                projectName,
                realDirectory,
                entries.size()
        );

        return entries;
    }
}