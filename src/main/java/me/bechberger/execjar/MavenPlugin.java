package me.bechberger.execjar;

import me.bechberger.execjar.core.ExecJar;
import me.bechberger.execjar.core.JarValidationException;
import me.bechberger.execjar.core.LauncherConfig;
import me.bechberger.execjar.core.ShellCheckValidationException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Maven plugin goal to create executable files from fat JAR files.
 * <p>
 * This plugin wraps a runnable fat JAR (JAR with all dependencies) with a POSIX shell launcher script,
 * creating a single executable file that can be run directly on Unix-like systems.
 * </p>
 * <p>
 * <strong>Important:</strong> This plugin requires a fat JAR (uber JAR) that contains all dependencies.
 * Use maven-assembly-plugin with the jar-with-dependencies descriptor, or maven-shade-plugin.
 * </p>
 * <p>
 * Example usage with maven-assembly-plugin:
 * <pre>
 * &lt;build&gt;
 *   &lt;plugins&gt;
 *     &lt;!-- Create fat JAR --&gt;
 *     &lt;plugin&gt;
 *       &lt;artifactId&gt;maven-assembly-plugin&lt;/artifactId&gt;
 *       &lt;version&gt;3.6.0&lt;/version&gt;
 *       &lt;configuration&gt;
 *         &lt;descriptorRefs&gt;
 *           &lt;descriptorRef&gt;jar-with-dependencies&lt;/descriptorRef&gt;
 *         &lt;/descriptorRefs&gt;
 *         &lt;archive&gt;
 *           &lt;manifest&gt;
 *             &lt;mainClass&gt;com.example.Main&lt;/mainClass&gt;
 *           &lt;/manifest&gt;
 *         &lt;/archive&gt;
 *       &lt;/configuration&gt;
 *       &lt;executions&gt;
 *         &lt;execution&gt;
 *           &lt;id&gt;make-assembly&lt;/id&gt;
 *           &lt;phase&gt;package&lt;/phase&gt;
 *           &lt;goals&gt;
 *             &lt;goal&gt;single&lt;/goal&gt;
 *           &lt;/goals&gt;
 *         &lt;/execution&gt;
 *       &lt;/executions&gt;
 *     &lt;/plugin&gt;
 *
 *     &lt;!-- Create executable from fat JAR --&gt;
 *     &lt;plugin&gt;
 *       &lt;groupId&gt;me.bechberger&lt;/groupId&gt;
 *       &lt;artifactId&gt;execjar&lt;/artifactId&gt;
 *       &lt;version&gt;0.1.0&lt;/version&gt;
 *       &lt;executions&gt;
 *         &lt;execution&gt;
 *           &lt;goals&gt;
 *             &lt;goal&gt;execjar&lt;/goal&gt;
 *           &lt;/goals&gt;
 *         &lt;/execution&gt;
 *       &lt;/executions&gt;
 *       &lt;configuration&gt;
 *         &lt;minJavaVersion&gt;17&lt;/minJavaVersion&gt;
 *         &lt;validateWithShellcheck&gt;true&lt;/validateWithShellcheck&gt;
 *       &lt;/configuration&gt;
 *     &lt;/plugin&gt;
 *   &lt;/plugins&gt;
 * &lt;/build&gt;
 * </pre>
 */
@Mojo(name = "execjar", defaultPhase = LifecyclePhase.PACKAGE)
public class MavenPlugin extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Minimum Java version required to run the application.
     * Default is determined from maven.compiler.target or maven.compiler.release.
     * If neither is set, defaults to 11.
     */
    @Parameter(property = "execjar.minJavaVersion")
    private Integer minJavaVersion;

    /**
     * Maximum Java version allowed to run the application.
     * If not set, any version >= minJavaVersion is allowed.
     */
    @Parameter(property = "execjar.maxJavaVersion")
    private Integer maxJavaVersion;

    /**
     * If true, only use Java from PATH and fail if it doesn't meet version requirements.
     * If false (default), search for Java in common locations.
     */
    @Parameter(property = "execjar.strictMode", defaultValue = "false")
    private boolean strictMode;

    /**
     * JVM options to pass to the Java runtime.
     * Example: "-Xmx2G -Xms512M"
     */
    @Parameter(property = "execjar.jvmOpts")
    private String jvmOpts;

    /**
     * Output file path for the executable.
     * Default is ${project.build.directory}/${project.artifactId}.
     */
    @Parameter(property = "execjar.outputFile")
    private File outputFile;


    /**
     * Validate the generated launcher script with shellcheck.
     * This ensures POSIX compliance and catches common shell scripting errors.
     * Requires shellcheck to be available (will be auto-downloaded if needed).
     * Default is false.
     */
    @Parameter(property = "execjar.validateWithShellcheck", defaultValue = "false")
    private boolean validateWithShellcheck;

    /**
     * Skip execution of this plugin.
     * Default is false.
     */
    @Parameter(property = "execjar.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The JAR file to make executable.
     * If not specified, uses ${project.build.directory}/${project.build.finalName}.jar
     */
    @Parameter(property = "execjar.jarFile")
    private File jarFile;

    /**
     * Classifier for the JAR file (e.g., "jar-with-dependencies" for maven-assembly-plugin).
     * The plugin will look for ${project.build.finalName}-${classifier}.jar
     * This plugin requires a fat JAR (JAR with all dependencies included).
     * Default is "jar-with-dependencies" (standard maven-assembly-plugin classifier).
     */
    @Parameter(property = "execjar.classifier", defaultValue = "jar-with-dependencies")
    private String classifier;

    /**
     * Custom environment variables to set in the launcher script.
     * Example:
     * <pre>
     * &lt;environmentVariables&gt;
     *   &lt;RUN_AS_BINARY&gt;true&lt;/RUN_AS_BINARY&gt;
     *   &lt;APP_ENV&gt;production&lt;/APP_ENV&gt;
     * &lt;/environmentVariables&gt;
     * </pre>
     */
    @Parameter
    private Map<String, String> environmentVariables;

    /**
     * Java system properties to pass to the application.
     * These are added as -D flags automatically.
     * Example:
     * <pre>
     * &lt;javaProperties&gt;
     *   &lt;app.name&gt;MyApp&lt;/app.name&gt;
     *   &lt;app.version&gt;1.0.0&lt;/app.version&gt;
     * &lt;/javaProperties&gt;
     * </pre>
     */
    @Parameter
    private Map<String, String> javaProperties;

    /**
     * Arguments to prepend to the application arguments (before user-provided args).
     * Example: "--config /etc/myapp/config.yml"
     */
    @Parameter(property = "execjar.prependArgs")
    private String prependArgs;

    /**
     * Arguments to append to the application arguments (after user-provided args).
     * Example: "--verbose"
     */
    @Parameter(property = "execjar.appendArgs")
    private String appendArgs;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping execjar plugin execution");
            return;
        }

        try {
            // Determine the JAR file to process
            Path inputJar = getInputJar();
            if (!Files.exists(inputJar)) {
                throw new MojoFailureException("JAR file not found: " + inputJar);
            }

            // Determine the output file
            Path output = getOutputFile();

            // Build the launcher configuration
            LauncherConfig config = buildConfig();

            // Log the configuration
            logConfiguration(inputJar, output, config);

            // Create the executable
            ExecJar.create(inputJar, output, config);

            getLog().info("Successfully created executable: " + output);
            getLog().info("You can now run it with: ./" + output.getFileName());

        } catch (JarValidationException e) {
            throw new MojoFailureException("JAR validation failed: " + e.getMessage(), e);
        } catch (ShellCheckValidationException e) {
            throw new MojoFailureException(
                "Launcher script validation failed:\n" + e.getMessage() +
                "\n\nTo skip validation, set <validateWithShellcheck>false</validateWithShellcheck>", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create executable: " + e.getMessage(), e);
        }
    }

    /**
     * Get the input JAR file path.
     * Constructs the path to the fat JAR using the classifier.
     */
    private Path getInputJar() {
        if (jarFile != null) {
            return jarFile.toPath();
        }

        // Construct path: ${project.build.directory}/${project.build.finalName}-${classifier}.jar
        String buildDirectory = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName();

        String jarFileName;
        if (classifier != null && !classifier.trim().isEmpty()) {
            jarFileName = finalName + "-" + classifier + ".jar";
        } else {
            // Fallback to regular JAR if classifier is explicitly set to empty
            jarFileName = finalName + ".jar";
            getLog().warn("No classifier specified - looking for regular JAR. " +
                         "This plugin requires a fat JAR (with dependencies). " +
                         "Consider using classifier 'jar-with-dependencies' or configure maven-assembly-plugin.");
        }

        return Paths.get(buildDirectory, jarFileName);
    }

    /**
     * Detect the minimum Java version from Maven compiler configuration.
     * Checks maven.compiler.release first, then maven.compiler.target.
     * Falls back to 11 if neither is set.
     */
    private int detectMinJavaVersion() {
        if (minJavaVersion != null) {
            return minJavaVersion;
        }

        // Check maven.compiler.release first (preferred in Java 9+)
        String release = project.getProperties().getProperty("maven.compiler.release");
        if (release != null && !release.trim().isEmpty()) {
            try {
                int version = Integer.parseInt(release.trim());
                getLog().debug("Detected minJavaVersion from maven.compiler.release: " + version);
                return version;
            } catch (NumberFormatException e) {
                getLog().warn("Invalid maven.compiler.release value: " + release);
            }
        }

        // Check maven.compiler.target
        String target = project.getProperties().getProperty("maven.compiler.target");
        if (target != null && !target.trim().isEmpty()) {
            try {
                // Handle both "1.8" and "8" formats
                String versionStr = target.trim();
                if (versionStr.startsWith("1.")) {
                    versionStr = versionStr.substring(2);
                }
                int version = Integer.parseInt(versionStr);
                getLog().debug("Detected minJavaVersion from maven.compiler.target: " + version);
                return version;
            } catch (NumberFormatException e) {
                getLog().warn("Invalid maven.compiler.target value: " + target);
            }
        }

        // Default to 11 (minimum LTS)
        getLog().debug("No compiler target/release found, defaulting minJavaVersion to 11");
        return 11;
    }

    /**
     * Get the output file path.
     */
    private Path getOutputFile() {
        if (outputFile != null) {
            return outputFile.toPath();
        }

        // Default: ${project.build.directory}/${project.build.finalName} (no .jar extension)
        String buildDirectory = project.getBuild().getDirectory();
        String finalName = project.getArtifactId();
        return Paths.get(buildDirectory, finalName);
    }

    /**
     * Build the launcher configuration from plugin parameters.
     */
    private LauncherConfig buildConfig() {
        int effectiveMinJavaVersion = detectMinJavaVersion();

        LauncherConfig.Builder builder = LauncherConfig.builder()
            .minJavaVersion(effectiveMinJavaVersion)
            .strictMode(strictMode)
            .artifactName(project.getArtifactId())
            .validateWithShellcheck(validateWithShellcheck);

        if (maxJavaVersion != null) {
            builder.maxJavaVersion(maxJavaVersion);
        }

        if (jvmOpts != null && !jvmOpts.trim().isEmpty()) {
            builder.jvmOpts(jvmOpts);
        }

        // Add environment variables
        if (environmentVariables != null && !environmentVariables.isEmpty()) {
            builder.environmentVariables(environmentVariables);
        }

        // Add Java properties
        if (javaProperties != null && !javaProperties.isEmpty()) {
            builder.javaProperties(javaProperties);
        }

        // Add prepend args
        if (prependArgs != null && !prependArgs.trim().isEmpty()) {
            builder.prependArgs(prependArgs);
        }

        // Add append args
        if (appendArgs != null && !appendArgs.trim().isEmpty()) {
            builder.appendArgs(appendArgs);
        }

        return builder.build();
    }

    /**
     * Log the plugin configuration.
     */
    private void logConfiguration(Path inputJar, Path output, LauncherConfig config) {
        getLog().info("Creating executable from JAR");
        getLog().info("  Input JAR:        " + inputJar);
        getLog().info("  Output file:      " + output);
        getLog().info("  Artifact name:    " + config.artifactName());
        getLog().info("  Min Java version: " + config.minJavaVersion());
        if (config.maxJavaVersion() != null) {
            getLog().info("  Max Java version: " + config.maxJavaVersion());
        }
        getLog().info("  Strict mode:      " + config.strictMode());
        if (config.jvmOpts() != null) {
            getLog().info("  JVM opts:         " + config.jvmOpts());
        }
        if (config.validateWithShellcheck()) {
            getLog().info("  Shellcheck:       enabled");
        }
        if (config.prependArgs() != null && !config.prependArgs().isEmpty()) {
            getLog().info("  Prepend args:     " + config.prependArgs());
        }
        if (config.appendArgs() != null && !config.appendArgs().isEmpty()) {
            getLog().info("  Append args:      " + config.appendArgs());
        }
        if (!config.environmentVariables().isEmpty()) {
            getLog().info("  Environment vars: " + config.environmentVariables());
        }
        if (!config.javaProperties().isEmpty()) {
            getLog().info("  Java properties:  " + config.javaProperties());
        }
    }
}