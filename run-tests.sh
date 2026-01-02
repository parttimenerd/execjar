#!/bin/bash

# Test runner script for execjar integration tests
# This script runs both Maven plugin tests (JUnit-based) and CLI tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Color output helpers
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

print_debug() {
    echo -e "${BLUE}⚙ $1${NC}"
}

# Parse arguments
RUN_MAVEN_ITS=true
RUN_UNIT_TESTS=true
CLEAN=false
DEBUG=false
DEBUG_PORT=5005
TEST_PATTERN=""
SKIP_INSTALL=false
MAVEN_OPTS=""

show_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Test Categories:"
    echo "  - Maven Integration Tests: Build test projects with maven-invoker-plugin,"
    echo "    then verify with MavenPluginIT (runs with failsafe)"
    echo "  - Unit Tests: All unit tests including CLIIntegrationTest (runs with surefire)"
    echo ""
    echo "Options:"
    echo "  --maven-only         Run only Maven integration tests (invoker + MavenPluginIT)"
    echo "  --unit-only          Run only unit tests (including CLIIntegrationTest)"
    echo "  --test <pattern>     Run specific test(s) matching pattern (e.g., 'CLIIntegrationTest#testBasic')"
    echo "  --clean              Clean build before running tests"
    echo "  --skip-install       Skip 'mvn install' step (use if plugin already installed)"
    echo "  --debug              Enable JDWP debugging on port 5005 (suspend=y)"
    echo "  --debug-port <port>  Set custom debug port (default: 5005)"
    echo "  --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                          # Run all tests"
    echo "  $0 --test CLIIntegrationTest                # Run all CLI integration tests"
    echo "  $0 --test CLIIntegrationTest#testBasic      # Run specific test method"
    echo "  $0 --test 'LauncherRenderer*'               # Run all LauncherRenderer tests"
    echo "  $0 --debug --test CLIIntegrationTest        # Debug CLI integration tests"
    echo "  $0 --debug-port 8000 --debug                # Debug on port 8000"
    echo "  $0 --unit-only --skip-install               # Quick unit test run"
    echo "  $0 --maven-only                             # Only Maven integration tests"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --maven-only)
            RUN_UNIT_TESTS=false
            shift
            ;;
        --unit-only|--cli-only)
            # --cli-only is kept for backwards compatibility
            RUN_MAVEN_ITS=false
            shift
            ;;
        --test)
            TEST_PATTERN="$2"
            shift 2
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --skip-install)
            SKIP_INSTALL=true
            shift
            ;;
        --debug)
            DEBUG=true
            shift
            ;;
        --debug-port)
            DEBUG_PORT="$2"
            shift 2
            ;;
        --help|-h)
            show_help
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Setup debug options
if [ "$DEBUG" = true ]; then
    MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:$DEBUG_PORT"
    export MAVEN_OPTS
    print_debug "JDWP debugging enabled on port $DEBUG_PORT (suspend=y)"
    print_debug "Attach your debugger before tests start"
    echo ""
fi

echo "========================================="
echo "execjar Integration Test Runner"
echo "========================================="
echo ""

cd "$PROJECT_ROOT"

# Clean if requested
if [ "$CLEAN" = true ]; then
    print_info "Cleaning project..."
    mvn clean -q
fi

# Install the plugin first (unless skipped)
if [ "$SKIP_INSTALL" = false ]; then
    print_info "Installing execjar plugin locally..."
    if mvn clean install -DskipTests -q; then
        print_success "Plugin installed successfully"
    else
        print_error "Failed to install plugin"
        exit 1
    fi
    echo ""
else
    print_info "Skipping plugin installation (--skip-install)"
    echo ""
fi

# Determine which tests to run based on pattern
if [ -n "$TEST_PATTERN" ]; then
    print_info "Running tests matching pattern: $TEST_PATTERN"

    # Determine if this is a Maven IT test or unit/integration test
    # Maven ITs are in src/it/, unit tests are in src/test/
    if [[ "$TEST_PATTERN" == *"IT"* ]] || [[ "$TEST_PATTERN" == *"integration"* ]]; then
        # Assume Maven IT
        if [ "$RUN_MAVEN_ITS" = true ]; then
            echo "========================================="
            echo "Maven Plugin Integration Tests (Pattern: $TEST_PATTERN)"
            echo "========================================="
            echo ""

            print_info "Building and running Maven integration tests..."
            if mvn invoker:install invoker:run -Prun-its -Dinvoker.test="$TEST_PATTERN"; then
                print_success "Maven integration tests passed"
            else
                print_error "Maven integration tests failed"
                exit 1
            fi
            echo ""
        fi
    else
        # Assume JUnit test
        echo "========================================="
        echo "Running JUnit Test: $TEST_PATTERN"
        echo "========================================="
        echo ""

        if mvn test -Dtest="$TEST_PATTERN"; then
            print_success "Tests passed"
        else
            print_error "Tests failed"
            exit 1
        fi
        echo ""
    fi

    # Exit early when running specific test pattern
    echo "========================================="
    print_success "Test pattern completed!"
    echo "========================================="
    exit 0
fi

# Run Maven integration tests
if [ "$RUN_MAVEN_ITS" = true ]; then
    echo "========================================="
    echo "Maven Plugin Integration Tests (JUnit)"
    echo "========================================="
    echo ""

    print_info "Building integration test projects with maven-invoker-plugin..."
    if mvn invoker:install invoker:run -Prun-its 2>&1 | tee /tmp/invoker.log; then
        print_success "Test projects built successfully"
    else
        print_error "Failed to build test projects"
        cat /tmp/invoker.log
        exit 1
    fi

    echo ""
    print_info "Running JUnit verification tests (MavenPluginIT)..."
    if mvn failsafe:integration-test failsafe:verify -Prun-its; then
        print_success "Maven integration tests passed"
    else
        print_error "Maven integration tests failed"
        exit 1
    fi
    echo ""
fi

# Run all unit tests (surefire excludes *IT.java files automatically via pom.xml)
# This includes CLIIntegrationTest and all other unit tests
if [ "$RUN_UNIT_TESTS" = true ]; then
    echo "========================================="
    echo "Unit Tests (mvn test)"
    echo "========================================="
    echo ""

    print_info "Running all unit tests (including CLIIntegrationTest)..."
    if mvn test; then
        print_success "Unit tests passed"
    else
        print_error "Unit tests failed"
        exit 1
    fi
    echo ""
fi

echo "========================================="
print_success "All tests passed!"
echo "========================================="