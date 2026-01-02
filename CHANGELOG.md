# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions CI workflow for testing on multiple OS (Linux, macOS) and Java versions (11, 17, 21, 23)
- CI job to build and test the example project on Linux and macOS with Java 17 and 21
- Shell compatibility test script (`test-shell-compatibility.sh`) to verify POSIX compliance across sh, bash, dash, and zsh
- Minimal example project in `example-project/` demonstrating plugin usage
- Comprehensive documentation for Maven plugin configuration options in README
- CLI options documentation with examples in README
- Note about Maven parameter validation warnings in README
- Example project README with usage instructions and explanations

### Changed
- Fixed Maven plugin parameter name from `javaOpts` to `jvmOpts` to match CLI and documentation
- Improved tagline in README to be more concise and user-focused
- Updated README with corrected CLI option names (`-D`/`--property` instead of `--java-property`)
- Fixed launcher template variables documentation to reflect actual implementation
- Updated test runner documentation to reflect `--unit-only` flag instead of `--cli-only`
- Maven surefire plugin now excludes `*IT.java` files (integration tests run with failsafe)
- `run-tests.sh` script now properly separates unit tests from Maven integration tests
- `release.py` now uses `run-tests.sh` for comprehensive testing instead of `mvn test`
- `release.py` now updates version in `example-project/pom.xml` during releases
- `release.py` now references execjar instead of jstall (fixed copy-paste artifacts)

### Fixed
- Integration test `testAdvancedConfigWithUserArgs` now has correct expectations
- Integration test `jvm-opts-test` now uses correct parameter name `jvmOpts`
- Apostrophe escaping in XML configuration (use unescaped within double quotes)
- Cross-shell compatibility verified for sh, bash, dash, and zsh

### Documentation
- Fixed truncated "Build and test" section header in README
- Added explanation of why `<executions>` block is needed in Maven plugin configuration
- Created EXECUTIONS-EXPLAINED.md in example project for detailed explanation

## [0.1.0] - 2026-01-02

- Initial implementation