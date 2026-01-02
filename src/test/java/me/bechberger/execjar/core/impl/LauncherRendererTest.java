package me.bechberger.execjar.core.impl;

import me.bechberger.execjar.core.LauncherConfig;
import me.bechberger.execjar.core.validation.ShellCheck;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class LauncherRendererTest {

    @TempDir
    static Path tempDir;

    private static ShellCheck shellcheck;
    private static boolean shellcheckAvailable;

    @BeforeAll
    static void setupShellCheck() {
        shellcheck = new ShellCheck(tempDir.resolve("shellcheck-cache"));
        shellcheckAvailable = shellcheck.isAvailable();
        if (!shellcheckAvailable) {
            System.out.println("WARNING: ShellCheck not available - script validation will be skipped");
        }
    }

    /**
     * Validates the rendered script with shellcheck if available.
     * Logs a warning but doesn't fail if shellcheck is not available.
     */
    private void validateWithShellCheck(String script, String testName) throws IOException {
        if (!shellcheckAvailable) {
            return;
        }

        ShellCheck.ShellCheckResult result = shellcheck.validate(script);
        if (!result.isSuccess()) {
            System.err.println("ShellCheck validation failed in " + testName + ":");
            System.err.println(result.getOutputAsString());
        }
        assertTrue(result.isSuccess(),
            "Generated launcher script should pass shellcheck validation in " + testName +
            ". Output:\n" + result.getOutputAsString());
    }

    @Test
    void testRenderMinimalConfig() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertNotNull(result);
        assertTrue(result.startsWith("#!/usr/bin/env sh"));
        assertThat(result).contains("MIN_JAVA_VERSION=11");
        assertThat(result).contains("test-app");
        assertTrue(result.endsWith("\n"));

        validateWithShellCheck(result, "testRenderMinimalConfig");
    }

    @Test
    void testRenderWithMaxJavaVersion() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("MIN_JAVA_VERSION=17");
        assertThat(result).contains("MAX_JAVA_VERSION=21");

        validateWithShellCheck(result, "testRenderWithMaxJavaVersion");
    }

    @Test
    void testRenderWithoutMaxJavaVersion() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("MIN_JAVA_VERSION=17");
        assertThat(result).contains("MAX_JAVA_VERSION=\"\"");

        validateWithShellCheck(result, "testRenderWithoutMaxJavaVersion");
    }

    @Test
    void testRenderWithStrictMode() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .strictMode(true)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("STRICT_MODE=1");

        validateWithShellCheck(result, "testRenderWithStrictMode");
    }

    @Test
    void testRenderWithoutStrictMode() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .strictMode(false)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("STRICT_MODE=0");

        validateWithShellCheck(result, "testRenderWithoutStrictMode");
    }

    @Test
    void testRenderWithjvmOpts() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .jvmOpts("-Xmx2g -Xms512m")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        // Check that javaOpts are passed directly to exec command, not via JAVA_OPTS variable
        assertThat(result).contains("-Xmx2g -Xms512m");
        assertThat(result).contains("-Dsun.misc.URLClassPath.disableJarChecking");

        validateWithShellCheck(result, "testRenderWithjvmOpts");
    }

    @Test
    void testRenderWithoutjvmOpts() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("-Dsun.misc.URLClassPath.disableJarChecking");

        validateWithShellCheck(result, "testRenderWithoutjvmOpts");
    }

    @Test
    void testRenderWithDefaultArgs() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .prependArgs("--help --verbose")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("exec \"$BEST_JAVA\" $USER_JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking -jar \"$0\" --help --verbose \"$@\"");
    }

    @Test
    void testRenderWithEmptyDefaultArgs() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .prependArgs("")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);
        assertThat(result).contains("exec \"$BEST_JAVA\" $USER_JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking -jar \"$0\" \"$@\"");
    }

    @Test
    void testRenderWithoutDefaultArgs() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);
        assertThat(result).contains("exec \"$BEST_JAVA\" $USER_JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking -jar \"$0\" \"$@\"");
    }

    @Test
    void testRenderWithQuotesInDefaultArgsAndJvmOpts() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .prependArgs("--message \"Hello World\" --path '/some path/with spaces'")
            .jvmOpts("-Dapp.name=\"My App\" -Dapp.path='/some path/with spaces'")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("    exec \"$BEST_JAVA\" -Dapp.name=\"My App\" -Dapp.path='/some path/with spaces' $USER_JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking -jar \"$0\" --message \"Hello World\" --path '/some path/with spaces' \"$@\"");
    }

    @Test
    void testRenderAllOptions() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("my-awesome-app")
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .strictMode(true)
            .jvmOpts("-Xmx4g")
            .prependArgs("--config prod.yaml")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("MIN_JAVA_VERSION=17")
                .contains("MAX_JAVA_VERSION=21")
                .contains("STRICT_MODE=1")
                .contains("-Xmx4g")
                .contains("--config prod.yaml")
                .contains("my-awesome-app");
    }

    @Test
    void testRenderEndsWithNewline() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertTrue(result.endsWith("\n"));
        assertFalse(result.endsWith("\n\n")); // Should not have multiple trailing newlines
    }

    @Test
    void testRenderUtf8Encoding() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app-with-émojis-🚀")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        // Should handle UTF-8 correctly
        assertThat(result).contains("test-app-with-émojis-🚀");

        // Verify no BOM
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        assertFalse(bytes.length >= 3 &&
                   bytes[0] == (byte) 0xEF &&
                   bytes[1] == (byte) 0xBB &&
                   bytes[2] == (byte) 0xBF);
    }

    @Test
    void testRenderShebang() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertTrue(result.startsWith("#!/usr/bin/env sh\n"));
    }

    @Test
    void testRenderDebugModePresent() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("EXECJAR_DEBUG").contains("debug()");
    }

    @Test
    void testRenderJavaDiscoveryLogic() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        // Verify Java discovery locations are present
        assertThat(result).contains("JAVA_HOME")
                .contains("command -v java")
                .contains("/etc/alternatives/java")
                .contains("/Library/Java/JavaVirtualMachines")
                .contains("/usr/lib/jvm");
    }

    @Test
    void testRenderVersionValidation() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("get_java_version")
                .contains("is_valid_version");
    }

    @Test
    void testRenderExecuteJar() throws IOException {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        assertThat(result).contains("exec \"$BEST_JAVA\" $USER_JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking")
                .contains("-jar \"$0\" \"$@\"");
    }

    @Test
    void testShellCheckValidationMinimal() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir.resolve("shellcheck-cache"));

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping validation test");
            return;
        }

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        ShellCheck.ShellCheckResult validation = shellcheck.validate(result);

        if (!validation.isSuccess()) {
            System.err.println("ShellCheck output:");
            System.err.println(validation.getOutputAsString());
        }

        assertTrue(validation.isSuccess(),
            "Launcher script should pass shellcheck validation. Output:\n" + validation.getOutputAsString());
    }

    @Test
    void testShellCheckValidationAllOptions() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir.resolve("shellcheck-cache"));

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping validation test");
            return;
        }

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("my-awesome-app")
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .strictMode(true)
            .jvmOpts("-Xmx4g -Xms1g")
            .prependArgs("--config prod.yaml")
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        ShellCheck.ShellCheckResult validation = shellcheck.validate(result);

        if (!validation.isSuccess()) {
            System.err.println("ShellCheck output:");
            System.err.println(validation.getOutputAsString());
        }

        assertTrue(validation.isSuccess(),
            "Launcher script with all options should pass shellcheck validation. Output:\n" + validation.getOutputAsString());
        assertFalse(validation.hasErrors(), "Should not have shellcheck errors");
    }

    @Test
    void testShellCheckValidationStrictMode() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir.resolve("shellcheck-cache"));

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping validation test");
            return;
        }

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .strictMode(true)
            .build();

        LauncherRenderer renderer = new LauncherRenderer();
        String result = renderer.render(config);

        ShellCheck.ShellCheckResult validation = shellcheck.validate(result);

        assertTrue(validation.isSuccess(),
            "Launcher script with strict mode should pass shellcheck validation. Output:\n" + validation.getOutputAsString());
    }
}