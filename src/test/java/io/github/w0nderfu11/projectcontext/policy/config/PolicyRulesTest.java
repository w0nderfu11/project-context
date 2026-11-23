package io.github.w0nderfu11.projectcontext.policy.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PolicyRulesTest {

    @Test
    void shouldRejectInvalidExtension() {
        for (String extension : List.of(
                "",
                " ",
                ".",
                "..",
                "foo",
                "tar.gz",
                ".tar.gz",
                ".a/b",
                ".log "
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PolicyRules(
                            List.of(),
                            List.of(extension),
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    @Test
    void shouldRejectInvalidPathsForBothRuleTypes() {
        for (String path : List.of(
                "",
                " ",
                ".",
                "./",
                "..",
                "../key",
                "src/../key",
                "/root",
                "//host/share",
                "dir/\u0000file"
        )) {
            assertInvalidPath(path);
        }
    }

    @Test
    void shouldRejectUnsafeWindowsPaths() {
        assumeTrue(File.separatorChar == '\\', "Windows path rules required");

        for (String value : List.of(
                "src\\..\\key",
                "\\root",
                "\\\\host\\share",
                "C:\\root",
                "C:file",
                "dir/a:b",
                "dir/a?",
                "dir/.. /file"
        )) {
            assertInvalidPath(value);
        }
    }

    @Test
    void shouldNormalizeRelativePaths() {
        PolicyRules rules = new PolicyRules(
                List.of(),
                List.of(),
                List.of("src/./config"),
                List.of("." + File.separator + "Secret//logs/")
        );

        assertEquals(List.of("src/config"), rules.filePaths());
        assertEquals(List.of("Secret/logs"), rules.directoryPaths());
    }

    @Test
    void shouldRejectEquivalentDirectoryRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("secret/logs/", "SECRET" + File.separator + "./Logs")
                )
        );
    }

    @Test
    void shouldRejectDirectorySyntaxInFilePaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of(),
                        List.of(),
                        List.of("src/"),
                        List.of()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of(),
                        List.of(),
                        List.of("src/."),
                        List.of()
                )
        );
    }

    @Test
    void shouldRequireSingleCompleteFileName() {
        for (String value : List.of(
                "",
                " ",
                ".",
                "..",
                "dir/name",
                "dir/",
                "\u0000name"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PolicyRules(
                            List.of(value),
                            List.of(),
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    @Test
    void shouldRejectInvalidWindowsFileNames() {
        assumeTrue(File.separatorChar == '\\', "Windows path rules required");

        for (String value : List.of(
                "dir\\name",
                "dir\\",
                "a*b",
                "report?.txt",
                "file."
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PolicyRules(
                            List.of(value),
                            List.of(),
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    @Test
    void shouldRejectTrailingSpaceInWindowsNamesAndPaths() {
        assumeTrue(File.separatorChar == '\\', "Windows path rules required");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of("report "),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertEquals(
                "invalid rule name or path component: report ",
                exception.getMessage()
        );
        assertInvalidPath("config /file");
        assertInvalidPath("config/report ");
    }

    @Test
    void shouldPreserveTrailingSpaceInLinuxNamesAndPaths() {
        assumeTrue(
                "Linux".equals(System.getProperty("os.name")),
                "Linux path rules required"
        );

        PolicyRules rules = new PolicyRules(
                List.of("report "),
                List.of(),
                List.of("config /report "),
                List.of("private /")
        );

        assertEquals(List.of("report "), rules.fileNames());
        assertEquals(List.of("config /report "), rules.filePaths());
        assertEquals(List.of("private "), rules.directoryPaths());
    }

    @Test
    void shouldCopyRuleListsAndProjectMap() {
        List<String> names = new ArrayList<>(List.of("README"));
        PolicyRules rules = new PolicyRules(
                names,
                List.of(),
                List.of(),
                List.of()
        );
        Map<String, PolicyRules> projects = new HashMap<>();
        projects.put("project", rules);
        PolicyConfiguration configuration = new PolicyConfiguration(
                PolicyRules.empty(),
                projects
        );

        names.clear();
        projects.clear();

        assertEquals(
                List.of("README"),
                configuration.projects().get("project").fileNames()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.projects().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> rules.fileNames().clear()
        );
    }

    @Test
    void shouldRejectNullRuleCollection() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyRules(
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullRuleEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyRules(
                        Arrays.asList("readme", null),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullGlobalRules() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyConfiguration(null, Map.of())
        );
    }

    @Test
    void shouldRejectNullProjectMap() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyConfiguration(PolicyRules.empty(), null)
        );
    }

    @Test
    void shouldRejectBlankProjectName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyConfiguration(
                        PolicyRules.empty(),
                        Map.of(" ", PolicyRules.empty())
                )
        );
    }

    private void assertInvalidPath(String path) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of(),
                        List.of(),
                        List.of(path),
                        List.of()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyRules(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(path)
                )
        );
    }
}
