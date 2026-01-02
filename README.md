# execjar

[![CI](https://github.com/parttimenerd/execjar/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/execjar/actions/workflows/ci.yml)

Turn your JAR (with dependencies) into a single self-executing file.
No more `java -jar` — just run it directly on Linux and macOS.

Features
* Single self-executing file combining a small POSIX `sh` launcher with a runnable fat JAR
* Automatic Java binary discovery (JAVA_HOME, PATH, common install locations)
* Min/max Java version checking and clear error messages when requirements aren't met
* Works on Linux and macOS
* Deterministic, reproducible output suitable for packaging

Maven plugin
------------

The `execjar-maven-plugin` is intended to run in the `package` phase and produce an additional executable artifact (it does not replace the original JAR).

**Important:** This plugin requires a fat JAR (uber JAR with all dependencies included). You must configure `maven-assembly-plugin` or `maven-shade-plugin` **before** the execjar plugin in your `pom.xml`.

### Complete example with maven-assembly-plugin

The following is the relevant part of a `pom.xml` (see [example-project/pom.xml](example-project/pom.xml)) that creates a fat JAR with dependencies:

```xml
<groupId>com.example</groupId>
<artifactId>hello-execjar</artifactId>
<!-- ... -->
<build>
  <plugins>
    <!-- Step 1: Create fat JAR with dependencies -->
    <plugin>
      <artifactId>maven-assembly-plugin</artifactId>
      <version>3.6.0</version>
      <configuration>
        <descriptorRefs>
          <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
        <archive>
          <manifest>
            <mainClass>com.example.HelloExecJar</mainClass>
          </manifest>
        </archive>
      </configuration>
      <executions>
        <execution>
          <id>make-assembly</id>
          <phase>package</phase>
          <goals>
            <goal>single</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

    <!-- Step 2: Create executable from fat JAR -->
    <plugin>
      <groupId>me.bechberger</groupId>
      <artifactId>execjar-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <goals>
            <goal>execjar</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

You can then run the resulting executable (e.g., `target/hello-execjar`) directly:

```bash
> ./target/hello-execjar
Hello from Exec Jar!
```

### Configuration options

Basic options:

| Option | Description | Default |
|--------|-------------|---------|
| `minJavaVersion` | Minimum required Java major version (e.g., `17`) | Auto-detected from `maven.compiler.release` or `maven.compiler.target` |
| `maxJavaVersion` | Maximum allowed Java major version (e.g., `21`) | No upper limit |
| `strictMode` | Require exact Java version match (use only `java` from `PATH`) | `false` |
| `skip` | Skip plugin execution | `false` |
| `validateWithShellcheck` | Validate launcher script with shellcheck | `false` |

<details><summary>Additional Options</summary>

#### JAR and output configuration

| Option | Description | Default |
|--------|-------------|---------|
| `jarFile` | Path to input JAR file | Auto-detected: `${project.build.directory}/${project.build.finalName}-${classifier}.jar` |
| `classifier` | Classifier for the fat JAR | `jar-with-dependencies` |
| `outputFile` | Output path for the executable | `${project.build.directory}/${project.artifactId}` |

#### JVM and application configuration

| Option | Description | Default |
|--------|-------------|---------|
| `jvmOpts` | JVM options embedded in the launcher (e.g., `-Xmx1g -XX:+UseG1GC`) | None |
| `javaProperties` | Java system properties as key-value pairs (converted to `-Dkey=value`) | None |
| `environmentVariables` | Environment variables to set before launching | None |
| `prependArgs` | Arguments prepended before user-provided arguments | None |
| `appendArgs` | Arguments appended after user-provided arguments | None |

The plugin looks for `${project.build.finalName}-${classifier}.jar` by default (e.g., `myapp-1.0-jar-with-dependencies.jar`).
</details>

<details><summary>Advanced Configuration Example</summary>

For a comprehensive example with Java properties, environment variables, and argument handling, see the [advanced-config-test](src/it/advanced-config-test/pom.xml) integration test:

```xml
<plugin>
  <groupId>me.bechberger</groupId>
  <artifactId>execjar-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals>
        <goal>execjar</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <minJavaVersion>17</minJavaVersion>
    <maxJavaVersion>21</maxJavaVersion>
    
    <!-- JVM options -->
    <jvmOpts>-Xmx1g -XX:+UseG1GC</jvmOpts>
    
    <!-- Java system properties (supports spaces and special characters) -->
    <javaProperties>
      <app.name>My Application</app.name>
      <app.config>/path/to/my app/config</app.config>
      <app.message>Hello World &amp; Friends</app.message>
    </javaProperties>
    
    <!-- Environment variables -->
    <environmentVariables>
      <MY_APP_NAME>My Application</MY_APP_NAME>
      <MY_APP_PATH>/opt/my app/bin</MY_APP_PATH>
    </environmentVariables>
    
    <!-- Arguments: prepended before user args, appended after -->
    <prependArgs>--config "/path/to/config.yml"</prependArgs>
    <appendArgs>--log-level info</appendArgs>
  </configuration>
</plugin>
```

With this configuration, running `./myapp --user-arg` results in:
```bash
java -Xmx1g -XX:+UseG1GC \
  -Dapp.name="My Application" \
  -Dapp.config="/path/to/my app/config" \
  -Dapp.message="Hello World & Friends" \
  -jar myapp \
  --config "/path/to/config.yml" \
  --user-arg \
  --log-level info
```
</details>

Command-line Tool
-----------------

Download the latest JAR or executable from the [releases page](https://github.com/parttimenerd/execjar/releases) or use [JBang](https://www.jbang.dev/):

```bash
jbang execjar@parttimenerd/execjar
```

### Basic usage

Create an executable from a runnable fat JAR:

```bash
./execjar input.jar -o myapp
```

### CLI options

```
Usage: execjar <jarFile> [options]

Arguments:
  <jarFile>                   Input JAR file (must be a runnable fat JAR)

Options:
  -o, --output <path>         Output path for the executable
                              Default: <jar-name-without-.jar>
  
  --min-java-version <major>  Minimum required Java version (e.g., 17)
                              Default: auto-detected from JAR manifest
  
  --max-java-version <major>  Maximum allowed Java version (e.g., 21)
                              Default: no upper limit
  
  --strict-mode              Require exact Java version match
                              (use only 'java' from PATH)
                              Default: false
  
  --jvm-opts <options>        JVM options embedded in launcher
                              Example: "-Xmx1g -XX:+UseG1GC"
  
  -D, --property <key=value>  Java system property (can be repeated)
                              Example: -D app.name="My App"
  
  -E, --env <key=value>       Environment variable (can be repeated)
                              Example: -E MY_VAR="value"
  
  --prepend-args <args>       Arguments prepended before user args
                              Example: --prepend-args "--config /etc/app.conf"
  
  --append-args <args>        Arguments appended after user args
                              Example: --append-args "--log-level debug"
    
  --validate-shellcheck       Validate generated launcher with shellcheck
                              Default: false
  
  -h, --help                  Show this help message
  -v, --version               Show version information
```

### Examples

```bash
# Simple executable with Java version requirement
./execjar myapp.jar -o myapp --min-java-version 17

# With JVM options and Java properties
./execjar myapp.jar -o myapp \
  --jvm-opts "-Xmx2g -XX:+UseZGC" \
  -D app.name="My Application" \
  -D app.config="/etc/myapp/config.yml"

# With environment variables and default arguments
./execjar myapp.jar -o myapp \
  -E MY_APP_HOME="/opt/myapp" \
  --prepend-args "--config /etc/myapp.conf" \
  --append-args "--verbose"

# Strict mode for containerized environments
./execjar myapp.jar -o myapp \
  --min-java-version 21 \
  --max-java-version 21 \
  --strict-mode

# Validate launcher script with shellcheck
./execjar myapp.jar -o myapp --validate-shellcheck
```

### What the tool produces

The tool creates a single executable file with:
* POSIX `sh` launcher prepended to the JAR bytes
* Embedded configuration (Java version requirements, JVM options, etc.)
* File layout: `#!/usr/bin/env sh` script + original JAR data
* Executable permissions set automatically
* No external dependencies required


Implementation details
----------------------

The sections below describe internal implementation and launcher behavior (useful for debugging and advanced configuration).

Background: JAR files are ZIP files, and prepending data to a ZIP file is allowed as ZIP readers ignore leading bytes before the ZIP header
as they look for the End of Central Directory (EOCD) record at the end of the file which contains offsets to all entries
(see [ZIP specification](https://libzip.org/specifications/appnote_iz.txt)).
This property enables the creation of self-executing JAR files by adding a launcher script at the beginning.

Launcher highlights

* Shebang: `#!/usr/bin/env sh` — fully POSIX `sh` compatible
* Template variables embedded into the launcher:
  - `artifactName` — used for human-friendly error messages
  - `minJavaVersion`, `maxJavaVersion` — version constraints
  - `strictMode` — whether to search for Java or only use PATH
  - `jvmOpts` — JVM options
  - `javaProperties` — Java system properties (-D flags)
  - `environmentVariables` — environment variables to export
  - `prependArgs`, `appendArgs` — default arguments
* Debug mode: set `EXECJAR_DEBUG=1` to print selected Java executable, detected version, resolved JVM options and the final `exec` command
* JVM option parsing mirrors async-profiler semantics:
  - `-D*`, `-X*`, `-agent*` → passed to JVM
  - `-J<opt>` → treated as JVM option (leading `-J` stripped)
  - First non-JVM option terminates JVM-option parsing and remaining args are passed to the application

Java discovery and version handling

The launcher searches for a usable `java` in this order:
1. `$JAVA_HOME/bin/java`
2. `java` on `PATH`
3. `/etc/alternatives/java`
4. Common JVM installation locations:
   - macOS: `/Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java`
   - Linux: `/usr/lib/jvm/*/bin/java`

For each candidate the launcher:
* Parses `java -version` output to extract the major version (supports Java 11+)
* Rejects EA builds and versions outside the configured range
* If multiple valid JREs are found, picks the highest version
* Exits with a clear error if none are valid

### Input validation and behavior

* Input must be a valid JAR with `META-INF/MANIFEST.MF` containing `Main-Class`
* The tool validates the JAR and fails fast on missing `Main-Class` or invalid JAR
* Output is created next to the input JAR by default and is marked executable
* The tool overwrites the output by default (unless input == output)

Development
-----------

### Core and CLI Tests

```bash
mvn clean package
```

### Running integration tests

The project includes comprehensive JUnit-based integration tests for both the Maven plugin and CLI tool:

```bash
# Run all tests (Maven plugin integration tests + unit tests)
./run-tests.sh

# Run only Maven plugin integration tests
./run-tests.sh --maven-only

# Run only unit tests (including CLI tests)
./run-tests.sh --unit-only

# Or use Maven directly
mvn clean install -DskipTests  # Install plugin first
mvn verify -Prun-its           # Run integration tests
```

Contributing & Support
----------------------

Please file issues and feature requests on the project's GitHub issue tracker:
https://github.com/parttimenerd/execjar/issues

Contributions are welcome — please follow typical PR practices (small focused changes, tests for new behavior).

License
-------

Apache License 2.0

Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors