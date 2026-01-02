package me.bechberger.execjar.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LauncherConfigTest {

    @Test
    void testBuilderDefaults() {
        LauncherConfig config = LauncherConfig.builder().build();

        assertEquals(11, config.minJavaVersion());
        assertNull(config.maxJavaVersion());
        assertFalse(config.strictMode());
        assertNull(config.jvmOpts());
        assertNull(config.prependArgs());
        assertEquals("application", config.artifactName());
    }

    @Test
    void testBuilderWithAllOptions() {
        LauncherConfig config = LauncherConfig.builder()
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .strictMode(true)
            .jvmOpts("-Xmx2g")
            .prependArgs("--help")
            .artifactName("my-app")
            .build();

        assertEquals(17, config.minJavaVersion());
        assertEquals(21, config.maxJavaVersion());
        assertTrue(config.strictMode());
        assertEquals("-Xmx2g", config.jvmOpts());
        assertEquals("--help", config.prependArgs());
        assertEquals("my-app", config.artifactName());
    }

    @Test
    void testValidationMinJavaVersionTooLow() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .minJavaVersion(8)
                .build();
        });
    }

    @Test
    void testValidationMinJavaVersion10() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .minJavaVersion(10)
                .build();
        });
    }

    @Test
    void testValidationMinJavaVersion11() {
        LauncherConfig config = LauncherConfig.builder()
            .minJavaVersion(11)
            .build();

        assertEquals(11, config.minJavaVersion());
    }

    @Test
    void testValidationMaxJavaVersionLessThanMin() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .minJavaVersion(17)
                .maxJavaVersion(11)
                .build();
        });
    }

    @Test
    void testValidationMaxJavaVersionEqualToMin() {
        LauncherConfig config = LauncherConfig.builder()
            .minJavaVersion(17)
            .maxJavaVersion(17)
            .build();

        assertEquals(17, config.minJavaVersion());
        assertEquals(17, config.maxJavaVersion());
    }

    @Test
    void testValidationNullArtifactName() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .artifactName(null)
                .build();
        });
    }

    @Test
    void testValidationEmptyArtifactName() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .artifactName("")
                .build();
        });
    }

    @Test
    void testValidationWhitespaceArtifactName() {
        assertThrows(IllegalArgumentException.class, () -> {
            LauncherConfig.builder()
                .artifactName("   ")
                .build();
        });
    }

    @Test
    void testRecordEquality() {
        LauncherConfig config1 = LauncherConfig.builder()
            .artifactName("app")
            .minJavaVersion(17)
            .build();

        LauncherConfig config2 = LauncherConfig.builder()
            .artifactName("app")
            .minJavaVersion(17)
            .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testRecordInequality() {
        LauncherConfig config1 = LauncherConfig.builder()
            .artifactName("app1")
            .minJavaVersion(17)
            .build();

        LauncherConfig config2 = LauncherConfig.builder()
            .artifactName("app2")
            .minJavaVersion(17)
            .build();

        assertNotEquals(config1, config2);
    }

    @Test
    void testRecordToString() {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("test-app")
            .minJavaVersion(17)
            .maxJavaVersion(21)
            .build();

        String str = config.toString();
        assertTrue(str.contains("test-app"));
        assertTrue(str.contains("17"));
        assertTrue(str.contains("21"));
    }

    @Test
    void testWithNullMaxJavaVersion() {
        LauncherConfig config = LauncherConfig.builder()
            .minJavaVersion(17)
            .maxJavaVersion(null)
            .build();

        assertNull(config.maxJavaVersion());
    }

    @Test
    void testWithNulljvmOpts() {
        LauncherConfig config = LauncherConfig.builder()
            .jvmOpts(null)
            .build();

        assertNull(config.jvmOpts());
    }

    @Test
    void testWithNullDefaultArgs() {
        LauncherConfig config = LauncherConfig.builder()
            .prependArgs(null)
            .build();

        assertNull(config.prependArgs());
    }

    @Test
    void testStrictModeFalse() {
        LauncherConfig config = LauncherConfig.builder()
            .strictMode(false)
            .build();

        assertFalse(config.strictMode());
    }

    @Test
    void testStrictModeTrue() {
        LauncherConfig config = LauncherConfig.builder()
            .strictMode(true)
            .build();

        assertTrue(config.strictMode());
    }

    @Test
    void testHighJavaVersions() {
        LauncherConfig config = LauncherConfig.builder()
            .minJavaVersion(21)
            .maxJavaVersion(25)
            .build();

        assertEquals(21, config.minJavaVersion());
        assertEquals(25, config.maxJavaVersion());
    }

    @Test
    void testComplexJvmOpts() {
        String opts = "-Xmx4g -Xms1g -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom";
        LauncherConfig config = LauncherConfig.builder()
            .jvmOpts(opts)
            .build();

        assertEquals(opts, config.jvmOpts());
    }

    @Test
    void testComplexDefaultArgs() {
        String args = "--config /path/to/config.yml --verbose --port 8080";
        LauncherConfig config = LauncherConfig.builder()
            .prependArgs(args)
            .build();

        assertEquals(args, config.prependArgs());
    }

    @Test
    void testArtifactNameWithSpecialCharacters() {
        LauncherConfig config = LauncherConfig.builder()
            .artifactName("my-app_v2.0")
            .build();

        assertEquals("my-app_v2.0", config.artifactName());
    }
}