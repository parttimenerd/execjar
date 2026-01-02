package me.bechberger.execjar.core.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShellEscaperTest {

    @ParameterizedTest
    @MethodSource("provideEscapeBasicCases")
    void testEscapeBasicCases(String input, String expected, String description) {
        assertEquals(expected, ShellEscaper.escape(input), description);
    }

    static Stream<Arguments> provideEscapeBasicCases() {
        return Stream.of(
            Arguments.of("", "", "Empty string"),
            Arguments.of("hello", "hello", "Simple word"),
            Arguments.of("hello world", "hello world", "String with space"),
            Arguments.of("it's", "it\\'s", "String with single quote"),
            Arguments.of("don't", "don\\'t", "Another string with single quote"),
            Arguments.of("single", "single", "Word 'single' without quotes"),
            Arguments.of("test'test", "test\\'test", "Single quote in middle"),
            Arguments.of("a'b'c", "a\\'b\\'c", "Multiple single quotes"),
            Arguments.of("-Xmx512m", "-Xmx512m", "JVM option"),
            Arguments.of("app.message=test", "app.message=test", "Property without special chars"),
            Arguments.of("app.message='test value'", "app.message=\\'test value\\'", "Property with single-quoted value"),
            Arguments.of("app.message=\"test value\"", "app.message=\\\"test value\\\"", "Property with double-quoted value"),
            Arguments.of("/usr/local/bin", "/usr/local/bin", "Path"),
            Arguments.of("key=value", "key=value", "Simple key-value"),
            Arguments.of("test\\path", "test\\\\path", "Backslash in path"),
            Arguments.of("say \"hi\"", "say \\\"hi\\\"", "Double quotes in middle")
        );
    }

    @Test
    void testEscapeNull() {
        assertEquals("", ShellEscaper.escape(null));
    }

    @Test
    void testEscapeEmpty() {
        assertEquals("", ShellEscaper.escape(""));
    }

    @ParameterizedTest
    @MethodSource("provideSpecialCharacterCases")
    void testEscapeSpecialCharacters(String input, String expected, String description) {
        assertEquals(expected, ShellEscaper.escape(input), description);
    }

    static Stream<Arguments> provideSpecialCharacterCases() {
        return Stream.of(
            Arguments.of("$HOME", "$HOME", "Dollar sign is not escaped"),
            Arguments.of("`whoami`", "`whoami`", "Backticks are not escaped"),
            Arguments.of("$(date)", "$(date)", "Command substitution is not escaped"),
            Arguments.of("test; rm -rf /", "test; rm -rf /", "Semicolon is not escaped"),
            Arguments.of("test && echo hi", "test && echo hi", "Logical AND is not escaped"),
            Arguments.of("test | grep x", "test | grep x", "Pipe is not escaped"),
            Arguments.of("test\nline", "test\nline", "Newline is preserved"),
            Arguments.of("tab\there", "tab\there", "Tab is preserved"),
            Arguments.of("\\", "\\\\", "Backslash is escaped"),
            Arguments.of("path\\to\\file", "path\\\\to\\\\file", "Multiple backslashes are escaped"),
            Arguments.of("it's", "it\\'s", "Single quote is escaped")
        );
    }



    static Stream<Arguments> provideSplitRespectingQuotesCases() {
        return Stream.of(
            Arguments.of(
                "",
                new String[]{},
                "Empty string should return empty array"
            ),
            Arguments.of(
                "   ",
                new String[]{},
                "Whitespace only should return empty array"
            ),
            Arguments.of(
                "simple",
                new String[]{"simple"},
                "Single word without quotes"
            ),
            Arguments.of(
                "one two three",
                new String[]{"one", "two", "three"},
                "Multiple words without quotes"
            ),
            Arguments.of(
                "-Xmx512m",
                new String[]{"-Xmx512m"},
                "JVM option without spaces"
            ),
            Arguments.of(
                "-Xmx512m -Xms256m",
                new String[]{"-Xmx512m", "-Xms256m"},
                "Multiple JVM options"
            ),
            Arguments.of(
                "-Dapp.message=\"Hello World\"",
                new String[]{"-Dapp.message=Hello World"},
                "Property with double-quoted value containing space"
            ),
            Arguments.of(
                "-Dapp.name='Test App'",
                new String[]{"-Dapp.name=Test App"},
                "Property with single-quoted value containing space"
            ),
            Arguments.of(
                "-Xmx512m -Dapp.message=\"Hello World\" -Dapp.name='Test App'",
                new String[]{"-Xmx512m", "-Dapp.message=Hello World", "-Dapp.name=Test App"},
                "Mixed JVM options with quoted values"
            ),
            Arguments.of(
                "-Dvalue=\"a b c\"",
                new String[]{"-Dvalue=a b c"},
                "Double quotes with multiple spaces"
            ),
            Arguments.of(
                "\"quoted string\" unquoted",
                new String[]{"quoted string", "unquoted"},
                "Mix of quoted and unquoted arguments"
            ),
            Arguments.of(
                "  -Xmx512m   -Xms256m  ",
                new String[]{"-Xmx512m", "-Xms256m"},
                "Extra whitespace should be trimmed"
            ),
            Arguments.of(
                "-Dpath=\"/usr/local/bin\"",
                new String[]{"-Dpath=/usr/local/bin"},
                "Quoted path"
            ),
            Arguments.of(
                "-Dmsg=\"it's working\"",
                new String[]{"-Dmsg=it's working"},
                "Double quoted value with single quote inside"
            ),
            Arguments.of(
                "-Dmsg='he said \"hi\"'",
                new String[]{"-Dmsg=he said \"hi\""},
                "Single quoted value with double quotes inside"
            )
        );
    }

    @Test
    void testEscapeDoesNotAddQuotes() {
        // Verify that escape never adds quotes
        String input = "safe-value";
        String escaped = ShellEscaper.escape(input);
        assertEquals("safe-value", escaped);

        String withSpaces = "value with spaces";
        String escapedSpaces = ShellEscaper.escape(withSpaces);
        assertEquals("value with spaces", escapedSpaces);
    }

    @ParameterizedTest
    @MethodSource("provideEscapeForDoubleQuotesCases")
    void testEscapeForDoubleQuotes(String input, String expected, String description) {
        assertEquals(expected, ShellEscaper.escapeForDoubleQuotes(input), description);
    }

    static Stream<Arguments> provideEscapeForDoubleQuotesCases() {
        return Stream.of(
            Arguments.of("", "", "Empty string"),
            Arguments.of("hello", "hello", "Simple word"),
            Arguments.of("hello world", "hello world", "String with space"),
            Arguments.of("it's", "it's", "Single quote should NOT be escaped inside double quotes"),
            Arguments.of("don't", "don't", "Another single quote should NOT be escaped"),
            Arguments.of("It\\'s working!", "It\\\\'s working!", "Backslash before single quote should be escaped (backslash gets escaped)"),
            Arguments.of("say \"hi\"", "say \\\"hi\\\"", "Double quotes should be escaped"),
            Arguments.of("test\\path", "test\\\\path", "Backslash should be escaped"),
            Arguments.of("C:\\Users\\Test", "C:\\\\Users\\\\Test", "Windows path backslashes should be escaped"),
            Arguments.of("Hello & Friends", "Hello & Friends", "Ampersand should not be escaped"),
            Arguments.of("value with spaces", "value with spaces", "Spaces should not be escaped")
        );
    }

    @Test
    void testEscapeForDoubleQuotesNull() {
        assertEquals("", ShellEscaper.escapeForDoubleQuotes(null));
    }

    @Test
    void testEscapeForDoubleQuotesEmpty() {
        assertEquals("", ShellEscaper.escapeForDoubleQuotes(""));
    }
}