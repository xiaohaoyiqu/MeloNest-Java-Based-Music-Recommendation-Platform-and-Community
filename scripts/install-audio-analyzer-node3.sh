#!/bin/bash
                                                                            
                                                                              
set -euo pipefail

PYTHON_BIN="${PYTHON_BIN:-/usr/local/soft/python3.7.16/bin/python3}"
TARGET_DIR="${AUDIO_ANALYZER_PYTHONPATH:-/sdb1/haoranmusicData/tools/essentia-py37}"
TARGET_PARENT=$(dirname "$TARGET_DIR")

if [ "$(hostname -s)" != "node3" ] && [ "${ALLOW_OTHER_HOST:-false}" != "true" ]; then
    echo "refusing to install outside node3" >&2
    exit 2
fi

if [ ! -x "$PYTHON_BIN" ]; then
    echo "python runtime not found: $PYTHON_BIN" >&2
    exit 2
fi

verify_runtime() {
    PYTHONPATH="$1" "$PYTHON_BIN" -c \
        'import essentia, numpy, yaml, six; print("essentia=%s numpy=%s" % (essentia.__version__, numpy.__version__))'
}

if [ -d "$TARGET_DIR" ]; then
    verify_runtime "$TARGET_DIR"
    exit 0
fi

mkdir -p "$TARGET_PARENT"
STAGE_DIR=$(mktemp -d "${TARGET_PARENT}/.essentia-py37.XXXXXX")
cleanup() {
    case "$STAGE_DIR" in
        "${TARGET_PARENT}"/.essentia-py37.*) rm -rf -- "$STAGE_DIR" ;;
    esac
}
trap cleanup EXIT

download_wheel() {
    filename="$1"
    expected_sha256="$2"
    url="$3"
    curl --fail --silent --show-error --location "$url" --output "$STAGE_DIR/$filename"
    printf '%s  %s\n' "$expected_sha256" "$STAGE_DIR/$filename" | sha256sum --check --status
}

download_wheel \
    numpy-1.21.6-cp37-cp37m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl \
    a6be4cb0ef3b8c9250c19cc122267263093eee7edd4e3fa75395dfda8c17a8e2 \
    https://files.pythonhosted.org/packages/6d/ad/ff3b21ebfe79a4d25b4a4f8e5cf9fd44a204adb6b33c09010f566f51027a/numpy-1.21.6-cp37-cp37m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl
download_wheel \
    six-1.16.0-py2.py3-none-any.whl \
    8abb2f1d86890a2dfb989f9a77cfcfd3e47c2a354b01111771326f8aa26e0254 \
    https://files.pythonhosted.org/packages/d9/5a/e7c31adbe875f2abbb91bd84cf2dc52d792b5a01506781dbcf25c91daf11/six-1.16.0-py2.py3-none-any.whl
download_wheel \
    PyYAML-6.0.1-cp37-cp37m-manylinux_2_17_x86_64.manylinux2014_x86_64.whl \
    baa90d3f661d43131ca170712d903e6295d1f7a0f595074f151c0aed377c9b9c \
    https://files.pythonhosted.org/packages/d7/8f/db62b0df635b9008fe90aa68424e99cee05e68b398740c8a666a98455589/PyYAML-6.0.1-cp37-cp37m-manylinux_2_17_x86_64.manylinux2014_x86_64.whl
download_wheel \
    essentia-2.1b6.dev1034-cp37-cp37m-manylinux_2_17_x86_64.manylinux2014_x86_64.whl \
    56975de212085c8ac567335d2cc6d4cf64a30a9acbc242028252f03d05a8def7 \
    https://files.pythonhosted.org/packages/fe/4e/581cb49a7c891c5328fd739591d0b7c55cd7fecb32382206517f363af944/essentia-2.1b6.dev1034-cp37-cp37m-manylinux_2_17_x86_64.manylinux2014_x86_64.whl

mkdir "$STAGE_DIR/site"
"$PYTHON_BIN" -m pip install --disable-pip-version-check --no-index --no-deps \
    --target "$STAGE_DIR/site" "$STAGE_DIR"/*.whl
verify_runtime "$STAGE_DIR/site"
mv "$STAGE_DIR/site" "$TARGET_DIR"
echo "audio analyzer installed: $TARGET_DIR"

