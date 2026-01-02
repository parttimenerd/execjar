package me.bechberger.it;

public class ConfigApp {
    public static void main(String[] args) {
        System.out.println("ConfigApp is running!");
        System.out.println("Java version: " + System.getProperty("java.version"));

        // Print the configured test property
        String testProperty = System.getProperty("test.property", "NOT_SET");
        System.out.println("test.property=" + testProperty);

        // Print max memory to verify -Xmx setting
        long maxMemMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.println("Max memory: " + maxMemMB + "MB");

        // Print arguments to verify default args
        System.out.println("Arguments: " + String.join(", ", args));

        System.exit(0);
    }
}