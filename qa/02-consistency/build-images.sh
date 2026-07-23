#!/usr/bin/env bash
# =============================================================================
# treatment(방어 ON) 이미지 빌드 — Dockerfile 이 prebuilt build/libs/*.jar 를 COPY 하므로
# 호스트 gradle 로 jar 를 먼저 만든 뒤 compose build 한다.
#
# asciidoctor(restdocs) 가 bootJar 에 걸려 있어 -x test 로 스킵한다(CI 와 동일: clean build -x test).
#
# 사용: ./build-images.sh
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"
ROOT="../.."

echo "[build] gradle bootJar (-x test) ..."
( cd "$ROOT" && ./gradlew :userApi:bootJar :orderApi:bootJar :gateway:bootJar :eurekaServer:bootJar -x test )

echo "[build] docker compose build (treatment 이미지: consist-*) ..."
docker compose -f docker-compose.qa.yml build

echo "[build] treatment 이미지 준비 완료 ✓"
