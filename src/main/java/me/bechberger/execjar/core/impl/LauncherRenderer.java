package me.bechberger.execjar.core.impl;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.internal.Files;
import me.bechberger.execjar.core.LauncherConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the launcher script from the Handlebars template.
 */
public class LauncherRenderer {

    private static final String TEMPLATE_PATH = "/launcher.sh.hbs";
    private final Template template;

    public LauncherRenderer() throws IOException {
        this.template = loadTemplate();
    }

    /**
     * Renders the launcher script with the given configuration.
     *
     * @param config the launcher configuration
     * @return the rendered launcher script
     * @throws IOException if rendering fails
     */
    public String render(LauncherConfig config) throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("minJavaVersion", config.minJavaVersion());
        context.put("maxJavaVersion", config.maxJavaVersion());
        context.put("strictMode", config.strictMode());

        context.put("jvmOpts", config.jvmOpts());
        context.put("artifactName", config.artifactName());

        // Add environment variables
        context.put("hasEnvironmentVariables", !config.environmentVariables().isEmpty());
        Map<String, String> escapedEnvVars = new HashMap<>();
        for (Map.Entry<String, String> entry : config.environmentVariables().entrySet()) {
            String value = entry.getValue();
            escapedEnvVars.put(entry.getKey(), ShellEscaper.escapeForDoubleQuotes(value));
        }
        context.put("environmentVariables", escapedEnvVars);

        // Add Java properties
        context.put("hasJavaProperties", !config.javaProperties().isEmpty());
        Map<String, String> escapedJavaProps = new HashMap<>();
        for (Map.Entry<String, String> entry : config.javaProperties().entrySet()) {
            String value = entry.getValue();
            escapedJavaProps.put(entry.getKey(), ShellEscaper.escapeForDoubleQuotes(value));
        }
        context.put("javaProperties", escapedJavaProps);

        // Add prepend and append args as simple strings
        if (config.prependArgs() != null && !config.prependArgs().trim().isEmpty()) {
            context.put("hasPrependArgs", true);
            context.put("prependArgs", config.prependArgs());
        } else {
            context.put("hasPrependArgs", false);
            context.put("prependArgs", "");
        }

        if (config.appendArgs() != null && !config.appendArgs().trim().isEmpty()) {
            context.put("hasAppendArgs", true);
            context.put("appendArgs", config.appendArgs());
        } else {
            context.put("hasAppendArgs", false);
            context.put("appendArgs", "");
        }

        String rendered = template.apply(context);

        // Ensure it ends with exactly one newline
        if (!rendered.endsWith("\n")) {
            rendered += "\n";
        } else if (rendered.endsWith("\n\n")) {
            // Remove extra newlines
            while (rendered.endsWith("\n\n")) {
                rendered = rendered.substring(0, rendered.length() - 1);
            }
        }

        return rendered;
    }

    /**
     * Loads the launcher template from the classpath.
     *
     * @return the compiled template
     * @throws IOException if the template cannot be loaded
     */
    private Template loadTemplate() throws IOException {
        InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH);
        if (is == null) {
            throw new IOException("Launcher template not found: " + TEMPLATE_PATH);
        }

        return new Handlebars().compileInline(Files.read(is, StandardCharsets.UTF_8));
    }

}