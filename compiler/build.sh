#!/usr/bin/env bash
#
# Build script for The RAT compiler
# Supports:
#   - JVM (JAR with dependencies) — works on all platforms
#   - Kotlin Native executables — macOS, Linux, Windows
#
# Usage:
#   ./build.sh                  # Build JVM JAR (default)
#   ./build.sh jvm              # Build JVM JAR
#   ./build.sh all              # Build JVM + all native targets
#   ./build.sh macos            # Build macOS native (x64 + arm64)
#   ./build.sh linux            # Build Linux native (x64 + arm64)
#   ./build.sh windows          # Build Windows native (x64)
#   ./build.sh host             # Build for host platform only
#   ./build.sh <task>           # Build specific Gradle task
#
# Artifacts:
#   build/jar/the-rat.jar        — JVM fat JAR
#   build/bin/<target>/ratc      — Native executable
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
BIN_DIR="${BUILD_DIR}/bin"
JAR_DIR="${BUILD_DIR}/jar"
GRADLE_W="${SCRIPT_DIR}/gradlew"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${BLUE}[STEP]${NC}  $*"; }

# -------------------------------------------------------
# Detect host platform
# -------------------------------------------------------
detect_host() {
    local os arch
    os=$(uname -s | tr '[:upper:]' '[:lower:]')
    arch=$(uname -m)

    case "$os" in
        darwin)
            case "$arch" in
                x86_64)  echo "macosX64" ;;
                arm64)   echo "macosArm64" ;;
                *)       log_error "Unsupported macOS architecture: $arch"; exit 1 ;;
            esac
            ;;
        linux)
            case "$arch" in
                x86_64)  echo "linuxX64" ;;
                aarch64) echo "linuxArm64" ;;
                *)       log_error "Unsupported Linux architecture: $arch"; exit 1 ;;
            esac
            ;;
        msys*|mingw*|cygwin)
            echo "mingwX64"
            ;;
        *)
            log_error "Unsupported host platform: $os ($arch)"
            exit 1
            ;;
    esac
}

# -------------------------------------------------------
# Prepare Gradle wrapper
# -------------------------------------------------------
prepare_gradle() {
    if [[ ! -f "$GRADLE_W" ]]; then
        log_info "Gradle wrapper not found. Generating..."
        if command -v gradle &>/dev/null; then
            gradle wrapper --gradle-version 8.5
        else
            log_error "Gradle not found. Please install Gradle."
            exit 1
        fi
    fi
    chmod +x "$GRADLE_W" 2>/dev/null || true
}

# -------------------------------------------------------
# Build JVM JAR
# -------------------------------------------------------
build_jvm() {
    log_info "Building JVM JAR ..."
    mkdir -p "$JAR_DIR"

    if "$GRADLE_W" fatJar --console=plain 2>&1 | tail -20; then
        # Copy the fat JAR to our build directory
        local jar_file
        jar_file=$(find "${SCRIPT_DIR}/build/libs" -name "the-rat-*.jar" -type f | head -1)
        if [[ -n "$jar_file" && -f "$jar_file" ]]; then
            cp "$jar_file" "${JAR_DIR}/the-rat.jar"
            log_info "JVM JAR built: ${JAR_DIR}/the-rat.jar"
            return 0
        else
            log_error "JAR not found in build/libs/ directory"
            return 1
        fi
    else
        log_error "Gradle JVM build failed"
        return 1
    fi
}

# -------------------------------------------------------
# Build a single native target
# -------------------------------------------------------
build_native_target() {
    local gradle_task="$1"
    local label="$2"

    log_info "Building: ${label} (${gradle_task})"

    if "$GRADLE_W" "$gradle_task" --console=plain 2>&1 | tee "${BUILD_DIR}/build-${label}.log"; then
        log_info "SUCCESS: ${label}"
        return 0
    else
        log_error "FAILED: ${label} (see ${BUILD_DIR}/build-${label}.log)"
        return 1
    fi
}

# -------------------------------------------------------
# Get executable path for a native target
# -------------------------------------------------------
get_executable_path() {
    local target="$1"
    case "$target" in
        macosX64|macosArm64)
            echo "${BUILD_DIR}/composeExecutableFiles/macos/${target}/releaseExecutable/ratc"
            ;;
        linuxX64|linuxArm64)
            echo "${BUILD_DIR}/composeExecutableFiles/${target}/releaseExecutable/ratc"
            ;;
        mingwX64)
            echo "${BUILD_DIR}/composeExecutableFiles/mingw/x64/releaseExecutable/ratc.exe"
            ;;
        *)
            echo ""
            ;;
    esac
}

# -------------------------------------------------------
# Collect built executables and copy to bin/
# -------------------------------------------------------
collect_artifacts() {
    local target="$1"
    local output_dir="${BIN_DIR}/${target}"
    mkdir -p "$output_dir"

    local exec_path
    exec_path=$(get_executable_path "$target")

    if [[ -n "$exec_path" && -f "$exec_path" ]]; then
        local basename
        basename=$(basename "$exec_path")
        cp "$exec_path" "$output_dir/$basename"
        chmod +x "$output_dir/$basename" 2>/dev/null || true
        log_info "  -> ${output_dir}/${basename}"
    else
        log_warn "  Executable not found at: ${exec_path}"
    fi
}

# -------------------------------------------------------
# Print usage
# -------------------------------------------------------
print_usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] [TARGET]

Targets:
  all               Build JVM + all native platforms
  jvm               Build JVM JAR (default)
  macos             Build macOS native executables (x64 + arm64)
  linux             Build Linux native executables (x64 + arm64)
  windows           Build Windows native executable (x64)
  host              Build for host platform only
  <task>            Build specific Gradle task

Options:
  -h, --help        Show this help message
  -c, --clean       Clean build directory before building

Examples:
  $(basename "$0")                  # Build JVM JAR
  $(basename "$0") jvm              # Build JVM JAR
  $(basename "$0") all              # Build JVM + all native targets
  $(basename "$0") macos            # Build macOS native binaries
  $(basename "$0") --clean all      # Clean and build everything
EOF
}

# -------------------------------------------------------
# Main
# -------------------------------------------------------
main() {
    local target="jvm"
    local clean=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                print_usage
                exit 0
                ;;
            -c|--clean)
                clean=true
                shift
                ;;
            all|jvm|macos|linux|windows|host)
                target="$1"
                shift
                ;;
            *)
                if [[ -n "${1:-}" ]]; then
                    target="$1"
                fi
                shift
                ;;
        esac
    done

    log_info "The RAT Compiler — Build Script"
    log_info "---------------------------------"

    # Clean if requested
    if $clean; then
        log_info "Cleaning build directory..."
        rm -rf "${BUILD_DIR}"
        if [[ -f "$GRADLE_W" ]]; then
            "$GRADLE_W" clean --console=plain 2>&1 | tail -3 || true
        fi
    fi

    # Create output directories
    mkdir -p "${BIN_DIR}"
    mkdir -p "${JAR_DIR}"

    local -a native_tasks=()
    local -a native_labels=()
    local build_jvm=false
    local build_native=false

    case "$target" in
        jvm)
            build_jvm=true
            ;;
        all)
            build_jvm=true
            build_native=true
            native_tasks=(
                "linkRatcReleaseExecutableMacosX64"
                "linkRatcReleaseExecutableMacosArm64"
                "linkRatcReleaseExecutableLinuxX64"
                "linkRatcReleaseExecutableLinuxArm64"
                "linkRatcReleaseExecutableMingwX64"
            )
            native_labels=(
                "macos-x64"
                "macos-arm64"
                "linux-x64"
                "linux-arm64"
                "windows-x64"
            )
            ;;
        macos)
            build_native=true
            native_tasks=(
                "linkRatcReleaseExecutableMacosX64"
                "linkRatcReleaseExecutableMacosArm64"
            )
            native_labels=(
                "macos-x64"
                "macos-arm64"
            )
            ;;
        linux)
            build_native=true
            native_tasks=(
                "linkRatcReleaseExecutableLinuxX64"
                "linkRatcReleaseExecutableLinuxArm64"
            )
            native_labels=(
                "linux-x64"
                "linux-arm64"
            )
            ;;
        windows)
            build_native=true
            native_tasks=("linkRatcReleaseExecutableMingwX64")
            native_labels=("windows-x64")
            ;;
        host)
            build_native=true
            local host_target
            host_target=$(detect_host)
            log_info "Host platform detected: ${host_target}"
            native_tasks+=("linkRatcReleaseExecutable${host_target}")
            native_labels+=("${host_target}")
            ;;
        *)
            # Try as a direct Gradle task (native)
            build_native=true
            native_tasks+=("$target")
            native_labels+=("$target")
            ;;
    esac

    local success_count=0
    local fail_count=0

    # Build JVM
    if $build_jvm; then
        if build_jvm; then
            ((success_count++)) || true
        else
            ((fail_count++)) || true
        fi
    fi

    # Build Native
    if $build_native; then
        if [[ ! -f "$GRADLE_W" ]]; then
            prepare_gradle
        fi

        for i in "${!native_tasks[@]}"; do
            local task="${native_tasks[$i]}"
            local label="${native_labels[$i]}"

            if build_native_target "$task" "$label"; then
                # Collect artifact
                local target_name
                case "$label" in
                    macos-x64)        target_name="macosX64" ;;
                    macos-arm64)      target_name="macosArm64" ;;
                    linux-x64)        target_name="linuxX64" ;;
                    linux-arm64)      target_name="linuxArm64" ;;
                    windows-x64)      target_name="mingwX64" ;;
                    *)                target_name="$label" ;;
                esac
                collect_artifacts "$target_name"
                ((success_count++)) || true
            else
                ((fail_count++)) || true
            fi
        done
    fi

    # Summary
    echo ""
    log_info "Build Summary"
    log_info "-------------"
    log_info "Success: ${success_count}"
    if [[ $fail_count -gt 0 ]]; then
        log_error "Failed: ${fail_count}"
    fi

    # List artifacts
    if [[ $fail_count -eq 0 ]]; then
        log_info "Artifacts:"
        if [[ -f "${JAR_DIR}/the-rat.jar" ]]; then
            log_info "  JVM:    ${JAR_DIR}/the-rat.jar"
        fi
        find "${BIN_DIR}" -type f \( -name "ratc" -o -name "ratc.exe" \) 2>/dev/null | sort | while read -r f; do
            log_info "  Native: ${f#${SCRIPT_DIR}/}"
        done
    fi

    if [[ $fail_count -gt 0 ]]; then
        exit 1
    fi
}

main "$@"
