package me.bechberger.it;

import java.util.Arrays;
import java.util.List;

/**
 * Test application that validates javaProperties, environmentVariables,
 * prependArgs, and appendArgs with spaces and special characters.
 */
public class AdvancedConfigApp {
    public static void main(String[] args) {
        System.out.println("AdvancedConfigApp is running!");
        System.out.println("Arguments received: " + Arrays.toString(args));
        
        boolean success = true;
        
        // Test Java Properties
        success &= testJavaProperties();
        
        // Test Environment Variables
        success &= testEnvironmentVariables();
        
        // Test Arguments (prepend + user + append)
        success &= testArguments(args);
        
        if (success) {
            System.out.println("======================");
            System.out.println("SUCCESS: All configuration values are correct!");
            System.out.println("======================");
            System.exit(0);
        } else {
            System.err.println("======================");
            System.err.println("FAILURE: Some configuration values were incorrect");
            System.err.println("======================");
            System.exit(1);
        }
    }
    
    private static boolean testJavaProperties() {
        System.out.println("\n--- Testing Java Properties ---");
        boolean success = true;
        
        // Test properties with spaces
        success &= checkProperty("app.name", "My Application");
        success &= checkProperty("app.version", "1.0.0");
        success &= checkProperty("app.path", "/path/to/my app/config");
        
        // Test properties with special characters
        success &= checkProperty("app.message", "Hello World & Friends");
        success &= checkProperty("app.quote", "It's working!");  // Single quotes don't need escaping in double quotes
        success &= checkProperty("app.backslash", "C:\\Users\\Test\\file.txt");
        
        return success;
    }
    
    private static boolean testEnvironmentVariables() {
        System.out.println("\n--- Testing Environment Variables ---");
        boolean success = true;
        
        // Test environment variables with spaces
        success &= checkEnvVar("MY_APP_NAME", "Advanced Test App");
        success &= checkEnvVar("MY_APP_PATH", "/opt/my app/bin");
        
        // Test environment variables with quotes and special characters
        success &= checkEnvVar("MY_VAR_WITH_SPECIAL", "Value & More Text");
        success &= checkEnvVar("MY_VAR_APOSTROPHE", "It's working!");  // Single quotes don't need escaping in double quotes

        return success;
    }
    
    private static boolean testArguments(String[] args) {
        System.out.println("\n--- Testing Arguments ---");
        
        // Expected structure: prependArgs + user args + appendArgs
        // prependArgs: --config "/path/to/my config/file.yml" "--name=Test App"
        // appendArgs: --log-level debug "--message=Hello World!"
        // Note: "--name=Test App" and "--message=Hello World!" are each a single argument

        List<String> argList = Arrays.asList(args);
        boolean success = true;
        
        // Check minimum length: 2 prepend + 1 name + 2 append = 5 args minimum
        if (argList.size() < 5) {
            System.err.println("ERROR: Expected at least 5 arguments (2 prepend + 1 name + 2 append), got: " + argList.size());
            return false;
        }
        
        // Check first 2 args (prependArgs)
        success &= checkArgument(argList, 0, "--config", "prepend arg 1");
        success &= checkArgument(argList, 1, "/path/to/my config/file.yml", "prepend arg 2 (with spaces)");

        // Check last 2 args (appendArgs)
        // Last 2 args should be: --log-level, debug
        // But AFTER that comes --message=Hello World!
        // So we need to check for these in order from the end
        int lastIdx = argList.size() - 1;
        success &= checkArgument(argList, lastIdx, "--message=Hello World!", "append arg 3 (with =)");
        success &= checkArgument(argList, lastIdx - 1, "debug", "append arg 2");
        success &= checkArgument(argList, lastIdx - 2, "--log-level", "append arg 1");

        // Check that --name=Test App is in the middle somewhere (position 2 when no user args)
        boolean hasNameArg = argList.contains("--name=Test App");
        if (hasNameArg) {
            int nameIdx = argList.indexOf("--name=Test App");
            System.out.println("✓ Found --name=Test App at position " + nameIdx);
        } else {
            System.err.println("✗ Missing expected argument: --name=Test App");
            success = false;
        }

        // Print info about all args
        System.out.println("All arguments: " + argList);

        return success;
    }
    
    private static boolean checkProperty(String key, String expected) {
        String actual = System.getProperty(key);
        boolean matches = expected.equals(actual);
        
        if (matches) {
            System.out.println("✓ " + key + " = " + actual);
        } else {
            System.err.println("✗ " + key + " FAILED");
            System.err.println("  Expected: " + expected);
            System.err.println("  Got:      " + actual);
        }
        
        return matches;
    }
    
    private static boolean checkEnvVar(String key, String expected) {
        String actual = System.getenv(key);
        boolean matches = expected.equals(actual);
        
        if (matches) {
            System.out.println("✓ " + key + " = " + actual);
        } else {
            System.err.println("✗ " + key + " FAILED");
            System.err.println("  Expected: " + expected);
            System.err.println("  Got:      " + actual);
        }
        
        return matches;
    }
    
    private static boolean checkArgument(List<String> args, int index, String expected, String description) {
        if (index < 0 || index >= args.size()) {
            System.err.println("✗ Argument " + index + " (" + description + ") is out of bounds");
            return false;
        }
        
        String actual = args.get(index);
        boolean matches = expected.equals(actual);
        
        if (matches) {
            System.out.println("✓ args[" + index + "] (" + description + ") = " + actual);
        } else {
            System.err.println("✗ args[" + index + "] (" + description + ") FAILED");
            System.err.println("  Expected: " + expected);
            System.err.println("  Got:      " + actual);
        }
        
        return matches;
    }
}