package io.github.w0nderfu11.projectcontext.policy.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public final class PolicyConfigurationStore {

    private static final Logger log =
            LoggerFactory.getLogger(PolicyConfigurationStore.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .withCoercionConfig(
                    LogicalType.Textual,
                    coercion -> coercion
                            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            )
            .build();

    private static final ObjectWriter WRITER = MAPPER.writer(
            new DefaultPrettyPrinter().withObjectIndenter(
                    new DefaultIndenter("  ", "\n")
            )
    );

    private final ProjectRegistry projectRegistry;
    private final Path configurationPath;

    public PolicyConfigurationStore(ProjectRegistry projectRegistry) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );
        this.configurationPath = null;
    }

    public PolicyConfigurationStore(
            ProjectRegistry projectRegistry,
            Path configurationPath
    ) {
        this.projectRegistry = Objects.requireNonNull(
                projectRegistry,
                "projectRegistry must not be null"
        );

        Objects.requireNonNull(
                configurationPath,
                "configurationPath must not be null"
        );

        for (Path component : configurationPath) {
            if ("..".equals(component.toString())) {
                throw new IllegalArgumentException(
                        "configurationPath must not contain '..' segments"
                );
            }
        }

        this.configurationPath = configurationPath.toAbsolutePath().normalize();

        if (configurationPath.toString().isBlank()
                || configurationPath.getFileName() == null
                || configurationPath.getFileName().toString().equals(".")) {
            throw new IllegalArgumentException(
                    "configurationPath must name a file"
            );
        }
    }

    public PolicyConfiguration load() throws IOException {
        if (configurationPath == null) {
            return PolicyConfiguration.empty();
        }

        validateConfigurationPath(false);

        try (InputStream input = Files.newInputStream(
                configurationPath,
                LinkOption.NOFOLLOW_LINKS
        )) {
            PolicyConfiguration configuration = MAPPER.readValue(
                    input,
                    PolicyConfiguration.class
            );

            if (configuration == null) {
                throw new IOException(
                        "Policy configuration must be an object"
                );
            }

            configuration.validateProjects(projectRegistry);
            return configuration;

        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Invalid policy configuration: " + e.getMessage(),
                    e
            );
        }
    }

    public void save(PolicyConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration must not be null");
        configuration.validateProjects(projectRegistry);

        if (configurationPath == null) {
            throw new IOException(
                    "Policy configuration file is not specified"
            );
        }

        byte[] content = WRITER.writeValueAsBytes(configuration);

        validateConfigurationPath(true);

        Path temporaryFile = Files.createTempFile(
                configurationPath.getParent(),
                ".policy-",
                ".tmp"
        );

        try {
            Files.write(
                    temporaryFile,
                    content,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS
            );

            validateConfigurationPath(true);
            Files.move(
                    temporaryFile,
                    configurationPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException | RuntimeException e) {
            try {
                validateParentDirectory();
                Files.deleteIfExists(temporaryFile);

            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }

            throw e;
        }

        log.debug("Policy configuration saved");
    }

    private void validateConfigurationPath(boolean allowMissingFile)
            throws IOException {

        validateParentDirectory();

        BasicFileAttributes attributes;

        try {
            attributes = Files.readAttributes(
                    configurationPath,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );

        } catch (NoSuchFileException e) {
            if (allowMissingFile) {
                return;
            }

            throw e;
        }

        if (attributes.isSymbolicLink()) {
            throw new IOException(
                    "Symbolic links are not allowed in the configuration path"
            );
        }

        if (!attributes.isRegularFile()) {
            throw new IOException(
                    "Policy configuration must be a regular file"
            );
        }
    }

    private void validateParentDirectory() throws IOException {
        Path parent = configurationPath.getParent();
        Path current = parent.getRoot();

        for (Path component : parent) {
            current = current.resolve(component);

            BasicFileAttributes attributes = Files.readAttributes(
                    current,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );

            if (attributes.isSymbolicLink()) {
                throw new IOException(
                        "Symbolic links are not allowed in the configuration path"
                );
            }

            if (!attributes.isDirectory()) {
                throw new IOException(
                        "Configuration path component is not a directory"
                );
            }
        }

        if (!parent.toRealPath().equals(parent)) {
            throw new IOException(
                    "Unsafe configuration directory redirection detected"
            );
        }
    }

    void validateProject(String projectName) {
        projectRegistry.getRoot(projectName);
    }
}
