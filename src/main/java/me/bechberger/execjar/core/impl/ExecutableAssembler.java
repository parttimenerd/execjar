package me.bechberger.execjar.core.impl;

import me.bechberger.execjar.core.LauncherConfig;
import me.bechberger.execjar.core.ShellCheckValidationException;
import me.bechberger.execjar.core.validation.ShellCheck;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

/**
 * Assembles executable files by combining launcher script and JAR.
 */
public class ExecutableAssembler {

    private final LauncherRenderer renderer;

    public ExecutableAssembler() throws IOException {
        this.renderer = new LauncherRenderer();
    }

    /**
     * Assembles an executable file from a JAR and launcher configuration.
     *
     * @param jarFile the input JAR file
     * @param outputFile the output executable file
     * @param config the launcher configuration
     * @throws IOException if assembly fails
     * @throws ShellCheckValidationException if shellcheck validation fails
     */
    public void assemble(Path jarFile, Path outputFile, LauncherConfig config) throws IOException, ShellCheckValidationException {
        if (jarFile == null) {
            throw new IllegalArgumentException("JAR file cannot be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Launcher config cannot be null");
        }

        if (!Files.exists(jarFile)) {
            throw new IOException("JAR file does not exist: " + jarFile);
        }

        if (!Files.isReadable(jarFile)) {
            throw new IOException("JAR file is not readable: " + jarFile);
        }

        // Render the launcher script
        String launcherScript = renderer.render(config);

        // Validate with shellcheck if requested
        if (config.validateWithShellcheck()) {
            validateWithShellCheck(launcherScript);
        }

        byte[] launcherBytes = launcherScript.getBytes(StandardCharsets.UTF_8);

        // Create parent directories if needed
        Path parentDir = outputFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Write launcher + JAR to output file
        // Use a temp file for atomic operation
        Path tempFile = outputFile.resolveSibling(outputFile.getFileName() + ".tmp");

        try {
            try (OutputStream os = Files.newOutputStream(tempFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                // Write launcher bytes
                os.write(launcherBytes);

                // Copy JAR bytes
                Files.copy(jarFile, os);
            }

            // Make executable (chmod +x)
            makeExecutable(tempFile);

            // Atomic move to final destination
            Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            // Clean up temp file on error
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /**
     * Makes a file executable (chmod +x on Unix-like systems).
     *
     * @param file the file to make executable
     * @throws IOException if setting permissions fails
     */
    private void makeExecutable(Path file) throws IOException {
        try {
            // Try to use POSIX permissions if available
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                Files.getPosixFilePermissions(file);

            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);

            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // Not a POSIX file system (e.g., Windows)
            // Set executable flag using the generic method
            boolean success = file.toFile().setExecutable(true, false);
            if (!success) {
                throw new IOException("Failed to set executable permission on: " + file);
            }
        }
    }

    /**
     * Validates the launcher script using shellcheck.
     *
     * @param launcherScript the launcher script content
     * @throws IOException if shellcheck execution fails
     * @throws ShellCheckValidationException if validation fails
     */
    private void validateWithShellCheck(String launcherScript) throws IOException, ShellCheckValidationException {
        ShellCheck shellcheck = new ShellCheck();

        if (!shellcheck.isAvailable()) {
            System.err.println("Warning: Shellcheck validation requested but shellcheck is not available. Skipping validation.");
            return;
        }

        ShellCheck.ShellCheckResult result = shellcheck.validate(launcherScript);

        if (!result.isSuccess()) {
            throw new ShellCheckValidationException(result);
        }

        // Log warnings even if validation succeeds
        if (result.hasWarnings()) {
            System.out.println("Shellcheck warnings:");
            System.out.println(result.getOutputAsString());
        }
    }
}