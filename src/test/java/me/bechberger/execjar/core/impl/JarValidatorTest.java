package me.bechberger.execjar.core.impl;

import me.bechberger.execjar.core.JarValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

class JarValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void testValidateValidJar() throws IOException, JarValidationException {
        Path jarFile = createValidJar("test-app");

        String mainClass = JarValidator.validate(jarFile);

        assertEquals("com.example.Main", mainClass);
    }

    @Test
    void testValidateNullPath() {
        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(null)
        );

        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testValidateNonExistentFile() {
        Path nonExistent = tempDir.resolve("does-not-exist.jar");

        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(nonExistent)
        );

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testValidateDirectory() throws IOException {
        Path directory = tempDir.resolve("not-a-jar");
        Files.createDirectory(directory);

        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(directory)
        );

        assertTrue(exception.getMessage().contains("not a regular file"));
    }

    @Test
    void testValidateJarWithoutManifest() throws IOException {
        Path jarFile = tempDir.resolve("no-manifest.jar");

        // Create JAR without manifest
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jos.putNextEntry(new ZipEntry("test.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(jarFile)
        );

        assertTrue(exception.getMessage().contains("does not contain a META-INF/MANIFEST.MF"));
    }

    @Test
    void testValidateJarWithoutMainClass() throws IOException {
        Path jarFile = tempDir.resolve("no-main-class.jar");

        // Create JAR with manifest but no Main-Class
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new ZipEntry("test.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(jarFile)
        );

        assertTrue(exception.getMessage().contains("does not contain a Main-Class"));
    }

    @Test
    void testValidateJarWithEmptyMainClass() throws IOException {
        Path jarFile = tempDir.resolve("empty-main-class.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "   ");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new ZipEntry("test.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        JarValidationException exception = assertThrows(
            JarValidationException.class,
            () -> JarValidator.validate(jarFile)
        );

        assertTrue(exception.getMessage().contains("does not contain a Main-Class"));
    }

    @Test
    void testValidateJarWithWhitespaceMainClass() throws IOException, JarValidationException {
        Path jarFile = tempDir.resolve("whitespace-main-class.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "  com.example.Main  ");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new ZipEntry("test.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        String mainClass = JarValidator.validate(jarFile);
        assertEquals("com.example.Main", mainClass);
    }

    @Test
    void testExtractArtifactNameSimple() {
        Path jarFile = Path.of("/path/to/myapp.jar");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameWithVersion() {
        Path jarFile = Path.of("/path/to/myapp-1.0.0.jar");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameWithSnapshot() {
        Path jarFile = Path.of("/path/to/myapp-1.0-SNAPSHOT.jar");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameWithComplexVersion() {
        Path jarFile = Path.of("/path/to/myapp-2.5.3.jar");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameWithoutJarExtension() {
        Path jarFile = Path.of("/path/to/myapp");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameWithUppercaseExtension() {
        Path jarFile = Path.of("/path/to/myapp.JAR");
        assertEquals("myapp", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameEmpty() {
        Path jarFile = Path.of("/path/to/.jar");
        assertEquals("application", JarValidator.extractArtifactName(jarFile));
    }

    @Test
    void testExtractArtifactNameComplexName() {
        Path jarFile = Path.of("/path/to/my-complex-app-name-3.2.1.jar");
        assertEquals("my-complex-app-name", JarValidator.extractArtifactName(jarFile));
    }

    // Helper method to create a valid JAR for testing
    private Path createValidJar(String name) throws IOException {
        Path jarFile = tempDir.resolve(name + ".jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.example.Main");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            // Add a dummy class file
            jos.putNextEntry(new ZipEntry("com/example/Main.class"));
            jos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Minimal class file magic
            jos.closeEntry();
        }

        return jarFile;
    }
}