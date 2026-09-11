FROM nginx:1.27-alpine
ARG APP
COPY docker/k3s-static-nginx.conf /etc/nginx/conf.d/default.conf
COPY apps/${APP}/dist/ /usr/share/nginx/html/
USER 101
EXPOSE 8080
