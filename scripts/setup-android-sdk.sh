#!/usr/bin/env bash
# Installs the Android SDK pieces FocusTag needs and writes local.properties.
# Idempotent: anything already present is skipped.
set -euo pipefail

CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
PACKAGES=("platforms;android-37.0" "build-tools;37.0.0" "platform-tools")

default_sdk_dir() {
  if [[ -d /opt/android-sdk ]]; then echo /opt/android-sdk; else echo "$HOME/android-sdk"; fi
}

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(default_sdk_dir)}}"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

install_cmdline_tools() {
  [[ -x "$SDKMANAGER" ]] && return
  echo "Installing Android command-line tools into $SDK_DIR"
  local tmp
  tmp="$(mktemp -d)"
  curl -fsSL --retry 5 --retry-delay 5 -o "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
}

package_dir() {
  echo "$SDK_DIR/${1//;//}"
}

install_packages() {
  local missing=()
  for pkg in "${PACKAGES[@]}"; do
    [[ -d "$(package_dir "$pkg")" ]] || missing+=("$pkg")
  done
  if [[ ${#missing[@]} -eq 0 ]]; then
    echo "All SDK packages already installed"
    return
  fi
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK_DIR" "${missing[@]}"
}

write_local_properties() {
  echo "sdk.dir=$SDK_DIR" > "$REPO_ROOT/local.properties"
  echo "Wrote $REPO_ROOT/local.properties (sdk.dir=$SDK_DIR)"
}

mkdir -p "$SDK_DIR"
install_cmdline_tools
install_packages
write_local_properties
