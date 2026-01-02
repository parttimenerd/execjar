package me.bechberger.execjar.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Maven plugin.
 * These tests verify the results of maven-invoker-plugin runs.
 *
 * Tests are executed after maven-invoker-plugin has built the test projects
 * in target/it/
 */
public class MavenPluginIT {

    private static Path itProjectsDir;

    @BeforeAll
    public static void setup() {
        String itProjectsDirProperty = System.getProperty("it.projects.dir");
        assertNotNull(itProjectsDirProperty, "it.projects.dir system property must be set");
        if (itProjectsDirProperty.isEmpty()) {
            fail("it.projects.dir system property must not be empty");
        }
        itProjectsDir = Paths.get(itProjectsDirProperty);
        assertTrue(Files.exists(itProjectsDir),
                  "Integration test projects directory must exist: " + itProjectsDir);
    }

    @Test
    public void testBasicMavenPlugin() throws IOException {
        Path projectDir = itProjectsDir.resolve("basic-maven-plugin");
        assumeProjectExists(projectDir);

        // Verify fat JAR was created
        Path fatJar = projectDir.resolve("target/basic-maven-plugin-1.0-SNAPSHOT-jar-with-dependencies.jar");
        assertTrue(Files.exists(fatJar), "Fat JAR should be created: " + fatJar);
        assertTrue(Files.isRegularFile(fatJar), "Fat JAR should be a regular file");

        // Verify executable was created
        Path executable = assertExecutable(projectDir, "basic-maven-plugin");

        // Verify executable is larger than JAR (contains launcher + JAR)
        long execSize = Files.size(executable);
        long jarSize = Files.size(fatJar);
        assertTrue(execSize > jarSize,
                  String.format("Executable (%d bytes) should be larger than JAR (%d bytes)",
                               execSize, jarSize));

    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testBasicMavenPluginExecutePermissions() throws IOException {
        Path projectDir = itProjectsDir.resolve("basic-maven-plugin");
        assumeProjectExists(projectDir);

        assertExecutable(projectDir, "basic-maven-plugin");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testBasicMavenPluginExecution() throws IOException, InterruptedException {
        Path projectDir = itProjectsDir.resolve("basic-maven-plugin");
        assumeProjectExists(projectDir);
        // list files in target for debugging
        try {
            System.out.println("Files in target/:");
            Files.list(projectDir.resolve("target"))
                 .forEach(p -> System.out.println(" - " + p.getFileName()));
        } catch (IOException e) {
            System.err.println("Failed to list files in target/: " + e.getMessage());
        }
        Path executable = assertExecutable(projectDir, "basic-maven-plugin");

        // Execute it with test arguments
        ProcessBuilder pb = new ProcessBuilder(executable.toString(), "test", "args");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        assertTrue(finished, "Process should complete within 5 seconds");

        int exitCode = process.exitValue();
        assertEquals(0, exitCode, "Executable should exit with code 0");

        String fullOutput = String.join("\n", output);
        assertTrue(fullOutput.contains("BasicApp is running!"),
                  "Output should contain app message. Output: " + fullOutput);
        assertTrue(fullOutput.contains("test, args"),
                  "Output should contain arguments. Output: " + fullOutput);
    }

    private void assertStartsWithShebang(Path executable) throws IOException {
        // Verify shebang by reading first 1024 bytes to avoid binary data
        byte[] headerBytes = new byte[1024];
        try (var is = Files.newInputStream(executable)) {
            int read = is.read(headerBytes);
            String header = new String(headerBytes, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = header.split("\n", 2);
            assertTrue(lines.length > 0 && lines[0].startsWith("#!/"),
                    "Executable should start with shebang");
        }
    }

    @Test
    public void testCustomConfig() throws IOException {
        Path projectDir = itProjectsDir.resolve("custom-config");
        assumeProjectExists(projectDir);

        // Verify custom output file location
        Path executable = assertExecutable(projectDir, "custom-executable", "MIN_JAVA_VERSION=21", "MAX_JAVA_VERSION=23");
    }

    @Test
    public void testShadePlugin() throws IOException {
        Path projectDir = itProjectsDir.resolve("shade-plugin");
        assumeProjectExists(projectDir);

        // Verify shaded JAR was created (replaces original)
        Path shadedJar = projectDir.resolve("target/shade-plugin-1.0-SNAPSHOT.jar");
        assertTrue(Files.exists(shadedJar), "Shaded JAR should be created: " + shadedJar);

        // Verify executable was created from shaded JAR
        assertExecutable(projectDir, "shaded-executable");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testShadePluginExecutePermissions() throws IOException {
        Path projectDir = itProjectsDir.resolve("shade-plugin");
        assumeProjectExists(projectDir);

        Path executable = projectDir.resolve("target/shaded-executable");
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(executable);
        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE),
                  "Executable should have execute permission");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testShadePluginExecution() throws IOException, InterruptedException {
        Path projectDir = itProjectsDir.resolve("shade-plugin");
        assumeProjectExists(projectDir);

        Path executable = assertExecutable(projectDir, "shaded-executable");

        ProcessBuilder pb = new ProcessBuilder(executable.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        assertTrue(finished, "Process should complete within 5 seconds");

        String fullOutput = String.join("\n", output);
        assertTrue(fullOutput.contains("ShadeApp with maven-shade-plugin!"),
                  "Should contain expected message. Output: " + fullOutput);
    }

    /**
     * Verify that auto-detection of minJavaVersion works.
     * The basic-maven-plugin has maven.compiler.release=17,
     * so the launcher should use Java 17 as minimum.
     */
    @Test
    public void testMinJavaVersionAutoDetection() throws IOException {
        Path projectDir = itProjectsDir.resolve("basic-maven-plugin");
        assumeProjectExists(projectDir);

        assertExecutable(projectDir, "basic-maven-plugin", "MIN_JAVA_VERSION=17");
    }

    /**
     * Test JVM options and default arguments integration.
     * The jvm-opts-test project configures:
     * - jvmOpts: -Xmx256m -Dapp.name=JvmOptsTest -Dapp.version=1.0
     * - defaultArgs: --mode test --verbose
     *
     * The app validates these settings at runtime and exits with status 0 if correct, 1 if incorrect.
     */
    @Test
    public void testJvmOptsIntegration() throws IOException {
        Path projectDir = itProjectsDir.resolve("jvm-opts-test");
        assumeProjectExists(projectDir);

        // Verify fat JAR was created
        Path fatJar = projectDir.resolve("target/jvm-opts-test-1.0-SNAPSHOT-jar-with-dependencies.jar");
        assertTrue(Files.exists(fatJar), "Fat JAR should be created: " + fatJar);

        // Verify executable was created
        assertExecutable(projectDir, "jvm-opts-test");
    }

    private Path assertExecutable(Path projectDir, String expectedName, String... requiredContent) throws IOException {
        Path executable = projectDir.resolve("target/" + expectedName);
        assertTrue(Files.exists(executable), "Executable should be created: " + executable + ", but has files " +
                   Files.list(projectDir.resolve("target")).map(Path::getFileName).toList());
        assertTrue(Files.isRegularFile(executable), "Executable should be a regular file");
        assertStartsWithShebang(executable);
        // Verify execute permissions on Unix-like systems
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(executable);
        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE),
                "Executable should have owner execute permission");
        // Verify JAR signature is present
        byte[] execBytes = Files.readAllBytes(executable);
        assertTrue(containsJarSignature(execBytes), "Executable should contain JAR signature");
        if (requiredContent.length == 0) {
            return executable;
        }
        String execContent = new String(execBytes, java.nio.charset.StandardCharsets.UTF_8);
        for (String content : requiredContent) {
            assertTrue(execContent.contains(content),
                       "Executable should contain required content: " + content);
        }
        return executable;
    }

    /**
     * Test that JVM options and default arguments are actually applied when executing.
     * This is the real integration test - it runs the executable and verifies behavior.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testJvmOptsExecution() throws IOException, InterruptedException {
        Path projectDir = itProjectsDir.resolve("jvm-opts-test");
        assumeProjectExists(projectDir);

        Path executable = assertExecutable(projectDir, "jvm-opts-test");

        // Execute and capture output
        ProcessBuilder pb = new ProcessBuilder(executable.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        assertTrue(finished, "Process should complete within 5 seconds");

        int exitCode = process.exitValue();
        String fullOutput = String.join("\n", output);

        // The app validates everything internally and exits with 0 if correct, 1 if wrong
        assertEquals(0, exitCode,
                    "App should exit with 0 (all config correct). Output:\n" + fullOutput);

        // Also verify the output contains expected markers
        assertTrue(fullOutput.contains("app.name=JvmOptsTest"),
                  "Should have app.name system property. Output: " + fullOutput);
        assertTrue(fullOutput.contains("app.version=1.0"),
                  "Should have app.version system property. Output: " + fullOutput);
        assertTrue(fullOutput.contains("app.title=MyApplication"),
                  "Should have app.title with quoted value. Output: " + fullOutput);
        assertTrue(fullOutput.contains("max.memory=") &&
                  (fullOutput.contains("256") || fullOutput.contains("25") || fullOutput.contains("24")),
                  "Should have ~256MB max memory. Output: " + fullOutput);
        assertTrue(fullOutput.contains("--mode") && fullOutput.contains("--verbose"),
                  "Should have default arguments. Output: " + fullOutput);
        assertTrue(fullOutput.contains("SUCCESS"),
                  "App should report success. Output: " + fullOutput);
    }

    /**
     * Test advanced configuration features with spaces and special characters.
     * This tests javaProperties, environmentVariables, prependArgs, and appendArgs.
     */
    @Test
    public void testAdvancedConfig() throws IOException {
        Path projectDir = itProjectsDir.resolve("advanced-config-test");
        assumeProjectExists(projectDir);

        // Verify fat JAR was created
        Path fatJar = projectDir.resolve("target/advanced-config-test-1.0-SNAPSHOT-jar-with-dependencies.jar");
        assertTrue(Files.exists(fatJar), "Fat JAR should be created: " + fatJar);

        // Verify executable was created
        Path executable = projectDir.resolve("target/advanced-config-test");
        assertTrue(Files.exists(executable), "Executable should be created: " + executable);
        assertTrue(Files.isRegularFile(executable), "Executable should be a regular file");
    }

    /**
     * Test that advanced configuration features work when executing.
     * This validates javaProperties, environmentVariables, prependArgs, and appendArgs
     * with spaces and special characters that require proper escaping.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testAdvancedConfigExecution() throws IOException, InterruptedException {
        Path projectDir = itProjectsDir.resolve("advanced-config-test");
        assumeProjectExists(projectDir);

        Path executable = assertExecutable(projectDir, "advanced-config-test");

        // Execute and capture output
        ProcessBuilder pb = new ProcessBuilder(executable.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                System.out.println(line); // Print for debugging
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(finished, "Process should complete within 10 seconds");

        int exitCode = process.exitValue();
        String fullOutput = String.join("\n", output);

        // The app validates everything internally and exits with 0 if correct, 1 if wrong
        assertEquals(0, exitCode,
                    "App should exit with 0 (all config correct). Output:\n" + fullOutput);

        // Verify the output contains success marker
        assertTrue(fullOutput.contains("SUCCESS"),
                  "App should report success. Output: " + fullOutput);
    }

    /**
     * Test advanced configuration with user-provided arguments.
     * Verifies that prependArgs come before user args, and appendArgs come after.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testAdvancedConfigWithUserArgs() throws IOException, InterruptedException {
        Path projectDir = itProjectsDir.resolve("advanced-config-test");
        assumeProjectExists(projectDir);

        Path executable = assertExecutable(projectDir, "advanced-config-test");

        // Execute with user-provided arguments in the middle
        ProcessBuilder pb = new ProcessBuilder(
            executable.toString(),
            "--user-arg1",
            "user value with spaces",
            "--user-arg2"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                System.out.println(line); // Print for debugging
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(finished, "Process should complete within 10 seconds");

        String fullOutput = String.join("\n", output);

        // The app should still validate prepend/append args correctly
        // even with user args in the middle
        assertTrue(fullOutput.contains("SUCCESS: All configuration values are correct!"),
                  "Should detect user-provided args. Output: " + fullOutput);
        assertTrue(fullOutput.contains("--user-arg1"),
                  "Should contain user arg. Output: " + fullOutput);
        assertTrue(fullOutput.contains("user value with spaces"),
                  "Should contain user arg value. Output: " + fullOutput);
    }

    /**
     * Helper to check if JAR signature (PK\003\004) is present in bytes.
     */
    private boolean containsJarSignature(byte[] bytes) {
        // JAR files are ZIP files, signature is PK\003\004
        byte[] signature = new byte[]{0x50, 0x4B, 0x03, 0x04};

        for (int i = 0; i < bytes.length - 3; i++) {
            if (bytes[i] == signature[0] &&
                bytes[i+1] == signature[1] &&
                bytes[i+2] == signature[2] &&
                bytes[i+3] == signature[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to skip test if project doesn't exist.
     */
    private void assumeProjectExists(Path projectDir) {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.exists(projectDir),
            "Test project directory does not exist: " + projectDir
        );
    }
}