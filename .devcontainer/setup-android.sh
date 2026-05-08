#!/bin/bash
set -e

SDK_DIR="$HOME/android-sdk"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "══════════════════════════════════════════════════════════"
echo "  DeepLoader — Android SDK Setup for GitHub Codespaces"
echo "  Developer: Sonu Verma"
echo "══════════════════════════════════════════════════════════"

# Install Android SDK command-line tools
echo "[1/4] Installing Android SDK command-line tools..."
mkdir -p "$SDK_DIR/cmdline-tools"
wget -q "$TOOLS_URL" -O /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
mv /tmp/cmdline-tools-extract/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extract

# Accept all licenses non-interactively
echo "[2/4] Accepting SDK licenses..."
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

# Install required SDK packages
echo "[3/4] Installing SDK packages (platform-tools, android-35, build-tools)..."
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

# Verify installation
echo "[4/4] Verifying installation..."
echo "Java version:"
java -version
echo ""
echo "SDK Manager version:"
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --version
echo ""
echo "Installed packages:"
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --list_installed 2>/dev/null || true

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  ✅ Android SDK setup complete!"
echo "  SDK Location: $SDK_DIR"
echo "  Ready to build DeepLoader."
echo "══════════════════════════════════════════════════════════"
