package io.github.w0nderfu11.projectcontext.policy.config;

import io.github.w0nderfu11.projectcontext.policy.ProjectAccessPolicy;
import io.github.w0nderfu11.projectcontext.policy.enums.PolicyScope;
import io.github.w0nderfu11.projectcontext.policy.enums.RuleType;
import io.github.w0nderfu11.projectcontext.registry.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyConfigurationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistGlobalAndProjectChangesAndPreserveUnrelatedRules()
            throws IOException {

        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        service.addRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.FILE_NAME,
                "README"
        );
        service.addRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.EXTENSION,
                ".LOG"
        );
        service.addRule(
                PolicyScope.PROJECT,
                "first",
                RuleType.DIRECTORY_PATH,
                "Private/"
        );
        service.addRule(
                PolicyScope.PROJECT,
                "second",
                RuleType.FILE_PATH,
                "app/config"
        );

        PolicyConfiguration before = service.getConfiguration();
        assertEquals(before, store.load());

        service.removeRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.EXTENSION,
                ".log"
        );
        service.removeRule(
                PolicyScope.PROJECT,
                "first",
                RuleType.DIRECTORY_PATH,
                "./private"
        );

        PolicyConfiguration after = service.getConfiguration();
        assertEquals(after, store.load());
        assertEquals(List.of("README"), after.global().fileNames());
        assertTrue(after.global().extensions().isEmpty());
        assertFalse(after.projects().containsKey("first"));
        assertEquals(
                before.projects().get("second"),
                after.projects().get("second")
        );
        assertEquals(
                List.of("app/config"),
                after.projects().get("second").filePaths()
        );
        assertEquals(List.of(".LOG"), before.global().extensions());
        assertEquals(
                List.of("Private"),
                before.projects().get("first").directoryPaths()
        );
    }

    @Test
    void shouldRejectDuplicateAndMissingRulesWithoutPublishingChanges()
            throws IOException {

        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        service.addRule(
                PolicyScope.PROJECT,
                "first",
                RuleType.FILE_PATH,
                "src/Config"
        );
        PolicyConfiguration snapshot = service.getConfiguration();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addRule(
                        PolicyScope.PROJECT,
                        "first",
                        RuleType.FILE_PATH,
                        "SRC/./config"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.removeRule(
                        PolicyScope.GLOBAL,
                        null,
                        RuleType.FILE_PATH,
                        "src/config"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.removeRule(
                        PolicyScope.PROJECT,
                        "second",
                        RuleType.FILE_PATH,
                        "src/config"
                )
        );

        assertSame(snapshot, service.getConfiguration());
        assertEquals(snapshot, store.load());
    }

    @Test
    void shouldRejectNullScope() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.addRule(
                        null,
                        null,
                        RuleType.FILE_NAME,
                        "x"
                )
        );

        assertEquals("scope must not be null", exception.getMessage());
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectNullRuleType() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.addRule(
                        PolicyScope.GLOBAL,
                        null,
                        null,
                        "x"
                )
        );

        assertEquals("type must not be null", exception.getMessage());
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectNullRuleValue() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.addRule(
                        PolicyScope.GLOBAL,
                        null,
                        RuleType.FILE_NAME,
                        null
                )
        );

        assertEquals("rule value must not be null", exception.getMessage());
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectMissingProjectName() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.addRule(
                        PolicyScope.PROJECT,
                        null,
                        RuleType.FILE_NAME,
                        "x"
                )
        );

        assertEquals("project name must not be null", exception.getMessage());
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectProjectNameForGlobalRule() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addRule(
                        PolicyScope.GLOBAL,
                        "first",
                        RuleType.FILE_NAME,
                        "x"
                )
        );

        assertEquals(
                "projectName must be null for GLOBAL rules",
                exception.getMessage()
        );
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectUnregisteredProjectName() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addRule(
                        PolicyScope.PROJECT,
                        "First",
                        RuleType.FILE_NAME,
                        "x"
                )
        );

        assertEquals(
                "project is not registered: First",
                exception.getMessage()
        );
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectBlankProjectNameOnRemoval() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.removeRule(
                        PolicyScope.PROJECT,
                        " ",
                        RuleType.FILE_NAME,
                        "x"
                )
        );

        assertEquals(
                "project name must not be blank",
                exception.getMessage()
        );
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRejectTraversalOnRemoval() throws IOException {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.removeRule(
                        PolicyScope.GLOBAL,
                        null,
                        RuleType.FILE_PATH,
                        "../x"
                )
        );

        assertEquals(
                "rule path must not contain '..' segments",
                exception.getMessage()
        );
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldKeepActivePolicyAndEarlierSnapshotsImmutable() throws IOException {
        ProjectRegistry registry = registry();
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(registry, file);
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        PolicyConfiguration original = service.getConfiguration();
        ProjectAccessPolicy active = new ProjectAccessPolicy(registry, original);
        Path readme = Files.createFile(registry.getRoot("first").resolve("README"));

        service.addRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.FILE_NAME,
                "readme"
        );
        ProjectAccessPolicy restarted = new ProjectAccessPolicy(registry, store.load());

        assertFalse(active.isExcluded("first", readme));
        assertTrue(restarted.isExcluded("first", readme));
        assertEquals(PolicyConfiguration.empty(), original);

        PolicyRules savedRules = service.getConfiguration().global();
        assertThrows(
                UnsupportedOperationException.class,
                () -> savedRules.fileNames().clear()
        );
    }

    @Test
    void shouldKeepLastSavedSnapshotOnFailureAndAllowRetry() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        PolicyConfigurationStore store = store(registry(), file);
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        service.addRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.FILE_NAME,
                "README"
        );
        PolicyConfiguration saved = service.getConfiguration();
        Path backup = Files.move(file, tempDir.resolve("backup.json"));
        Files.createDirectory(file);
        Path marker = Files.createFile(file.resolve("marker"));

        assertThrows(
                IOException.class,
                () -> service.removeRule(
                        PolicyScope.GLOBAL,
                        null,
                        RuleType.FILE_NAME,
                        "README"
                )
        );
        assertSame(saved, service.getConfiguration());

        Files.delete(marker);
        Files.delete(file);
        Files.move(backup, file);
        assertEquals(saved, store.load());

        service.removeRule(
                PolicyScope.GLOBAL,
                null,
                RuleType.FILE_NAME,
                "README"
        );
        assertEquals(PolicyConfiguration.empty(), store.load());
    }

    @Test
    void shouldRetainBothConcurrentChanges() throws Exception {
        PolicyConfigurationStore store = emptyStore();
        PolicyConfigurationService service = new PolicyConfigurationService(store);

        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> first = executor.submit(
                    addFileName(service, start, "first.txt")
            );
            Future<Void> second = executor.submit(
                    addFileName(service, start, "second.txt")
            );
            start.countDown();
            first.get();
            second.get();
        }

        assertEquals(
                List.of("first.txt", "second.txt"),
                service.getConfiguration().global().fileNames()
        );
        assertEquals(service.getConfiguration(), store.load());
    }

    private ProjectRegistry registry() throws IOException {
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));

        return new ProjectRegistry(
                Map.of(
                        "first", first,
                        "second", second
                )
        );
    }

    private PolicyConfigurationStore emptyStore() throws IOException {
        Path file = tempDir.toRealPath().resolve("policy.json");
        return store(registry(), file);
    }

    private PolicyConfigurationStore store(
            ProjectRegistry registry,
            Path file
    ) throws IOException {

        PolicyConfigurationStore store = new PolicyConfigurationStore(registry, file);
        store.save(PolicyConfiguration.empty());
        return store;
    }

    private Callable<Void> addFileName(
            PolicyConfigurationService service,
            CountDownLatch start,
            String name
    ) {
        return () -> {
            start.await();
            service.addRule(
                    PolicyScope.GLOBAL,
                    null,
                    RuleType.FILE_NAME,
                    name
            );
            return null;
        };
    }
}
