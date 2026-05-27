# syntax=docker/dockerfile:1.7
# apps/web 빌드. monorepo 루트를 build context 로 사용 (pnpm workspace 해결).
#   docker build -f apps/web/Dockerfile .

# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /app

RUN corepack enable

# workspace 메타 + lockfile 만 먼저 복사 → install 캐시 최적화
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY apps/web/package.json ./apps/web/

RUN pnpm install --frozen-lockfile --filter @commerce/web...

# 소스 복사 후 빌드
COPY apps/web/ ./apps/web/
RUN pnpm --filter @commerce/web build

# ---- runtime stage ----
FROM nginx:1.27-alpine
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/web/dist/ /usr/share/nginx/html/

EXPOSE 80
