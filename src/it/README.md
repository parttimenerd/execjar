# Integration Tests for execjar

This directory contains JUnit-based integration tests for the execjar Maven plugin and CLI tool.

## Test Structure

### Maven Plugin Integration Tests

Located in `src/it/`, these test projects are built using `maven-invoker-plugin` and verified using JUnit tests in `MavenPluginIT.java`.

#### Test Projects:

1. **basic-maven-plugin** - Basic fat JAR with maven-assembly-plugin
   - Tests: Default configuration, auto-detection of minJavaVersion from compiler config
   - Verifies: Executable creation, permissions, shebang, execution

2. **custom-config** - Custom configuration options
   - Tests: Custom output file, JVM opts, default args, version constraints
   - Verifies: Configuration embedding, default JVM opts/args execution

3. **shade-plugin** - Using maven-shade-plugin instead of assembly
   - Tests: Compatibility with maven-shade-plugin
   - Verifies: Explicit JAR file path configuration

### CLI Integration Tests

Located in `src/test/java/me/bechberger/execjar/test/CLIIntegrationTest.java`.

Tests the command-line interface by invoking `Main.main()` directly.

#### Test Coverage:
- Basic CLI execution
- Min/max Java version configuration
- JVM options embedding
- Default arguments
- Invalid JAR handling
- Help and version options
- Verbose mode
- Force overwrite

## Running Tests

### Run All Tests

```bash
./run-tests.sh
```

### Run Only Maven Plugin Tests

```bash
./run-tests.sh --maven-only
```

### Run Only CLI Tests

```bash
./run-tests.sh --cli-only
```

### Clean Build Before Tests

```bash
./run-tests.sh --clean
```

### Using Maven Directly

#### Maven Plugin Integration Tests

```bash
# Install plugin first
mvn clean install -DskipTests

# Run integration tests
mvn verify -Prun-its
```

#### CLI Tests

```bash
mvn test -Dtest=CLIIntegrationTest
```

## Test Framework

### Technologies Used

- **JUnit 5** (Jupiter) - All tests use JUnit 5
- **maven-invoker-plugin** - Builds test projects in isolation
- **maven-failsafe-plugin** - Runs integration tests (MavenPluginIT)
- **maven-surefire-plugin** - Runs unit and CLI tests

### Key Features

- **No Groovy** - All tests are pure Java/JUnit
- **Cross-platform** - Tests adapt to OS (some tests disabled on Windows)
- **Process Execution** - Tests actually run generated executables
- **Comprehensive Verification** - Tests check:
  - File existence and permissions
  - Executable structure (shebang, JAR signature)
  - Configuration embedding
  - Actual execution and output

## Adding New Tests

### Adding a Maven Plugin Test

1. Create a new directory in `src/it/your-test-name/`
2. Add a `pom.xml` with:
   - maven-assembly-plugin or maven-shade-plugin configuration
   - execjar plugin configuration
3. Add Java source files in `src/main/java/`
4. Add test methods to `MavenPluginIT.java`:

```java
@Test
public void testYourNewTest() throws IOException {
    Path projectDir = itProjectsDir.resolve("your-test-name");
    assumeProjectExists(projectDir);
    
    // Your assertions here
}
```

### Adding a CLI Test

Add test methods to `CLIIntegrationTest.java`:

```java
@Test
public void testYourFeature() throws IOException {
    Path inputJar = createTestJar("test.jar", "com.example.Main");
    Path outputExec = tempDir.resolve("output");
    
    CLIResult result = invokeCLI(
        inputJar.toString(),
        "-o", outputExec.toString(),
        "--your-option", "value"
    );
    
    assertEquals(0, result.exitCode);
    // Your assertions here
}
```

## Debugging Tests

### View Invoker Plugin Output

```bash
# Check built test projects
ls -la target/it/

# View specific test project build
cat target/it/basic-maven-plugin/build.log
```

### Run Single Test

```bash
# Single Maven integration test
mvn failsafe:integration-test -Prun-its -Dit.test=MavenPluginIT#testBasicMavenPlugin

# Single CLI test
mvn test -Dtest=CLIIntegrationTest#testBasicCLIExecution
```

### Enable Debug Output

```bash
# Maven plugin tests with debug
mvn verify -Prun-its -X

# CLI tests with debug
mvn test -Dtest=CLIIntegrationTest -X
```

## Test Requirements

- Java 17+ (project requires Java 17)
- Maven 3.9+
- Unix-like OS recommended (some tests skip on Windows)

## Continuous Integration

The tests are designed to run in CI environments:

```yaml
# Example GitHub Actions workflow
- name: Run Integration Tests
  run: ./run-tests.sh --clean
```

## Test Output

Successful test run produces:

```
=========================================
execjar Integration Test Runner
=========================================

ℹ Installing execjar plugin locally...
✓ Plugin installed successfully

=========================================
Maven Plugin Integration Tests (JUnit)
=========================================

ℹ Building integration test projects with maven-invoker-plugin...
✓ Test projects built successfully

ℹ Running JUnit verification tests...
✓ Maven integration tests passed

=========================================
CLI Integration Tests (JUnit)
=========================================

✓ CLI integration tests passed

=========================================
✓ All tests passed!
=========================================
```