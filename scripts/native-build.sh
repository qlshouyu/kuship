#!/usr/bin/env bash
# 一键 GraalVM Native Image 本地构建脚本（enable-graalvm-native，13/13 phase）。
#
# 前置条件：
#   - GraalVM 21 community 已安装（macOS: sdk install java 21.0.2-graalce; Linux: 从 graalvm.org）
#   - native-image 命令在 PATH（GraalVM 安装包默认包含；如缺失：gu install native-image）
#
# 用法：
#   bash scripts/native-build.sh           # 仅 native build
#   bash scripts/native-build.sh docker    # native build + docker image build
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/kuship-console"

# 1. 校验 GraalVM
if ! command -v native-image >/dev/null 2>&1; then
    echo "ERROR: native-image not found in PATH"
    echo "  macOS: sdk install java 21.0.2-graalce && sdk use java 21.0.2-graalce"
    echo "  Linux: download from https://graalvm.org and put 'native-image' in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F\" '{print $2}')
echo "Using Java: $JAVA_VERSION"

# 2. native build
echo ">>> Building native image (this takes 3-5 minutes on M2 / 5-8 minutes on x86)..."
mvn -B -Pnative -DskipTests package

BINARY="$REPO_ROOT/kuship-console/target/kuship-console"
if [ ! -f "$BINARY" ]; then
    echo "ERROR: native binary not produced at $BINARY"
    exit 1
fi
SIZE=$(du -h "$BINARY" | awk '{print $1}')
echo ">>> Native binary built: $BINARY ($SIZE)"

# 3. 可选 docker image build
if [ "${1:-}" = "docker" ]; then
    echo ">>> Building Docker image (kuship-console-native)..."
    cd "$REPO_ROOT/kuship-console"
    docker build -f Dockerfile.native -t kuship-console-native .
    docker images kuship-console-native --format "{{.Repository}}:{{.Tag}} {{.Size}}"
fi

echo ">>> DONE. Run with:  $BINARY"
