package me.bechberger.execjar.core.impl;

/**
 * Utility class for escaping strings for use in shell scripts.
 * Provides methods to safely escape strings that will be embedded in shell scripts,
 * particularly for POSIX-compliant shells like bash and sh.
 */
public class ShellEscaper {

    private ShellEscaper() {
        // Utility class, no instantiation
    }

    /**
     * Escapes a string for safe use in a shell script.
     * <p>
     * Returns the string with minimal escaping. Escapes backslashes, single quotes, and double quotes.
     * This assumes the string will be used in a context where it's already properly quoted or doesn't need quoting.
     * </p>
     *
     * @param value the string to escape
     * @return the escaped string safe for embedding in a shell script
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }

        if (value.isEmpty()) {
            return "";
        }

        // Escape backslashes first, then quotes (both single and double)
        return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"");
    }

    /**
     * Escapes a string for safe use inside double quotes in a shell script.
     * <p>
     * Single quotes don't need escaping inside double quotes, so we only escape
     * backslashes and double quotes.
     * </p>
     *
     * @param value the string to escape
     * @return the escaped string safe for embedding inside double quotes
     */
    public static String escapeForDoubleQuotes(String value) {
        if (value == null) {
            return "";
        }

        if (value.isEmpty()) {
            return "";
        }

        // Inside double quotes, only backslashes and double quotes need escaping
        // Single quotes can be used literally
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
    }

}