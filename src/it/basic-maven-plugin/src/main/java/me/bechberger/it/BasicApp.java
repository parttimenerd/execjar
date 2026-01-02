package me.bechberger.it;

public class BasicApp {
    public static void main(String[] args) {
        System.out.println("BasicApp is running!");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println("Arguments: " + String.join(", ", args));

        // Print if any test properties are set
        String testProp = System.getProperty("test.property");
        if (testProp != null) {
            System.out.println("test.property=" + testProp);
        }

        // Print max memory
        long maxMemMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.println("Max memory: " + maxMemMB + "MB");

        System.exit(0);
    }
}