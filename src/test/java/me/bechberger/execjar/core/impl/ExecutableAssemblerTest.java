package me.bechberger.execjar.core.impl;

import me.bechberger.execjar.core.LauncherConfig;
import me.bechberger.execjar.core.ShellCheckValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

class ExecutableAssemblerTest {

    @TempDir
    Path tempDir;

    @Test
    void testAssembleBasic() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.isRegularFile(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    @Test
    void testAssembleIsExecutable() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        // Check if file is executable
        assertTrue(Files.isExecutable(outputFile));

        // On POSIX systems, verify execute permissions
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(outputFile);
            assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException e) {
            // Not a POSIX system, skip this check
        }
    }

    @Test
    void testAssembleStartsWithShebang() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        byte[] bytes = Files.readAllBytes(outputFile);
        String start = new String(bytes, 0, Math.min(20, bytes.length));

        assertTrue(start.startsWith("#!/usr/bin/env sh"));
    }

    @Test
    void testAssembleContainsJarData() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        byte[] executableBytes = Files.readAllBytes(outputFile);
        byte[] jarBytes = Files.readAllBytes(jarFile);

        // The executable should be larger than the JAR (launcher + JAR)
        assertTrue(executableBytes.length > jarBytes.length);

        // The JAR signature (PK) should be present in the executable
        boolean foundJarSignature = false;
        for (int i = 0; i < executableBytes.length - 1; i++) {
            if (executableBytes[i] == 'P' && executableBytes[i + 1] == 'K') {
                foundJarSignature = true;
                break;
            }
        }
        assertTrue(foundJarSignature, "JAR signature not found in executable");
    }

    @Test
    void testAssembleNullJarFile() throws IOException, ShellCheckValidationException {
        Path outputFile = tempDir.resolve("output");
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();

        assertThrows(IllegalArgumentException.class, () -> {
            assembler.assemble(null, outputFile, config);
        });
    }

    @Test
    void testAssembleNullOutputFile() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();

        assertThrows(IllegalArgumentException.class, () -> {
            assembler.assemble(jarFile, null, config);
        });
    }

    @Test
    void testAssembleNullConfig() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("output");

        ExecutableAssembler assembler = new ExecutableAssembler();

        assertThrows(IllegalArgumentException.class, () -> {
            assembler.assemble(jarFile, outputFile, null);
        });
    }

    @Test
    void testAssembleNonExistentJar() throws IOException, ShellCheckValidationException {
        Path jarFile = tempDir.resolve("does-not-exist.jar");
        Path outputFile = tempDir.resolve("output");
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();

        assertThrows(IOException.class, () -> {
            assembler.assemble(jarFile, outputFile, config);
        });
    }

    @Test
    void testAssembleCreatesParentDirectories() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("nested/deep/directory/test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.exists(outputFile.getParent()));
    }

    @Test
    void testAssembleOverwritesExisting() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        // Create an existing file
        Files.writeString(outputFile, "old content");
        long oldSize = Files.size(outputFile);

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));
        assertNotEquals(oldSize, Files.size(outputFile));
    }

    @Test
    void testAssembleWithAllOptions() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("my-awesome-app")
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .strictMode(true)
            .jvmOpts("-Xmx4g -Xms1g")
            .prependArgs("--config prod.yaml")
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));

        // Read the launcher portion and verify config is embedded
        byte[] bytes = Files.readAllBytes(outputFile);
        String content = new String(bytes, 0, Math.min(10000, bytes.length));

        assertTrue(content.contains("my-awesome-app"));
        assertTrue(content.contains("MIN_JAVA_VERSION=17"));
        assertTrue(content.contains("MAX_JAVA_VERSION=21"));
        assertTrue(content.contains("STRICT_MODE=1"));
        assertTrue(content.contains("-Xmx4g"));
        assertTrue(content.contains("--config prod.yaml"));
    }

    @Test
    void testAssembleDeterministic() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile1 = tempDir.resolve("test-app-executable-1");
        Path outputFile2 = tempDir.resolve("test-app-executable-2");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile1, config);

        // Small delay to ensure different timestamps if they were included
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assembler.assemble(jarFile, outputFile2, config);

        // Both files should be identical
        byte[] bytes1 = Files.readAllBytes(outputFile1);
        byte[] bytes2 = Files.readAllBytes(outputFile2);

        assertArrayEquals(bytes1, bytes2, "Assembled executables should be deterministic");
    }

    @Test
    void testAssemblePreservesJarContent() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        // Find where the JAR starts in the executable
        byte[] execBytes = Files.readAllBytes(outputFile);
        byte[] jarBytes = Files.readAllBytes(jarFile);

        int jarStart = -1;
        for (int i = 0; i < execBytes.length - 1; i++) {
            if (execBytes[i] == 'P' && execBytes[i + 1] == 'K') {
                jarStart = i;
                break;
            }
        }

        assertTrue(jarStart > 0, "JAR start not found");

        // Verify the JAR portion is identical
        int jarLength = jarBytes.length;
        assertEquals(execBytes.length, jarStart + jarLength, "Executable size should be launcher + JAR");

        for (int i = 0; i < jarLength; i++) {
            assertEquals(jarBytes[i], execBytes[jarStart + i],
                "JAR byte mismatch at offset " + i);
        }
    }

    @Test
    void testAssembleWithLargeJar() throws IOException, ShellCheckValidationException {
        Path jarFile = createLargeJar("large-app", 1024 * 1024); // 1MB
        Path outputFile = tempDir.resolve("large-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("large-app")
            .minJavaVersion(11)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));
        // The output should be larger than the JAR (launcher + JAR)
        long jarSize = Files.size(jarFile);
        long outputSize = Files.size(outputFile);
        assertTrue(outputSize > jarSize,
            "Output size (" + outputSize + ") should be larger than JAR size (" + jarSize + ")");
    }

    @Test
    void testAssembleWithShellcheckValidation() throws IOException, ShellCheckValidationException {
        Path jarFile = createValidJar("test-app");
        Path outputFile = tempDir.resolve("test-app-executable");

        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(11)
            .validateWithShellcheck(true)
            .build();

        ExecutableAssembler assembler = new ExecutableAssembler();
        // This should succeed (or skip if shellcheck not available)
        assembler.assemble(jarFile, outputFile, config);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.isExecutable(outputFile));
    }

    // Helper methods

    private Path createValidJar(String name) throws IOException {
        Path jarFile = tempDir.resolve(name + ".jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.example.Main");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new ZipEntry("com/example/Main.class"));
            jos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            jos.closeEntry();

            jos.putNextEntry(new ZipEntry("test.txt"));
            jos.write("Hello, World!".getBytes());
            jos.closeEntry();
        }

        return jarFile;
    }

    private Path createLargeJar(String name, int sizeBytes) throws IOException {
        Path jarFile = tempDir.resolve(name + ".jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.example.Main");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new ZipEntry("large-file.bin"));
            byte[] buffer = new byte[8192];
            int remaining = sizeBytes;
            while (remaining > 0) {
                int toWrite = Math.min(buffer.length, remaining);
                jos.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            jos.closeEntry();
        }

        return jarFile;
    }
}