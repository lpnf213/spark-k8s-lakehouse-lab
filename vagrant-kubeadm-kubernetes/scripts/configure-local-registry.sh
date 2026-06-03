#!/bin/bash
#
# Configure CRI-O to pull from the local Docker registry over HTTP.

set -euxo pipefail

REGISTRY_HOST="${1:-192.168.31.1:5000}"
CONFIG_DIR="/etc/containers/registries.conf.d"
CONFIG_FILE="${CONFIG_DIR}/010-local-insecure-registry.conf"

sudo mkdir -p "${CONFIG_DIR}"

cat <<EOF | sudo tee "${CONFIG_FILE}"
[[registry]]
location = "${REGISTRY_HOST}"
insecure = true
EOF

sudo systemctl restart crio
sudo systemctl restart kubelet

echo "Configured CRI-O insecure registry: ${REGISTRY_HOST}"
