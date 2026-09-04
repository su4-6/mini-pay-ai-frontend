#!/usr/bin/env bash
set -euo pipefail
docker compose -f compose.server.yaml up -d --build --remove-orphans
docker compose -f compose.server.yaml ps
