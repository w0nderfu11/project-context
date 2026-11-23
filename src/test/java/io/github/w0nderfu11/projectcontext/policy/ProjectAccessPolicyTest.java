package io.github.w0nderfu11.projectcontext.policy;

import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathResolver;
import io.github.w0nderfu11.projectcontext.filesystem.ProjectPathViolationException;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyConfiguration;
import io.github.w0nderfu11.projectcontext.policy.config.PolicyRules;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectAccessPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMatchCompleteFileNamesAtAnyDepth() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        PolicyRules rules = new PolicyRules(
                List.of(".env", "README"),
                List.of(),
                List.of(),
                List.of()
        );
        ProjectAccessPolicy policy = globalPolicy(registry, rules);

        assertTrue(
                policy.isExcluded("first", createFile(firstProject, "nested/.ENV"))
        );
        assertTrue(
                policy.isExcluded("first", createFile(firstProject, "src/doc/readme"))
        );
        assertFalse(
                policy.isExcluded("first", createFile(firstProject, "docker.env"))
        );
        assertFalse(
                policy.isExcluded("first", createFile(firstProject, "readme.md"))
        );
    }

    @Test
    void shouldDistinguishCompleteNamesFromFinalExtensions() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        Path docker = createFile(firstProject, "docker.env");
        Path production = createFile(firstProject, "production.ENV");
        Path dotFile = createFile(firstProject, ".env");
        Path archive = createFile(firstProject, "archive.tar.GZ");

        PolicyRules nameRules = new PolicyRules(
                List.of("docker.env"),
                List.of(".tar"),
                List.of(),
                List.of()
        );
        ProjectAccessPolicy names = globalPolicy(registry, nameRules);

        PolicyRules extensionRules = new PolicyRules(
                List.of(),
                List.of(".env", ".gz"),
                List.of(),
                List.of()
        );
        ProjectAccessPolicy extensions = globalPolicy(registry, extensionRules);

        assertTrue(names.isExcluded("first", docker));
        assertFalse(names.isExcluded("first", production));
        assertFalse(names.isExcluded("first", archive));

        assertTrue(extensions.isExcluded("first", docker));
        assertTrue(extensions.isExcluded("first", production));
        assertTrue(extensions.isExcluded("first", archive));
        assertFalse(extensions.isExcluded("first", dotFile));
        assertFalse(
                extensions.isExcluded("first", createFile(firstProject, "README"))
        );
    }

    @Test
    void shouldApplyGlobalAndProjectPathsRelativeToTheirRoots() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        ProjectAccessPolicy policy = getAccessPolicy(registry);

        for (String name : registry.getProjectNames()) {
            Path root = registry.getRoot(name);
            Path excluded = createFile(root, "config/LOCAL.JSON");
            Path allowed = createFile(root, "other/local.json");

            assertTrue(policy.isExcluded(name, excluded));
            assertFalse(policy.isExcluded(name, allowed));
        }

        Path firstPrivate = createFile(firstProject, "PRIVATE/nested/key");
        Path secondPrivate = createFile(
                registry.getRoot("second"),
                "private/nested/key"
        );
        Path backup = createFile(firstProject, "private-backup/key");

        assertTrue(policy.isExcluded("first", firstPrivate));
        assertFalse(policy.isExcluded("second", secondPrivate));
        assertFalse(policy.isExcluded("first", backup));

        ProjectPathResolver resolver = new ProjectPathResolver(registry);
        Path validated = resolver.resolve(
                "first",
                firstPrivate.getParent().resolve("./key")
        );

        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> policy.checkAccess("first", validated)
        );
        assertEquals(
                "Access denied by PROJECT DIRECTORY_PATH rule: private",
                exception.getMessage()
        );
    }

    @Test
    void shouldDistinguishFilesAndDirectoriesWithIdenticalRuleNames()
            throws IOException {

        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        PolicyRules rules = new PolicyRules(
                List.of("build"),
                List.of(".json"),
                List.of("data.v1"),
                List.of("private.v1/", "plain")
        );
        ProjectAccessPolicy policy = globalPolicy(registry, rules);

        Path build = Files.createDirectory(firstProject.resolve("build"));
        Path json = Files.createDirectory(firstProject.resolve("dir.json"));
        Path data = Files.createDirectory(firstProject.resolve("data.v1"));

        assertFalse(policy.isExcluded("first", build));
        assertFalse(policy.isExcluded("first", json));
        assertFalse(policy.isExcluded("first", data));
        assertFalse(
                policy.isExcluded("first", createFile(firstProject, "data.v1/file"))
        );
        assertFalse(
                policy.isExcluded("first", createFile(firstProject, "plain"))
        );

        Path nested = createFile(firstProject, "private.v1/deep/file");
        assertTrue(policy.isExcluded("first", firstProject.resolve("private.v1")));
        assertTrue(policy.isExcluded("first", nested.getParent()));
        assertTrue(policy.isExcluded("first", nested));
    }

    @Test
    void shouldReportGlobalScopeThenClosestDirectoryThenMostSpecificFileRule()
            throws IOException {

        ProjectRegistry registry = createRegistry();

        ProjectAccessPolicy policy = getProjectAccessPolicy(registry);

        Path root = registry.getRoot("first");

        assertDenial(
                policy,
                createFile(root, "internal/keys/key.log"),
                "GLOBAL DIRECTORY_PATH rule: internal/keys"
        );
        assertDenial(
                policy,
                createFile(root, "public/key.log"),
                "GLOBAL FILE_PATH rule: public/key.log"
        );
        assertDenial(
                policy,
                createFile(root, "other/key.log"),
                "GLOBAL FILE_NAME rule: Key.log"
        );
        assertDenial(
                policy,
                createFile(root, "other/debug.log"),
                "GLOBAL EXTENSION rule: .LOG"
        );
    }

    @Test
    void shouldAllowRootAndFilesWithoutAutomaticExclusions() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());
        Path file = createFile(firstProject, ".git/.env");

        assertFalse(policy.isExcluded("first", registry.getRoot("first")));
        policy.checkAccess("first", file);
    }

    @Test
    void shouldRejectNullPathWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> policy.isExcluded("first", null)
        );

        assertEquals("validatedPath must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullProjectNameWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path root = registry.getRoot("first");
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> policy.isExcluded(null, root)
        );

        assertEquals("project name must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectUnregisteredProjectWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path root = registry.getRoot("first");
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> policy.isExcluded("FIRST", root)
        );

        assertEquals("project is not registered: FIRST", exception.getMessage());
    }

    @Test
    void shouldRejectRelativePathWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.isExcluded("first", Path.of("file"))
        );
    }

    @Test
    void shouldRejectUnnormalizedPathWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path root = registry.getRoot("first");
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.isExcluded("first", root.resolve("x/../y"))
        );
    }

    @Test
    void shouldRejectPathFromAnotherProjectWithEmptyRules() throws IOException {
        ProjectRegistry registry = createRegistry();
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.isExcluded("first", registry.getRoot("second"))
        );
    }

    @Test
    void shouldRejectRulesForUnregisteredProject() throws IOException {
        ProjectRegistry registry = createRegistry();
        PolicyConfiguration configuration = new PolicyConfiguration(
                PolicyRules.empty(),
                Map.of("missing", PolicyRules.empty())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectAccessPolicy(registry, configuration)
        );
    }

    @Test
    void shouldPropagateFailureToDetermineEntryType() throws IOException {
        ProjectRegistry registry = createRegistry();

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("directory")
        );
        ProjectAccessPolicy policy = globalPolicy(registry, rules);
        Path removed = Files.createDirectory(
                registry.getRoot("first").resolve("directory")
        );
        Files.delete(removed);

        assertThrows(
                NoSuchFileException.class,
                () -> policy.checkAccess("first", removed)
        );
    }

    @Test
    void shouldRejectValidatedFileReplacedByInternalSymlink() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path project = registry.getRoot("first");
        Path target = createFile(project, "inside");

        assertReplacedFileRejected(registry, target);
    }

    @Test
    void shouldRejectValidatedFileReplacedByExternalSymlink() throws IOException {
        ProjectRegistry registry = createRegistry();
        Path target = Files.createFile(tempDir.resolve("outside"));

        assertReplacedFileRejected(registry, target);
    }

    @Test
    void shouldRejectRootReplacedByRegularFileEvenWithEmptyRules()
            throws IOException {

        ProjectRegistry registry = createRegistry();

        Path root = registry.getRoot("first");
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());
        policy.checkAccess("first", root);

        Files.delete(root);
        Files.createFile(root);

        assertThrows(
                ProjectPathViolationException.class,
                () -> policy.checkAccess("first", root)
        );
    }

    @Test
    void shouldMatchLiteralLinuxFileNames() throws IOException {
        assumeTrue(
                "Linux".equals(System.getProperty("os.name")),
                "Linux file names required"
        );
        ProjectRegistry registry = createRegistry();
        Path project = registry.getRoot("first");

        for (String name : List.of(
                "report?.txt",
                "backup:2026.txt",
                "notes|final.txt",
                "read\\me",
                "report.",
                "report "
        )) {
            PolicyRules rules = new PolicyRules(
                    List.of(name.toUpperCase(Locale.ROOT)),
                    List.of(),
                    List.of(),
                    List.of()
            );
            ProjectAccessPolicy policy = globalPolicy(registry, rules);

            assertTrue(
                    policy.isExcluded("first", createFile(project, "nested/" + name))
            );
        }
    }

    @Test
    void shouldKeepBackslashDistinctFromDirectorySeparator() throws IOException {
        assumeTrue(
                "Linux".equals(System.getProperty("os.name")),
                "Linux file names required"
        );

        ProjectRegistry registry = createRegistry();
        Path firstProject = registry.getRoot("first");

        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("config\\Secret"),
                List.of("backup\\private")
        );
        ProjectAccessPolicy policy = globalPolicy(registry, rules);

        Path literalFile = createFile(firstProject, "CONFIG\\secret");
        Path literalDirectoryChild = createFile(firstProject, "backup\\PRIVATE/key");
        Path nestedFile = createFile(firstProject, "config/secret");
        Path nestedDirectoryChild = createFile(firstProject, "backup/private/key");

        assertTrue(policy.isExcluded("first", literalFile));
        assertTrue(policy.isExcluded("first", literalDirectoryChild));
        assertFalse(policy.isExcluded("first", nestedFile));
        assertFalse(policy.isExcluded("first", nestedDirectoryChild));
    }

    private void assertDenial(
            ProjectAccessPolicy policy,
            Path file,
            String rule
    ) {
        ProjectAccessDeniedException exception = assertThrows(
                ProjectAccessDeniedException.class,
                () -> policy.checkAccess("first", file)
        );

        assertEquals("Access denied by " + rule, exception.getMessage());
    }

    private Path createFile(Path project, String relativePath) throws IOException {
        Path file = project.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.createFile(file);
    }

    private ProjectRegistry createRegistry() throws IOException {
        Path root = tempDir.toRealPath();
        Path first = Files.createDirectory(root.resolve("first"));
        Path second = Files.createDirectory(root.resolve("second"));

        return new ProjectRegistry(
                Map.of(
                        "first", first,
                        "second", second
                )
        );
    }

    private void assertReplacedFileRejected(
            ProjectRegistry registry,
            Path target
    ) throws IOException {

        Path file = createFile(registry.getRoot("first"), "entry");
        ProjectAccessPolicy policy = globalPolicy(registry, PolicyRules.empty());
        ProjectPathResolver resolver = new ProjectPathResolver(registry);
        Path validated = resolver.resolve("first", file);
        assertFalse(policy.isExcluded("first", validated));

        Files.delete(file);
        try {
            Files.createSymbolicLink(file, target);
        } catch (
                UnsupportedOperationException
                        | IOException
                        | SecurityException exception
        ) {
            assumeTrue(
                    false,
                    "Symbolic links are not available: "
                            + exception.getClass().getSimpleName()
            );
        }

        ProjectPathViolationException exception = assertThrows(
                ProjectPathViolationException.class,
                () -> policy.isExcluded("first", validated)
        );

        assertEquals("Symbolic links are not allowed", exception.getMessage());
        assertThrows(
                ProjectPathViolationException.class,
                () -> policy.checkAccess("first", validated)
        );
    }

    private ProjectAccessPolicy globalPolicy(
            ProjectRegistry registry,
            PolicyRules rules
    ) {
        PolicyConfiguration configuration = new PolicyConfiguration(rules, Map.of());
        return new ProjectAccessPolicy(registry, configuration);
    }

    private static ProjectAccessPolicy getAccessPolicy(ProjectRegistry registry) {
        PolicyRules global = new PolicyRules(
                List.of(),
                List.of(),
                List.of("Config/local.json"),
                List.of()
        );
        PolicyRules project = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("private/")
        );
        PolicyConfiguration configuration = new PolicyConfiguration(
                global,
                Map.of("first", project)
        );
        return new ProjectAccessPolicy(registry, configuration);
    }

    private static ProjectAccessPolicy getProjectAccessPolicy(ProjectRegistry registry) {
        PolicyRules global = new PolicyRules(
                List.of("Key.log"),
                List.of(".LOG"),
                List.of("public/key.log"),
                List.of("internal", "internal/keys")
        );
        PolicyRules project = new PolicyRules(
                List.of(),
                List.of(),
                List.of(),
                List.of("public")
        );
        PolicyConfiguration configuration = new PolicyConfiguration(
                global,
                Map.of("first", project)
        );
        return new ProjectAccessPolicy(registry, configuration);
    }
}
