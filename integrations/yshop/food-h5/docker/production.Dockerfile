FROM nginx:1.27-alpine
COPY docker/production-nginx.conf /etc/nginx/conf.d/default.conf
COPY unpackage/dist/build/h5-minipay/ /usr/share/nginx/html/
EXPOSE 8080
