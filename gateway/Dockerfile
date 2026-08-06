# syntax=docker/dockerfile:1
# 멀티스테이지 빌드 — build context 는 레포 루트(멀티모듈 Gradle 의 settings.gradle/gradlew 필요).
#   compose·CI 모두 루트 컨텍스트로 이 Dockerfile 을 -f 로 지정해 빌드한다.
#   build 스테이지가 bootJar 까지 만드므로 호스트 gradle 프리빌드가 필요 없다.
#   --mount=type=cache 로 ~/.gradle(의존성)을 빌드 간 보존 → 브랜치 전환 재빌드 시 재다운로드 없음.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :gateway:bootJar --no-daemon

FROM eclipse-temurin:17-jre
RUN mkdir -p deploy
WORKDIR /deploy
COPY --from=build /workspace/gateway/build/libs/gateway-0.1.jar gateway.jar
ENTRYPOINT ["java", "-jar", "/deploy/gateway.jar"]
