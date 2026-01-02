package me.bechberger.execjar.core;

import java.util.Map;
import java.util.List;

/**
 * Configuration for the launcher script embedded in the executable.
 */
public record LauncherConfig(
    int minJavaVersion,
    Integer maxJavaVersion,
    boolean strictMode,
    String jvmOpts,
    String artifactName,
    boolean validateWithShellcheck,
    Map<String, String> environmentVariables,
    Map<String, String> javaProperties,
    String prependArgs,
    String appendArgs
) {
    /**
     * Compact constructor with validation.
     */
    public LauncherConfig {
        if (minJavaVersion < 11) {
            throw new IllegalArgumentException("Minimum Java version must be 11 or higher");
        }
        if (maxJavaVersion != null && maxJavaVersion < minJavaVersion) {
            throw new IllegalArgumentException(
                "Maximum Java version (" + maxJavaVersion + ") cannot be less than minimum version (" + minJavaVersion + ")"
            );
        }
        if (artifactName == null || artifactName.trim().isEmpty()) {
            throw new IllegalArgumentException("Artifact name cannot be null or empty");
        }
        // Make immutable copies
        environmentVariables = environmentVariables != null ? Map.copyOf(environmentVariables) : Map.of();
        javaProperties = javaProperties != null ? Map.copyOf(javaProperties) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int minJavaVersion = 11;
        private Integer maxJavaVersion;
        private boolean strictMode = false;
        private String jvmOpts;
        private String artifactName = "application";
        private boolean validateWithShellcheck = false;
        private Map<String, String> environmentVariables = new java.util.HashMap<>();
        private Map<String, String> javaProperties = new java.util.HashMap<>();
        private String prependArgs;
        private String appendArgs;

        public Builder minJavaVersion(int minJavaVersion) {
            this.minJavaVersion = minJavaVersion;
            return this;
        }

        public Builder maxJavaVersion(Integer maxJavaVersion) {
            this.maxJavaVersion = maxJavaVersion;
            return this;
        }

        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        public Builder jvmOpts(String jvmOpts) {
            this.jvmOpts = jvmOpts;
            return this;
        }

        public Builder artifactName(String artifactName) {
            this.artifactName = artifactName;
            return this;
        }

        public Builder validateWithShellcheck(boolean validateWithShellcheck) {
            this.validateWithShellcheck = validateWithShellcheck;
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables != null ? new java.util.HashMap<>(environmentVariables) : new java.util.HashMap<>();
            return this;
        }

        public Builder addEnvironmentVariable(String key, String value) {
            this.environmentVariables.put(key, value);
            return this;
        }

        public Builder javaProperties(Map<String, String> javaProperties) {
            this.javaProperties = javaProperties != null ? new java.util.HashMap<>(javaProperties) : new java.util.HashMap<>();
            return this;
        }

        public Builder addJavaProperty(String key, String value) {
            this.javaProperties.put(key, value);
            return this;
        }

        public Builder prependArgs(String prependArgs) {
            this.prependArgs = prependArgs;
            return this;
        }

        public Builder appendArgs(String appendArgs) {
            this.appendArgs = appendArgs;
            return this;
        }

        public LauncherConfig build() {
            return new LauncherConfig(
                minJavaVersion,
                maxJavaVersion,
                strictMode,
                jvmOpts,
                artifactName,
                validateWithShellcheck,
                environmentVariables,
                javaProperties,
                prependArgs,
                appendArgs
            );
        }
    }
}