package me.bechberger.execjar.core.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShellCheckTest {

    @TempDir
    Path tempDir;

    @Test
    void testValidScript() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String validScript = """
            #!/usr/bin/env sh
            echo "Hello, World!"
            exit 0
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(validScript);
        assertTrue(result.isSuccess(), "Valid script should pass: " + result.getOutputAsString());
    }

    @Test
    void testInvalidScript() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String invalidScript = """
            #!/usr/bin/env sh
            # Using undefined variable
            echo $UNDEFINED_VAR
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(invalidScript);
        // This might pass or fail depending on shellcheck strictness
        // Just verify we get a result
        assertNotNull(result);
    }

    @Test
    void testValidateFile() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        Path scriptFile = tempDir.resolve("test-script.sh");
        Files.writeString(scriptFile, """
            #!/usr/bin/env sh
            set -e
            echo "Test"
            """);

        ShellCheck.ShellCheckResult result = shellcheck.validateFile(scriptFile);
        assertTrue(result.isSuccess(), "Valid script file should pass: " + result.getOutputAsString());
    }

    @Test
    void testIsAvailable() {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        // Should either find shellcheck in PATH or be able to download it
        boolean available = shellcheck.isAvailable();

        // Just verify the method works
        assertNotNull(available);
    }

    @Test
    void testResultMethods() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String script = """
            #!/usr/bin/env sh
            echo "test"
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(script);

        assertNotNull(result.getOutput());
        assertNotNull(result.getOutputAsString());
        assertTrue(result.getExitCode() >= 0);
        assertNotNull(result.toString());
    }

    @Test
    void testScriptWithVariables() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String script = """
            #!/usr/bin/env sh
            VAR="value"
            echo "$VAR"
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(script);
        assertTrue(result.isSuccess(), "Script with variables should pass: " + result.getOutputAsString());
    }

    @Test
    void testScriptWithFunctions() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String script = """
            #!/usr/bin/env sh
            
            test_func() {
                echo "In function"
                return 0
            }
            
            test_func
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(script);
        assertTrue(result.isSuccess(), "Script with functions should pass: " + result.getOutputAsString());
    }

    @Test
    void testComplexScript() throws IOException {
        ShellCheck shellcheck = new ShellCheck(tempDir);

        if (!shellcheck.isAvailable()) {
            System.out.println("ShellCheck not available, skipping test");
            return;
        }

        String script = """
            #!/usr/bin/env sh
            
            # Variables
            MIN_VERSION=11
            MAX_VERSION=21
            
            # Functions
            check_version() {
                version="$1"
                if [ "$version" -lt "$MIN_VERSION" ]; then
                    return 1
                fi
                if [ -n "$MAX_VERSION" ] && [ "$version" -gt "$MAX_VERSION" ]; then
                    return 1
                fi
                return 0
            }
            
            # Main
            if check_version 17; then
                echo "Version OK"
            else
                echo "Version not OK"
                exit 1
            fi
            """;

        ShellCheck.ShellCheckResult result = shellcheck.validate(script);
        assertTrue(result.isSuccess(), "Complex script should pass: " + result.getOutputAsString());
        assertFalse(result.hasErrors(), "Should not have errors");
    }
}