#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

if [[ $# -gt 1 || (${1:-} != "" && ${1:-} != "--force") ]]; then
    echo "Usage: $0 [--force]" >&2
    exit 2
fi

if [[ ${1:-} != "--force" ]]; then
    if [[ ! -t 0 ]]; then
        echo "Confirmation required; rerun interactively or pass --force." >&2
        exit 1
    fi
    read -r -p "Delete the Compose containers and database volume? All data will be lost. [y/N] " answer
    if [[ $answer != "y" && $answer != "Y" ]]; then
        echo "Cleanup cancelled."
        exit 0
    fi
fi

docker compose down --volumes
echo "Compose containers and data volumes removed."
