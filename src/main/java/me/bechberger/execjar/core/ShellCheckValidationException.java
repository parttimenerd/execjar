package me.bechberger.execjar.core;

import me.bechberger.execjar.core.validation.ShellCheck;

/**
 * Exception thrown when shellcheck validation fails.
 */
public class ShellCheckValidationException extends Exception {

    private final ShellCheck.ShellCheckResult result;

    public ShellCheckValidationException(String message, ShellCheck.ShellCheckResult result) {
        super(message + "\n" + result.getOutputAsString());
        this.result = result;
    }

    public ShellCheckValidationException(ShellCheck.ShellCheckResult result) {
        this("Shellcheck validation failed", result);
    }

    public ShellCheck.ShellCheckResult getResult() {
        return result;
    }
}