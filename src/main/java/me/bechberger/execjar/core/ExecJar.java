package me.bechberger.execjar.core;

import me.bechberger.execjar.core.impl.ExecutableAssembler;
import me.bechberger.execjar.core.impl.JarValidator;
import me.bechberger.execjar.core.validation.ShellCheck;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Main facade for the execjar core library.
 * Provides a simple API for creating executable JAR files.
 */
public class ExecJar {

    /**
     * Creates an executable file from a JAR.
     *
     * @param jarFile the input JAR file (must have Main-Class in manifest)
     * @param outputFile the output executable file
     * @param config the launcher configuration
     * @throws JarValidationException if the JAR is invalid or not runnable
     * @throws IOException if file operations fail
     * @throws ShellCheckValidationException if shellcheck validation fails (when enabled)
     */
    public static void create(Path jarFile, Path outputFile, LauncherConfig config)
            throws JarValidationException, IOException, ShellCheckValidationException {

        // Validate the JAR
        JarValidator.validate(jarFile);

        // Assemble the executable
        ExecutableAssembler assembler = new ExecutableAssembler();
        assembler.assemble(jarFile, outputFile, config);
    }

    /**
     * Creates an executable file from a JAR with default configuration.
     * Uses the JAR file name as the artifact name and Java 11 as minimum version.
     *
     * @param jarFile the input JAR file
     * @param outputFile the output executable file
     * @throws JarValidationException if the JAR is invalid or not runnable
     * @throws IOException if file operations fail
     * @throws ShellCheckValidationException if shellcheck validation fails (when enabled)
     */
    public static void create(Path jarFile, Path outputFile)
            throws JarValidationException, IOException, ShellCheckValidationException {

        String artifactName = JarValidator.extractArtifactName(jarFile);
        LauncherConfig config = LauncherConfig.builder()
            .artifactName(artifactName)
            .build();

        create(jarFile, outputFile, config);
    }

    /**
     * Validates a JAR file and returns its Main-Class.
     *
     * @param jarFile the JAR file to validate
     * @return the Main-Class attribute value
     * @throws JarValidationException if the JAR is invalid or not runnable
     */
    public static String validateJar(Path jarFile) throws JarValidationException {
        return JarValidator.validate(jarFile);
    }

    /**
     * Extracts the artifact name from a JAR file name.
     *
     * @param jarFile the JAR file
     * @return the extracted artifact name
     */
    public static String extractArtifactName(Path jarFile) {
        return JarValidator.extractArtifactName(jarFile);
    }

    /**
     * Creates an executable file from a JAR with shellcheck validation enabled.
     * Uses default configuration with shellcheck validation enabled.
     *
     * @param jarFile the input JAR file
     * @param outputFile the output executable file
     * @throws JarValidationException if the JAR is invalid or not runnable
     * @throws IOException if file operations fail
     * @throws ShellCheckValidationException if shellcheck validation fails
     */
    public static void createWithShellcheck(Path jarFile, Path outputFile)
            throws JarValidationException, IOException, ShellCheckValidationException {

        String artifactName = JarValidator.extractArtifactName(jarFile);
        LauncherConfig config = LauncherConfig.builder()
            .artifactName(artifactName)
            .validateWithShellcheck(true)
            .build();

        create(jarFile, outputFile, config);
    }

    /**
     * Validates a launcher configuration by rendering and checking the script.
     * This is useful for testing configurations before creating executables.
     *
     * @param config the launcher configuration to validate
     * @return shellcheck validation result (null if shellcheck is not available)
     * @throws IOException if script rendering or validation fails
     */
    public static ShellCheck.ShellCheckResult validateLauncherScript(LauncherConfig config) throws IOException {
        ShellCheck shellcheck = new ShellCheck();

        if (!shellcheck.isAvailable()) {
            System.err.println("Warning: Shellcheck is not available");
            return null;
        }

        // Render the launcher script
        me.bechberger.execjar.core.impl.LauncherRenderer renderer =
            new me.bechberger.execjar.core.impl.LauncherRenderer();
        String launcherScript = renderer.render(config);

        // Validate with shellcheck
        return shellcheck.validate(launcherScript);
    }

    /**
     * Checks if shellcheck is available on the system.
     *
     * @return true if shellcheck is available (in PATH or can be downloaded)
     */
    public static boolean isShellcheckAvailable() {
        ShellCheck shellcheck = new ShellCheck();
        return shellcheck.isAvailable();
    }
}