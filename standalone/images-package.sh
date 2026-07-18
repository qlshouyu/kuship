#!/bin/sh
#
# 根据 standalone/k3s-version.env 中声明的 k3s 版本，从 k3s 官方 release 拉取
# 该版本对应的 k3s-images.txt 镜像清单，按当前架构生成 k3s-images-<arch>.tar.zst。
# 镜像 tag 不在脚本中硬编码，确保离线包与 standalone/Dockerfile 中嵌入的 k3s
# 二进制版本始终一致。
#
# 用法：
#   ./standalone/images-package.sh                       # 自动识别架构
#   ARCH=linux/arm64 ./standalone/images-package.sh      # 强制指定架构
#   K3S_IMAGES_TXT_URL=https://internal/x.txt ...        # 覆盖清单 URL（内网镜像源）

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

########################################
# 1. 加载 K3S_VERSION（单一真相）
########################################
ENV_FILE="${SCRIPT_DIR}/k3s-version.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} 不存在；请先创建并写入 K3S_VERSION=<tag>" >&2
    exit 1
fi

# shellcheck disable=SC1090
. "${ENV_FILE}"

if [ -z "${K3S_VERSION:-}" ]; then
    echo "ERROR: K3S_VERSION not set in ${ENV_FILE}" >&2
    exit 1
fi

echo "==> resolving k3s version: ${K3S_VERSION}"

########################################
# 1b. 加载镜像源覆盖表（可选，见 k3s-image-mirrors.env）
########################################
MIRRORS_ENV="${SCRIPT_DIR}/k3s-image-mirrors.env"
K3S_IMAGE_MIRRORS=""
if [ -f "${MIRRORS_ENV}" ]; then
    # shellcheck disable=SC1090
    . "${MIRRORS_ENV}"
    if [ -n "${K3S_IMAGE_MIRRORS:-}" ]; then
        MIRROR_COUNT="$(echo "${K3S_IMAGE_MIRRORS}" | awk 'NF>=2 && $1 !~ /^#/ {c++} END{print c+0}')"
        echo "==> loaded ${MIRROR_COUNT} image mirror override(s) from $(basename "${MIRRORS_ENV}")"
    fi
fi

# 在覆盖表中查找某原始镜像对应的镜像源；无匹配返回空串
mirror_source_for() {
    [ -z "${K3S_IMAGE_MIRRORS:-}" ] && return 0
    echo "${K3S_IMAGE_MIRRORS}" | awk -v t="$1" 'NF>=2 && $1==t {print $2; exit}'
}

########################################
# 2. 检测架构
########################################
if [ -z "${ARCH:-}" ]; then
    machine="$(uname -m)"
    case "${machine}" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        armv7l)         ARCH="linux/arm/v7" ;;
        *)              ARCH="linux/${machine}" ;;
    esac
fi
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
echo "==> target platform: ${ARCH} (archive suffix: ${ARCH_TAG})"

########################################
# 3. 必备工具检查
########################################
require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: 需要命令 '$1'，请先安装：$2" >&2
        exit 1
    fi
}
require_cmd curl "macOS 自带；Linux 用包管理器安装 curl"
require_cmd docker "请安装并启动 Docker / Docker Desktop"
require_cmd python3 "macOS 自带；Linux 用包管理器安装 python3"
if ! docker info >/dev/null 2>&1; then
    echo "ERROR: docker daemon 不可用，请确认 Docker Desktop 已启动" >&2
    exit 1
fi

# Docker Desktop 启用「containerd 镜像存储」时，docker save 对带 attestation 的多架构镜像
# (典型 metrics-server) 只写清单、丢掉层 blob → 离线包「有清单无实体」→ kubelet 退化在线
# 拉取 → ImagePullBackOff。本脚本据此规避：覆盖表里的镜像源镜像不走 docker save，改用一个
# 运行中的 k3s containerd 容器 ctr 完整拉取 + export 再并入(见第 5/6 步)；直连镜像仍用
# docker save。兜底：第 6 步打包后做 blob 完整性自校验，任一镜像缺层即报错中止，不产出坏包。
if docker info --format '{{.DriverStatus}}' 2>/dev/null | grep -q "io.containerd.snapshotter"; then
    echo "INFO: 检测到 Docker 使用 containerd 镜像存储；镜像源镜像将经 k3s 容器 ctr 导出以规避 docker save 丢层。" >&2
fi

install_zstd() {
    if command -v zstd >/dev/null 2>&1; then
        return 0
    fi
    OS="$(uname -s)"
    case "${OS}" in
        Darwin)
            if ! command -v brew >/dev/null 2>&1; then
                echo "ERROR: macOS 上未检测到 Homebrew，请先安装：https://brew.sh" >&2
                exit 1
            fi
            brew install zstd
            ;;
        Linux)
            if command -v apt-get >/dev/null 2>&1; then
                sudo apt-get update && sudo apt-get -y install zstd
            elif command -v yum >/dev/null 2>&1; then
                sudo yum install -y zstd
            elif command -v dnf >/dev/null 2>&1; then
                sudo dnf install -y zstd
            elif command -v apk >/dev/null 2>&1; then
                sudo apk add --no-cache zstd
            else
                echo "ERROR: 未识别的 Linux 包管理器，请手动安装 zstd" >&2
                exit 1
            fi
            ;;
        *)
            echo "ERROR: 不支持的操作系统：${OS}" >&2
            exit 1
            ;;
    esac
}
install_zstd

########################################
# 4. 拼接 k3s-images.txt URL（+ -> %2B），允许 K3S_IMAGES_TXT_URL 覆盖
########################################
ENCODED_VERSION="$(echo "${K3S_VERSION}" | sed -e 's/+/%2B/g')"
DEFAULT_IMAGES_TXT_URL="https://github.com/k3s-io/k3s/releases/download/${ENCODED_VERSION}/k3s-images.txt"
IMAGES_TXT_URL="${K3S_IMAGES_TXT_URL:-${DEFAULT_IMAGES_TXT_URL}}"

echo "==> fetching k3s-images.txt from: ${IMAGES_TXT_URL}"

TMP_LIST="$(mktemp -t k3s-images.XXXXXX)"
RENAME_MAP="$(mktemp -t k3s-rename.XXXXXX)"
trap 'rm -f "${TMP_LIST}" "${RENAME_MAP}" "${RAW_TAR:-}"; rm -rf "${MIRROR_DIR:-}"' EXIT INT TERM

# --retry-all-errors：代理对 GitHub CDN 的 SSL 握手会随机中断（SSL_ERROR_SYSCALL / curl 35），
# 默认 --retry 不重试这类连接错误，需显式开启才能在弱网/代理下稳定拉到清单
if ! curl --fail --location --retry 20 --retry-delay 2 --retry-all-errors --silent --show-error \
        -o "${TMP_LIST}" "${IMAGES_TXT_URL}"; then
    echo "ERROR: 无法获取 k3s-images.txt: ${IMAGES_TXT_URL}" >&2
    echo "       请检查网络是否可达 github.com，或通过 K3S_IMAGES_TXT_URL 指定可达的内网镜像源" >&2
    exit 1
fi

# 收集非空、非注释镜像
IMAGE_LIST="$(grep -vE '^[[:space:]]*(#|$)' "${TMP_LIST}" | awk 'NF>0{print $1}')"
if [ -z "${IMAGE_LIST}" ]; then
    echo "ERROR: 镜像清单为空，请检查 ${IMAGES_TXT_URL}" >&2
    exit 1
fi

IMAGE_COUNT="$(printf '%s\n' "${IMAGE_LIST}" | wc -l | tr -d ' ')"
echo "==> pulling ${IMAGE_COUNT} images for ${ARCH}"

########################################
# 5. 拉取镜像
#    直连镜像：docker pull + docker save 即可完整导出。
#    镜像源镜像(覆盖表)：很多是带 attestation 的多架构镜像，在 Docker 的 containerd 镜像
#      存储下 docker save 无法导出其层(典型 metrics-server)。改用一个「运行中的 k3s
#      containerd 容器」按 ctr 完整拉取 + 单架构 export，再于第 6 步并入离线包。
#      容器名由 K3S_CTR_CONTAINER 指定(默认 kuship-rainbond)；先 `docker compose ... up -d`
#      起一个 standalone 容器即可。
########################################
SAVE_LIST=""
for image in ${IMAGE_LIST}; do
    src="$(mirror_source_for "${image}")"
    if [ -n "${src}" ]; then
        printf '%s %s\n' "${src}" "${image}" >> "${RENAME_MAP}"   # 记录待 ctr 导出：镜像源 -> 原始名
    else
        echo "    -> docker pull --platform=${ARCH} ${image}"
        if ! docker pull --platform="${ARCH}" "${image}"; then
            echo "ERROR: 拉取失败：${image}" >&2
            exit 1
        fi
        SAVE_LIST="${SAVE_LIST} ${image}"
    fi
done

# 镜像源镜像：经运行中的 k3s containerd 容器 ctr 完整拉取 + export
MIRROR_DIR="$(mktemp -d -t k3s-mirror.XXXXXX)"
if [ -s "${RENAME_MAP}" ]; then
    CTR_CTR="${K3S_CTR_CONTAINER:-kuship-rainbond}"
    if ! docker exec "${CTR_CTR}" k3s ctr version >/dev/null 2>&1; then
        echo "ERROR: 覆盖表中的镜像源镜像无法用 docker save 在 containerd 镜像存储下完整导出，" >&2
        echo "       需借助一个运行中的 k3s containerd 容器来 ctr 导出（实测唯一可靠路径）。" >&2
        echo "       请先起一个 standalone 容器：docker compose -f docker/docker-compose.yaml up -d" >&2
        echo "       或用 K3S_CTR_CONTAINER=<容器名> 指定后重试。" >&2
        exit 1
    fi
    echo "==> 经容器 ${CTR_CTR} 的 k3s containerd 导出镜像源镜像"
    while read -r src orig; do
        [ -z "${src}" ] && continue
        echo "    -> [ctr@${CTR_CTR}] pull --all-platforms ${src}  (export ${ARCH} → ${orig})"
        if ! docker exec "${CTR_CTR}" k3s ctr -n k8s.io images pull --all-platforms "${src}" >/dev/null 2>&1; then
            echo "ERROR: 容器内 ctr 拉取失败：${src}" >&2; exit 1
        fi
        docker exec "${CTR_CTR}" k3s ctr -n k8s.io images tag --force "${src}" "${orig}" >/dev/null 2>&1 || true
        safe="$(printf '%s' "${orig}" | tr '/:@' '___')"
        if ! docker exec "${CTR_CTR}" k3s ctr -n k8s.io images export --platform "${ARCH}" "/tmp/${safe}.tar" "${orig}" >/dev/null 2>&1; then
            echo "ERROR: 容器内 ctr 导出失败：${orig}" >&2; exit 1
        fi
        docker cp "${CTR_CTR}:/tmp/${safe}.tar" "${MIRROR_DIR}/${safe}.tar" >/dev/null
        docker exec "${CTR_CTR}" rm -f "/tmp/${safe}.tar" >/dev/null 2>&1 || true
    done < "${RENAME_MAP}"
fi

########################################
# 6. 打包：docker save 直连镜像 -> 并入 ctr 导出的镜像源镜像 -> blob 完整性校验 -> 压缩
########################################
OUTPUT="${REPO_ROOT}/k3s-images-${ARCH_TAG}.tar.zst"
RAW_TAR="$(mktemp -t k3s-raw.XXXXXX)"
echo "==> docker save 直连镜像 -> ${RAW_TAR}"
rm -f "${OUTPUT}"

# shellcheck disable=SC2086
docker save ${SAVE_LIST} -o "${RAW_TAR}"

echo "==> 并入镜像源镜像 + blob 完整性校验(遍历 index.json，兼容 manifest-list)"
python3 - "${RAW_TAR}" "${MIRROR_DIR}" "${ARCH}" <<'PYEOF'
import json, os, shutil, sys, tarfile, tempfile

raw, mdir, plat = sys.argv[1], sys.argv[2], sys.argv[3]
arch = plat.split('/', 1)[1] if '/' in plat else plat   # linux/arm64 -> arm64

work = tempfile.mkdtemp()
with tarfile.open(raw) as tf:
    tf.extractall(work)

def merge_oci(srcdir):
    # 拷贝 blob，返回该归档 index.json 的 manifests
    for root, _, files in os.walk(os.path.join(srcdir, 'blobs')):
        for fn in files:
            dst = os.path.join(work, 'blobs/sha256', fn)
            if not os.path.exists(dst):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy(os.path.join(root, fn), dst)
    with open(os.path.join(srcdir, 'index.json')) as f:
        return json.load(f).get('manifests', [])

idx_path = os.path.join(work, 'index.json')
with open(idx_path) as f:
    mi = json.load(f)

# 并入每个 ctr 导出的镜像源镜像归档
if os.path.isdir(mdir):
    for tarname in sorted(os.listdir(mdir)):
        ed = tempfile.mkdtemp()
        with tarfile.open(os.path.join(mdir, tarname)) as tf:
            tf.extractall(ed)
        mans = merge_oci(ed)
        names_new = {(m.get('annotations') or {}).get('io.containerd.image.name') for m in mans}
        mi['manifests'] = [m for m in mi['manifests']
                           if (m.get('annotations') or {}).get('io.containerd.image.name') not in names_new]
        mi['manifests'].extend(mans)
        shutil.rmtree(ed)
with open(idx_path, 'w') as f:
    json.dump(mi, f, separators=(',', ':'))

# 完整性校验：遍历 index.json，遇 manifest-list 递归到目标架构子清单，校验 config+全部层
names = set()
for root, _, files in os.walk(os.path.join(work, 'blobs')):
    for fn in files:
        names.add('blobs/sha256/' + fn)

def loadblob(dg):
    with open(os.path.join(work, 'blobs/sha256', dg.split(':')[1])) as f:
        return json.load(f)

bad = []
for m in mi['manifests']:
    nm = (m.get('annotations') or {}).get('io.containerd.image.name')
    top = loadblob(m['digest'])
    if 'manifests' in top:   # manifest list
        chosen = [loadblob(c['digest']) for c in top['manifests']
                  if (c.get('platform') or {}).get('architecture') == arch
                  and (c.get('platform') or {}).get('os') == 'linux']
    else:
        chosen = [top]
    if not chosen:
        bad.append((nm, '无%s' % arch, '-')); continue
    for mf in chosen:
        need = [mf['config']['digest']] + [l['digest'] for l in mf.get('layers', [])]
        miss = [d for d in need if 'blobs/sha256/' + d.split(':')[1] not in names]
        if miss:
            bad.append((nm, len(miss), len(need)))
if bad:
    for nm, a, b in bad:
        print('  XX %s: 缺 %s/%s blob' % (nm, a, b), file=sys.stderr)
    print('ERROR: 离线包不完整，已中止（请检查上面缺层的镜像）', file=sys.stderr)
    shutil.rmtree(work)
    sys.exit(1)
print('  OK: %d 个镜像 %s 实体 blob 全部完整' % (len(mi['manifests']), arch))

out = raw + '.fixed'
with tarfile.open(out, 'w') as tf:
    for name in sorted(os.listdir(work)):
        tf.add(os.path.join(work, name), arcname=name)
shutil.move(out, raw)
shutil.rmtree(work)
PYEOF

echo "==> 压缩 -> ${OUTPUT}"
zstd -T0 -19 -o "${OUTPUT}" "${RAW_TAR}"
rm -f "${RAW_TAR}"

echo "==> done: $(ls -lh "${OUTPUT}" | awk '{print $5, $9}')"
