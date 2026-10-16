# KoalaShelf web: static SPA served by Caddy, which also proxies Audiobookshelf
# so app and API share one origin (no CORS, refresh token stays in an httpOnly cookie).
# Build from the repo root:  docker build -f apps/web/Dockerfile -t koalashelf-web .
#
# Base images are pinned by digest (Dependabot keeps them current). The build stage runs on
# the build machine's own platform: its output is plain static files, the same for every
# architecture, so multi-arch images only differ in the small Caddy stage.

FROM --platform=$BUILDPLATFORM node:26-alpine@sha256:6f08a523443ddedf52941874c203475b0ad503e859ad70cb26ab8be9a517dbd5 AS build
WORKDIR /repo
RUN npm i -g pnpm@12.11.2
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/web/package.json apps/web/
COPY packages/design-tokens/package.json packages/design-tokens/
COPY packages/abs-client/package.json packages/abs-client/
RUN pnpm fetch
COPY . .
RUN pnpm install --offline --frozen-lockfile --filter @koalashelf/web... \
 && pnpm --filter @koalashelf/web build

FROM caddy:2.11-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f
ARG VERSION=dev
LABEL org.opencontainers.image.title="KoalaShelf" \
      org.opencontainers.image.description="Fast, private, self-hostable client for Audiobookshelf (web app / PWA)" \
      org.opencontainers.image.source="https://github.com/Shik3i/KoalaShelf" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="$VERSION"
# Runs as an unprivileged user. Port 8080 needs no capability, so the one Caddy's binary
# carries is dropped (it would also stop the container under no-new-privileges).
RUN apk add --no-cache libcap \
 && setcap -r /usr/bin/caddy \
 && apk del libcap \
 && addgroup -S koalashelf && adduser -S -G koalashelf -H koalashelf \
 && chown -R koalashelf:koalashelf /config /data
COPY apps/web/docker/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/apps/web/build /srv
USER koalashelf
# ABS_URL: where Caddy reaches Audiobookshelf. ABS_PUBLIC_URL: the address users know it by
# (prefilled in the sign-in form); signing in to that address goes through this proxy.
ENV ABS_URL=http://audiobookshelf:80     ABS_PUBLIC_URL=""
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/manifest.webmanifest >/dev/null || exit 1
