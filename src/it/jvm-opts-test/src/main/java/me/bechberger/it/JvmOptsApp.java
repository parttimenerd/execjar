package me.bechberger.it;

public class JvmOptsApp {
    public static void main(String[] args) {
        System.out.println("JvmOptsApp is running!");

        // Print system properties that should be set via jvmOpts
        String appName = System.getProperty("app.name", "NOT_SET");
        String appVersion = System.getProperty("app.version", "NOT_SET");
        String appTitle = System.getProperty("app.title", "NOT_SET");
        System.out.println("app.name=" + appName);
        System.out.println("app.version=" + appVersion);
        System.out.println("app.title=" + appTitle);

        // Print max memory to verify -Xmx256m
        long maxMemMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.println("max.memory=" + maxMemMB + "MB");

        // Print arguments that should be set via defaultArgs
        System.out.println("Arguments: " + String.join(", ", args));

        // Verify specific expected values
        boolean success = true;

        if (!"JvmOptsTest".equals(appName)) {
            System.err.println("ERROR: app.name should be 'JvmOptsTest', got: " + appName);
            success = false;
        }

        if (!"1.0".equals(appVersion)) {
            System.err.println("ERROR: app.version should be '1.0', got: " + appVersion);
            success = false;
        }

        if (!"MyApplication".equals(appTitle)) {
            System.err.println("ERROR: app.title should be 'MyApplication', got: " + appTitle);
            success = false;
        }

        // Check memory is around 256MB (allow some variance for JVM overhead)
        if (maxMemMB < 200 || maxMemMB > 300) {
            System.err.println("ERROR: max memory should be around 256MB, got: " + maxMemMB + "MB");
            success = false;
        }

        // Check default arguments are present
        boolean hasMode = false;
        boolean hasVerbose = false;
        for (String arg : args) {
            if ("--mode".equals(arg) || arg.startsWith("--mode")) hasMode = true;
            if ("--verbose".equals(arg)) hasVerbose = true;
        }

        if (!hasMode) {
            System.err.println("ERROR: Expected --mode argument in default args");
            success = false;
        }

        if (!hasVerbose) {
            System.err.println("ERROR: Expected --verbose argument in default args");
            success = false;
        }

        if (success) {
            System.out.println("SUCCESS: All JVM options and default arguments are correct!");
            System.exit(0);
        } else {
            System.err.println("FAILURE: Some configuration values were incorrect");
            System.exit(1);
        }
    }
}