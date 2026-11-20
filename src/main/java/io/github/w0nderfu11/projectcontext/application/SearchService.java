
package io.github.w0nderfu11.projectcontext.application;

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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class SearchService {

    private static final Logger log =
            LoggerFactory.getLogger(SearchService.class);

    private final ProjectPathResolver pathResolver;

    public SearchService(ProjectRegistry projectRegistry) {
        this.pathResolver = new ProjectPathResolver(projectRegistry);
    }

    public List<String> search(
            String projectName,
            String fileName,
            String extension,
            Path directoryPath
    ) throws IOException {
        Objects.requireNonNull(
                projectName,
                "projectName must not be null"
        );
        Objects.requireNonNull(
                fileName,
                "fileName must not be null"
        );
        Objects.requireNonNull(
                extension,
                "extension must not be null"
        );

        if (fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "fileName must not be blank"
            );
        }

        if (extension.isBlank()) {
            throw new IllegalArgumentException(
                    "extension must not be blank"
            );
        }

        log.debug(
                "Starting file search: projectName={}, fileName={}, extension={}, directoryPath={}",
                projectName,
                fileName,
                extension,
                directoryPath
        );

        Path requestedDirectory = directoryPath != null
                ? directoryPath
                : pathResolver.getRoot(projectName);

        Path searchPath = pathResolver.resolve(
                projectName,
                requestedDirectory
        );

        if (!Files.isDirectory(
                searchPath,
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw new IllegalArgumentException(
                    "directory path must point to a directory: "
                            + searchPath
            );
        }

        String normalizedFileName =
                fileName.toLowerCase(Locale.ROOT);

        String normalizedExtension =
                extension.toLowerCase(Locale.ROOT);

        Set<Path> matches = new HashSet<>();

        Deque<Path> pendingDirectories = new ArrayDeque<>();
        pendingDirectories.push(searchPath);

        while (!pendingDirectories.isEmpty()) {
            Path directory = pendingDirectories.pop();

            try {
                Path realDirectory = pathResolver.resolve(
                        projectName,
                        directory
                );

                if (!Files.isDirectory(
                        realDirectory,
                        LinkOption.NOFOLLOW_LINKS
                )) {
                    if (directory.equals(searchPath)) {
                        throw new IllegalArgumentException(
                                "directory path must point to a directory: "
                                        + realDirectory
                        );
                    }

                    continue;
                }

                try (DirectoryStream<Path> entries =
                             Files.newDirectoryStream(realDirectory)) {

                    for (Path entry : entries) {
                        try {
                            Path realPath = pathResolver.resolve(
                                    projectName,
                                    entry
                            );

                            if (Files.isDirectory(
                                    realPath,
                                    LinkOption.NOFOLLOW_LINKS
                            )) {
                                pendingDirectories.push(realPath);
                                continue;
                            }

                            if (!Files.isRegularFile(
                                    realPath,
                                    LinkOption.NOFOLLOW_LINKS
                            )) {
                                continue;
                            }

                            String name = realPath.getFileName()
                                    .toString()
                                    .toLowerCase(Locale.ROOT);

                            if (!name.endsWith(
                                    "." + normalizedExtension
                            )) {
                                continue;
                            }

                            String nameWithoutExtension = name.substring(
                                    0,
                                    name.length()
                                            - normalizedExtension.length()
                                            - 1
                            );

                            if (!nameWithoutExtension.contains(
                                    normalizedFileName
                            )) {
                                continue;
                            }

                            matches.add(realPath);

                        } catch (IOException e) {
                            log.debug(
                                    "Skipping unsafe or inaccessible entry during search: filePath={}",
                                    entry,
                                    e
                            );
                        }
                    }
                }

            } catch (DirectoryIteratorException e) {
                if (directory.equals(searchPath)) {
                    throw e.getCause();
                }

                log.debug(
                        "Skipping directory after iteration failure: directoryPath={}",
                        directory,
                        e
                );

            } catch (IOException e) {
                if (directory.equals(searchPath)) {
                    throw e;
                }

                log.debug(
                        "Skipping unsafe or inaccessible directory during search: directoryPath={}",
                        directory,
                        e
                );
            }
        }

        List<String> result = matches.stream()
                .map(Path::toString)
                .sorted()
                .toList();

        log.debug(
                "File search completed: projectName={}, matches={}",
                projectName,
                result.size()
        );

        return result;
    }
}
