package me.bechberger.execjar.core;

/**
 * Exception thrown when JAR validation fails.
 */
public class JarValidationException extends Exception {
    public JarValidationException(String message) {
        super(message);
    }

    public JarValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}