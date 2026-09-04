FROM node:22-alpine AS build
WORKDIR /workspace
ARG WEB_PACKAGE=@minipay/merchant-web
ARG WEB_DIR=apps/merchant-web
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps ./apps
COPY packages ./packages
RUN pnpm install --frozen-lockfile
RUN pnpm --filter ${WEB_PACKAGE} build
RUN mkdir -p /web-dist && cp -R ${WEB_DIR}/dist/. /web-dist/

FROM nginx:1.27-alpine
ARG NGINX_CONFIG=docker/merchant-nginx.conf
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
COPY --from=build /web-dist /usr/share/nginx/html
EXPOSE 8080
