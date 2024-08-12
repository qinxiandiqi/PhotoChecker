# 使用官方 nginx 镜像作为基础镜像
FROM nginx:latest

# 将 HTML 文件和 favicon 图片复制到容器中的默认 Nginx 静态文件目录
COPY privacy.html /usr/share/nginx/html/index.html
COPY favicon.ico /usr/share/nginx/html/favicon.ico

# 暴露容器的80端口
EXPOSE 80
