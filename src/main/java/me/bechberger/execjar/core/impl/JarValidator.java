package me.bechberger.execjar.core.impl;

import me.bechberger.execjar.core.JarValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Validates JAR files to ensure they are runnable fat JARs with a Main-Class.
 */
public class JarValidator {

    /**
     * Validates that the given JAR file is runnable.
     *
     * @param jarFile the JAR file to validate
     * @return the Main-Class attribute value
     * @throws JarValidationException if the JAR is invalid or not runnable
     */
    public static String validate(Path jarFile) throws JarValidationException {
        if (jarFile == null) {
            throw new JarValidationException("JAR file path cannot be null");
        }

        if (!Files.exists(jarFile)) {
            throw new JarValidationException("JAR file does not exist: " + jarFile);
        }

        if (!Files.isRegularFile(jarFile)) {
            throw new JarValidationException("Path is not a regular file: " + jarFile);
        }

        if (!Files.isReadable(jarFile)) {
            throw new JarValidationException("JAR file is not readable: " + jarFile);
        }

        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Manifest manifest = jar.getManifest();

            if (manifest == null) {
                throw new JarValidationException(
                    "JAR file does not contain a META-INF/MANIFEST.MF: " + jarFile
                );
            }

            Attributes mainAttributes = manifest.getMainAttributes();
            String mainClass = mainAttributes.getValue("Main-Class");

            if (mainClass == null || mainClass.trim().isEmpty()) {
                throw new JarValidationException(
                    "JAR manifest does not contain a Main-Class attribute: " + jarFile
                );
            }

            return mainClass.trim();
        } catch (IOException e) {
            throw new JarValidationException("Failed to read JAR file: " + jarFile, e);
        } catch (SecurityException e) {
            throw new JarValidationException("Security exception while reading JAR: " + jarFile, e);
        }
    }

    /**
     * Extracts the artifact name from the JAR file name.
     * Removes the .jar extension and any version suffixes.
     *
     * @param jarFile the JAR file
     * @return the artifact name
     */
    public static String extractArtifactName(Path jarFile) {
        String fileName = jarFile.getFileName().toString();

        // Remove .jar extension
        if (fileName.toLowerCase().endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        // Remove common version patterns (e.g., -1.0.0, -1.0-SNAPSHOT)
        fileName = fileName.replaceAll("-\\d+(\\.\\d+)*(-SNAPSHOT)?$", "");

        return fileName.isEmpty() ? "application" : fileName;
    }
}