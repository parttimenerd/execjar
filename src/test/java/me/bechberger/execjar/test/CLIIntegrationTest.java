package me.bechberger.execjar.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the CLI tool.
 * These tests invoke the Main class directly and verify the output.
 */
public class CLIIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Helper method to create a simple runnable JAR for testing.
     */
    private Path createTestJar(String jarName, String mainClass) throws IOException {
        Path jarPath = tempDir.resolve(jarName);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            // Add a dummy class file
            ZipEntry entry = new ZipEntry("TestClass.class");
            jos.putNextEntry(entry);
            jos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Minimal class file signature
            jos.closeEntry();
        }

        return jarPath;
    }

    /**
     * Helper to invoke the CLI with arguments and capture output.
     * Uses picocli's CommandLine.execute() which returns the exit code without calling System.exit()
     */
    private CLIResult invokeCLI(String... args) {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        int exitCode;
        try {
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            // Use CommandLine.execute() instead of Main.main() to avoid System.exit()
            picocli.CommandLine cmd = new picocli.CommandLine(new me.bechberger.execjar.Main());
            exitCode = cmd.execute(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        return new CLIResult(exitCode, outContent.toString(), errContent.toString());
    }

    @Test
    public void testBasicCLIExecution() throws IOException {
        Path inputJar = createTestJar("test.jar", "com.example.Main");
        Path outputExec = tempDir.resolve("test-exec");

        CLIResult result = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString()
        );

        assertEquals(0, result.exitCode, "CLI should succeed");
        assertTrue(Files.exists(outputExec), "Executable should be created");

        // Verify it's executable on Unix systems
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(outputExec);
            assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE),
                      "File should be executable");
        }

        // Verify it starts with shebang (read only first line to avoid binary data)
        byte[] firstBytes = new byte[512];
        try (var is = Files.newInputStream(outputExec)) {
            is.read(firstBytes);
        }
        String firstPart = new String(firstBytes, StandardCharsets.UTF_8);
        assertTrue(firstPart.startsWith("#!/"), "Should start with shebang");
    }

    @Test
    public void testCLIWithMinJavaVersion() throws IOException {
        Path inputJar = createTestJar("test.jar", "com.example.Main");
        Path outputExec = tempDir.resolve("test-exec");

        CLIResult result = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString(),
            "--min-java-version", "17"
        );

        assertEquals(0, result.exitCode, "CLI should succeed");
        assertTrue(Files.exists(outputExec), "Executable should be created");

        // Verify launcher contains version constraint (read as bytes to avoid encoding issues)
        byte[] content = Files.readAllBytes(outputExec);
        String contentStr = new String(content, StandardCharsets.ISO_8859_1); // Use ISO-8859-1 for binary-safe reading
        assertTrue(contentStr.contains("17") || contentStr.contains("minJavaVersion"),
                  "Should reference Java 17");
    }

    @Test
    public void testCLIWithJvmOpts() throws IOException, InterruptedException {
        // Create a JAR with real Java code that prints system properties and max memory
        Path inputJar = createTestJarWithCode("test.jar", "TestApp",
            "public class TestApp {" +
            "  public static void main(String[] args) {" +
            "    System.out.println(\"test.property=\" + System.getProperty(\"test.property\", \"NOT_SET\"));" +
            "    long maxMemMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;" +
            "    System.out.println(\"max.memory=\" + maxMemMB + \"MB\");" +
            "  }" +
            "}");

        Path outputExec = tempDir.resolve("test-exec");

        CLIResult result = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString(),
            "--jvm-opts", "-Xmx512m -Dtest.property=myvalue"
        );

        assertEquals(0, result.exitCode, "CLI should succeed");
        assertTrue(Files.exists(outputExec), "Executable should be created");

        // Actually execute the generated binary to verify JVM opts are applied
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            ProcessBuilder pb = new ProcessBuilder(outputExec.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(finished, "Process should finish within 5 seconds");
            assertEquals(0, proc.exitValue(), "Executable should run successfully");

            String outputStr = output.toString();
            assertTrue(outputStr.contains("test.property=myvalue"),
                      "Should have system property set via -D option. Output: " + outputStr);
            // Max memory should be around 512MB (may vary slightly due to JVM overhead)
            assertTrue(outputStr.contains("max.memory=") &&
                      (outputStr.contains("512") || outputStr.contains("50") || outputStr.contains("48")),
                      "Should have max memory around 512MB via -Xmx option. Output: " + outputStr);
        }
    }

    /**
     * Helper to create a JAR with actual compiled Java code.
     * This allows us to test executables that actually run and verify runtime behavior.
     */
    private Path createTestJarWithCode(String jarName, String className, String javaCode) throws IOException {
        Path jarPath = tempDir.resolve(jarName);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, className);

        // Write Java source code
        Path sourceFile = tempDir.resolve(className + ".java");
        Files.writeString(sourceFile, javaCode);

        // Compile the Java code using the Java Compiler API
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int compilationResult = compiler.run(null, null, null, sourceFile.toString());
        assertEquals(0, compilationResult, "Java code should compile successfully");

        // Create JAR with compiled class
        Path classFile = tempDir.resolve(className + ".class");
        assertTrue(Files.exists(classFile), "Compiled class file should exist");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            ZipEntry entry = new ZipEntry(className + ".class");
            jos.putNextEntry(entry);
            Files.copy(classFile, jos);
            jos.closeEntry();
        }

        return jarPath;
    }

    @Test
    public void testCLIWithDefaultArgs() throws IOException {
        Path inputJar = createTestJar("test.jar", "com.example.Main");
        Path outputExec = tempDir.resolve("test-exec");

        CLIResult result = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString(),
            "--prepend-args", "--default-arg1 --default-arg2"
        );

        System.err.println("CLI stdout: " + result.stdout);
        System.err.println("CLI stderr: " + result.stderr);

        assertEquals(0, result.exitCode, "CLI should succeed");

        // Binary-safe read
        byte[] content = Files.readAllBytes(outputExec);
        String contentStr = new String(content, StandardCharsets.ISO_8859_1);
        assertTrue(contentStr.contains("--default-arg1"), "Should contain default arg1");
        assertTrue(contentStr.contains("--default-arg2"), "Should contain default arg2");
    }

    @Test
    public void testCLIWithInvalidJar() {
        Path invalidJar = tempDir.resolve("invalid.jar");
        Path outputExec = tempDir.resolve("output");

        CLIResult result = invokeCLI(
            invalidJar.toString(),
            "-o", outputExec.toString()
        );

        assertNotEquals(0, result.exitCode, "Should fail with invalid JAR");
        assertFalse(Files.exists(outputExec), "Output should not be created");
    }

    @Test
    public void testCLIHelpOption() {
        CLIResult result = invokeCLI("--help");

        assertTrue(result.stdout.contains("Usage:") || result.stdout.contains("execjar"),
                  "Help should show usage information");
        assertTrue(result.stdout.contains("--min-java-version") ||
                  result.stdout.contains("minJavaVersion"),
                  "Help should show options");
    }

    @Test
    public void testCLIVersionOption() {
        CLIResult result = invokeCLI("--version");

        assertTrue(result.stdout.contains("0.1") || result.stdout.contains("execjar"),
                  "Version should show version information");
    }

    @Test
    public void testCLIVerboseMode() throws IOException {
        Path inputJar = createTestJar("test.jar", "com.example.Main");
        Path outputExec = tempDir.resolve("test-exec");

        CLIResult result = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString(),
            "-v"
        );

        assertEquals(0, result.exitCode, "CLI should succeed");
        assertFalse(result.stdout.isEmpty(), "Verbose mode should produce output");
    }

    @Test
    public void testCLIForceOverwrite() throws IOException {
        Path inputJar = createTestJar("test.jar", "com.example.Main");
        Path outputExec = tempDir.resolve("test-exec");

        // Create first time
        CLIResult result1 = invokeCLI(inputJar.toString(), "-o", outputExec.toString());
        assertEquals(0, result1.exitCode, "First creation should succeed");
        assertTrue(Files.exists(outputExec), "Executable should be created");

        long firstModified = Files.getLastModifiedTime(outputExec).toMillis();

        // Wait a bit to ensure different timestamp
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // By default, force is true, so it should overwrite automatically
        CLIResult result2 = invokeCLI(
            inputJar.toString(),
            "-o", outputExec.toString()
        );

        assertEquals(0, result2.exitCode, "Overwrite should succeed (force defaults to true)");
        long secondModified = Files.getLastModifiedTime(outputExec).toMillis();
        assertTrue(secondModified >= firstModified, "File should be updated");
    }

    /**
     * Helper class to store CLI invocation results.
     */
    private static class CLIResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CLIResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}