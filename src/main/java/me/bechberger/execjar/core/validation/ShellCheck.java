package me.bechberger.execjar.core.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Downloads, caches, and runs shellcheck on shell scripts.
 */
public class ShellCheck {

    private static final String SHELLCHECK_VERSION = "v0.10.0";
    private static final String CACHE_DIR_PROPERTY = "shellcheck.cache.dir";

    private final Path cacheDir;
    private Path shellcheckBinary;

    public ShellCheck() {
        String cacheDirStr = System.getProperty(CACHE_DIR_PROPERTY);
        if (cacheDirStr != null) {
            this.cacheDir = Paths.get(cacheDirStr);
        } else {
            // Default: ~/.cache/execjar/shellcheck
            String home = System.getProperty("user.home");
            this.cacheDir = Paths.get(home, ".cache", "execjar", "shellcheck");
        }
    }

    public ShellCheck(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Validates a shell script using shellcheck.
     * Downloads and caches shellcheck if not already present.
     *
     * @param scriptContent the shell script content to validate
     * @return validation result
     * @throws IOException if shellcheck cannot be run
     */
    public ShellCheckResult validate(String scriptContent) throws IOException {
        ensureShellCheckAvailable();

        // Write script to temporary file
        Path tempScript = Files.createTempFile("shellcheck-", ".sh");
        try {
            Files.writeString(tempScript, scriptContent);
            return runShellCheck(tempScript);
        } finally {
            Files.deleteIfExists(tempScript);
        }
    }

    /**
     * Validates a shell script file using shellcheck.
     *
     * @param scriptFile the shell script file to validate
     * @return validation result
     * @throws IOException if shellcheck cannot be run
     */
    public ShellCheckResult validateFile(Path scriptFile) throws IOException {
        ensureShellCheckAvailable();
        return runShellCheck(scriptFile);
    }

    /**
     * Checks if shellcheck is available (either cached or in PATH).
     *
     * @return true if shellcheck is available
     */
    public boolean isAvailable() {
        try {
            ensureShellCheckAvailable();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensures shellcheck is available, downloading if necessary.
     */
    private void ensureShellCheckAvailable() throws IOException {
        // First check if it's already in PATH
        if (isShellCheckInPath()) {
            shellcheckBinary = Paths.get("shellcheck");
            return;
        }

        // Check cache
        Path cachedBinary = getCachedBinaryPath();
        if (Files.exists(cachedBinary) && Files.isExecutable(cachedBinary)) {
            shellcheckBinary = cachedBinary;
            return;
        }

        // Download and cache
        downloadShellCheck();
        shellcheckBinary = cachedBinary;
    }

    /**
     * Checks if shellcheck is available in PATH.
     */
    private boolean isShellCheckInPath() {
        try {
            Process process = new ProcessBuilder("shellcheck", "--version")
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Gets the path where shellcheck should be cached.
     */
    private Path getCachedBinaryPath() {
        return cacheDir.resolve(SHELLCHECK_VERSION).resolve("shellcheck");
    }

    /**
     * Downloads shellcheck and extracts it to the cache directory.
     */
    private void downloadShellCheck() throws IOException {
        String os = detectOS();
        String arch = detectArch();
        String platform = os + "." + arch;

        String downloadUrl = String.format(
            "https://github.com/koalaman/shellcheck/releases/download/%s/shellcheck-%s.%s.tar.xz",
            SHELLCHECK_VERSION, SHELLCHECK_VERSION, platform
        );

        System.out.println("Downloading shellcheck from: " + downloadUrl);

        // Create cache directory
        Path versionDir = cacheDir.resolve(SHELLCHECK_VERSION);
        Files.createDirectories(versionDir);

        // Download
        Path tarFile = versionDir.resolve("shellcheck.tar.xz");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to download shellcheck: HTTP " + response.statusCode());
            }

            Files.copy(response.body(), tarFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }

        // Extract
        extractTarXz(tarFile, versionDir);

        // Move binary to expected location
        Path extractedBinary = versionDir.resolve("shellcheck-" + SHELLCHECK_VERSION).resolve("shellcheck");
        Path targetBinary = getCachedBinaryPath();
        if (Files.exists(extractedBinary)) {
            Files.move(extractedBinary, targetBinary, StandardCopyOption.REPLACE_EXISTING);
            makeExecutable(targetBinary);
        } else {
            throw new IOException("Shellcheck binary not found after extraction");
        }

        // Cleanup
        Files.deleteIfExists(tarFile);
        Path extractedDir = versionDir.resolve("shellcheck-" + SHELLCHECK_VERSION);
        if (Files.exists(extractedDir)) {
            deleteDirectory(extractedDir);
        }

        System.out.println("Shellcheck cached at: " + targetBinary);
    }

    /**
     * Extracts a .tar.xz file.
     */
    private void extractTarXz(Path tarXzFile, Path outputDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tar", "-xJf", tarXzFile.toString(), "-C", outputDir.toString());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
                throw new IOException("Failed to extract tar.xz: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrupted", e);
        }
    }

    /**
     * Runs shellcheck on a script file.
     */
    private ShellCheckResult runShellCheck(Path scriptFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            shellcheckBinary.toString(),
            "-s", "sh",  // POSIX sh
            "-f", "gcc", // GCC-style output format
            scriptFile.toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            List<String> output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines()
                .collect(Collectors.toList());

            int exitCode = process.waitFor();

            return new ShellCheckResult(exitCode == 0, exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Shellcheck execution interrupted", e);
        }
    }

    /**
     * Detects the operating system.
     */
    private String detectOS() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        } else if (os.contains("linux")) {
            return "linux";
        } else {
            throw new IOException("Unsupported OS: " + os);
        }
    }

    /**
     * Detects the CPU architecture.
     */
    private String detectArch() throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x86_64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        } else {
            throw new IOException("Unsupported architecture: " + arch);
        }
    }

    /**
     * Makes a file executable.
     */
    private void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // Not POSIX, try generic method
            file.toFile().setExecutable(true, false);
        }
    }

    /**
     * Recursively deletes a directory.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> -a.compareTo(b)) // Reverse order for deletion
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    /**
     * Result of a shellcheck validation.
     */
    public static class ShellCheckResult {
        private final boolean success;
        private final int exitCode;
        private final List<String> output;

        public ShellCheckResult(boolean success, int exitCode, List<String> output) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = new ArrayList<>(output);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getExitCode() {
            return exitCode;
        }

        public List<String> getOutput() {
            return new ArrayList<>(output);
        }

        public String getOutputAsString() {
            return String.join("\n", output);
        }

        public boolean hasWarnings() {
            return output.stream().anyMatch(line ->
                line.contains("warning:") || line.contains("note:"));
        }

        public boolean hasErrors() {
            return output.stream().anyMatch(line -> line.contains("error:"));
        }

        @Override
        public String toString() {
            return "ShellCheckResult{" +
                   "success=" + success +
                   ", exitCode=" + exitCode +
                   ", output=" + output.size() + " lines" +
                   '}';
        }
    }
}