package me.bechberger.execjar;

import me.bechberger.execjar.core.ExecJar;
import me.bechberger.execjar.core.JarValidationException;
import me.bechberger.execjar.core.LauncherConfig;
import me.bechberger.execjar.core.ShellCheckValidationException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command-line interface for execjar.
 * Creates executable files from runnable JAR files.
 */
@Command(
    name = "execjar",
    description = "Create executable files from runnable JAR files",
    mixinStandardHelpOptions = true,
    version = "execjar 0.1.1",
    footer = {
        "",
        "Examples:",
        "  Create executable from JAR:",
        "    execjar myapp.jar",
        "",
        "  Specify output file:",
        "    execjar myapp.jar -o myapp",
        "",
        "  Set Java version constraints:",
        "    execjar myapp.jar --min-java-version 17 --max-java-version 21",
        "",
        "  Set environment variables:",
        "    execjar myapp.jar -E RUN_AS_BINARY=true -E APP_ENV=production",
        "",
        "  Set Java system properties:",
        "    execjar myapp.jar -D app.name=MyApp -D app.version=1.0.0",
        "",
        "  Prepend and append arguments:",
        "    execjar myapp.jar --prepend-args --config --prepend-args /etc/app.conf --append-args --verbose",
        "",
        "  Set default JVM options:",
        "    execjar myapp.jar --jvm-opts \"-Xmx4g -Xms1g\"",
        "",
        "  Enable shellcheck validation:",
        "    execjar myapp.jar --validate-shellcheck",
        "",
        "  Print launcher script to stdout:",
        "    execjar myapp.jar --print-launcher > launcher.sh",
        "",
        "For more information, visit: https://github.com/parttimenerd/execjar"
    }
)
public class Main implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Input JAR file (must be a runnable JAR with Main-Class in manifest)"
    )
    private Path inputJar;

    @Option(
        names = {"-o", "--output"},
        description = "Output executable file path (default: artifact name from JAR)"
    )
    private Path outputFile;

    @Option(
        names = {"--min-java-version"},
        description = "Minimum Java version required (default: 11)",
        defaultValue = "11"
    )
    private int minJavaVersion;

    @Option(
        names = {"--max-java-version"},
        description = "Maximum Java version allowed (optional)"
    )
    private Integer maxJavaVersion;

    @Option(
        names = {"--strict-mode"},
        description = "Enable strict mode (fail if PATH java has wrong version)",
        defaultValue = "false"
    )
    private boolean strictMode;

    @Option(
        names = {"--jvm-opts"},
        description = "JVM options (e.g., \"-Xmx4g -Xms1g\")"
    )
    private String jvmOpts;

    @Option(
        names = {"-E", "--env"},
        description = "Set environment variables (format: KEY=VALUE). Can be used multiple times."
    )
    private java.util.Map<String, String> environmentVariables;

    @Option(
        names = {"-D", "--property"},
        description = "Set Java system properties (format: KEY=VALUE). Can be used multiple times."
    )
    private java.util.Map<String, String> javaProperties;

    @Option(
        names = {"--prepend-args"},
        description = "Arguments to prepend before user arguments. Can be used multiple times."
    )
    private String prependArgs;

    @Option(
        names = {"--append-args"},
        description = "Arguments to append after user arguments. Can be used multiple times."
    )
    private String appendArgs;

    @Option(
        names = {"--artifact-name"},
        description = "Artifact name for error messages (default: extracted from JAR)"
    )
    private String artifactName;

    @Option(
        names = {"--validate-shellcheck"},
        description = "Validate launcher script with shellcheck",
        defaultValue = "false"
    )
    private boolean validateShellcheck;

    @Option(
        names = {"-f", "--force"},
        description = "Overwrite output file if it exists",
        defaultValue = "true"
    )
    private boolean force;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    private boolean verbose;

    @Option(
        names = {"--print-launcher"},
        description = "Print the launcher script to stdout and exit (does not create executable)"
    )
    private boolean printLauncher;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // Validate input JAR exists
            if (!Files.exists(inputJar)) {
                System.err.println("Error: Input JAR file does not exist: " + inputJar);
                return 1;
            }

            if (!Files.isRegularFile(inputJar)) {
                System.err.println("Error: Input path is not a file: " + inputJar);
                return 1;
            }

            // Validate JAR and extract artifact name if needed
            if (verbose) {
                System.out.println("Validating JAR file: " + inputJar);
            }

            String jarArtifactName;
            try {
                jarArtifactName = ExecJar.validateJar(inputJar);
                if (verbose) {
                    System.out.println("JAR is valid. Main-Class: " + jarArtifactName);
                }
            } catch (JarValidationException e) {
                System.err.println("Error: JAR validation failed: " + e.getMessage());
                return 1;
            }

            // Build launcher configuration
            LauncherConfig.Builder configBuilder = LauncherConfig.builder()
                .minJavaVersion(minJavaVersion)
                .strictMode(strictMode)
                .validateWithShellcheck(validateShellcheck);

            if (maxJavaVersion != null) {
                configBuilder.maxJavaVersion(maxJavaVersion);
            }

            if (jvmOpts != null) {
                configBuilder.jvmOpts(jvmOpts);
            }
            if (artifactName != null) {
                configBuilder.artifactName(artifactName);
            } else {
                configBuilder.artifactName(ExecJar.extractArtifactName(inputJar));
            }

            // Add environment variables
            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                configBuilder.environmentVariables(environmentVariables);
            }

            // Add Java properties
            if (javaProperties != null && !javaProperties.isEmpty()) {
                configBuilder.javaProperties(javaProperties);
            }

            // Add prepend args
            if (prependArgs != null && !prependArgs.isEmpty()) {
                configBuilder.prependArgs(prependArgs);
            }

            // Add append args
            if (appendArgs != null && !appendArgs.isEmpty()) {
                configBuilder.appendArgs(appendArgs);
            }

            LauncherConfig config = configBuilder.build();

            // Handle --print-launcher option
            if (printLauncher) {
                if (verbose) {
                    System.err.println("Rendering launcher script...");
                }

                // Validate with shellcheck if requested
                if (validateShellcheck) {
                    if (verbose) {
                        System.err.println("Validating launcher script with shellcheck...");
                    }
                    var result = ExecJar.validateLauncherScript(config);
                    if (result != null && !result.isSuccess()) {
                        System.err.println("Error: Shellcheck validation failed");
                        System.err.println(result.getOutputAsString());
                        return 2;
                    }
                    if (result != null && result.hasWarnings() && verbose) {
                        System.err.println("Shellcheck warnings:");
                        System.err.println(result.getOutputAsString());
                    }
                }

                // Render and print launcher script
                me.bechberger.execjar.core.impl.LauncherRenderer renderer =
                    new me.bechberger.execjar.core.impl.LauncherRenderer();
                String launcherScript = renderer.render(config);
                System.out.print(launcherScript);

                return 0;
            }

            // Determine output file
            Path output = outputFile;
            if (output == null) {
                // Default: same directory as input, artifact name without .jar
                String defaultName = artifactName != null ? artifactName : ExecJar.extractArtifactName(inputJar);
                output = inputJar.getParent() != null
                    ? inputJar.getParent().resolve(defaultName)
                    : Paths.get(defaultName);
            }

            // Check if output file exists
            if (Files.exists(output)) {
                if (!force) {
                    System.err.println("Error: Output file already exists: " + output);
                    System.err.println("Use -f/--force to overwrite");
                    return 1;
                }
                if (verbose) {
                    System.out.println("Overwriting existing file: " + output);
                }
            }

            // Warn but allow overwriting input file if --force is used
            if (inputJar.toAbsolutePath().equals(output.toAbsolutePath())) {
                if (!force) {
                    System.err.println("Error: Cannot overwrite input JAR file");
                    System.err.println("Please specify a different output file with -o/--output");
                    System.err.println("Or use -f/--force to overwrite (warning: this will replace the JAR!)");
                    return 1;
                }
                if (verbose) {
                    System.err.println("WARNING: Overwriting input JAR file!");
                }
            }

            // Display configuration if verbose
            if (verbose) {
                System.out.println("\nConfiguration:");
                System.out.println("  Input JAR: " + inputJar);
                System.out.println("  Output file: " + output);
                System.out.println("  Artifact name: " + config.artifactName());
                System.out.println("  Min Java version: " + config.minJavaVersion());
                if (config.maxJavaVersion() != null) {
                    System.out.println("  Max Java version: " + config.maxJavaVersion());
                }
                System.out.println("  Strict mode: " + config.strictMode());
                if (config.jvmOpts() != null) {
                    System.out.println("  Default JVM opts: " + config.jvmOpts());
                }
                System.out.println("  Validate shellcheck: " + config.validateWithShellcheck());
                System.out.println();
            }

            // Check shellcheck availability if validation requested
            if (validateShellcheck) {
                if (verbose) {
                    System.out.print("Checking shellcheck availability... ");
                }
                boolean available = ExecJar.isShellcheckAvailable();
                if (verbose) {
                    System.out.println(available ? "available" : "not available (will download)");
                }
            }

            // Create executable
            if (verbose) {
                System.out.println("Creating executable...");
            }

            ExecJar.create(inputJar, output, config);

            // Success
            System.out.println("✓ Created executable: " + output.toAbsolutePath());

            if (verbose) {
                long size = Files.size(output);
                System.out.println("  Size: " + formatSize(size));
                System.out.println("  Executable: " + Files.isExecutable(output));
            }

            return 0;

        } catch (JarValidationException e) {
            System.err.println("Error: JAR validation failed");
            System.err.println(e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;

        } catch (ShellCheckValidationException e) {
            System.err.println("Error: Shellcheck validation failed");
            System.err.println(e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 2;

        } catch (IOException e) {
            System.err.println("Error: I/O operation failed");
            System.err.println(e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 3;

        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid configuration");
            System.err.println(e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 4;

        } catch (Exception e) {
            System.err.println("Error: Unexpected error occurred");
            System.err.println(e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 99;
        }
    }

    /**
     * Format file size in human-readable format.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}