# KoalaShelf web: static SPA served by Caddy, which also proxies Audiobookshelf
# so app and API share one origin (no CORS, refresh token stays in an httpOnly cookie).
# Build from the repo root:  docker build -f apps/web/Dockerfile -t koalashelf-web .

FROM node:26-alpine AS build
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

FROM caddy:2.11-alpine
COPY apps/web/docker/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/apps/web/build /srv
ENV ABS_URL=http://audiobookshelf:80
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/manifest.webmanifest >/dev/null || exit 1
