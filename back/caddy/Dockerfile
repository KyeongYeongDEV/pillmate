# PillMate Caddy — 표준 caddy:2-alpine 에 없는 rate_limit 모듈을 xcaddy 로 포함.
# 오라클 A1(arm64)에서 빌드되므로 arm64 바이너리 자동 생성. (DEPLOY.md xcaddy arm 노트 참조)
FROM caddy:2-builder-alpine AS builder
RUN xcaddy build --with github.com/mholt/caddy-ratelimit

FROM caddy:2-alpine
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
