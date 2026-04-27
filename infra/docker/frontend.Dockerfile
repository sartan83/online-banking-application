# syntax=docker/dockerfile:1.7

# Builder image — pinned by digest.
FROM node:22-alpine@sha256:8ea2348b068a9544dae7317b4f3aafcdc032df1647bb7d768a05a5cad1a7683f AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY frontend/. .
RUN npm run build

# Runtime: nginxinc/nginx-unprivileged is nginx already configured to run
# entirely as the `nginx` user (uid 101) — no root master process — and to
# listen on the unprivileged port 8080. Pinned by digest. DORA Art. 9 / least
# privilege.
FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0
# Run as root briefly so the COPY can chown — the base image's USER 101
# directive will resume below. (Static-analysis tools like Trivy DS-0002
# look for an explicit USER directive in this stage.)
USER root
COPY --from=build --chown=nginx:nginx /src/dist /usr/share/nginx/html
COPY --chown=nginx:nginx infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
USER nginx
EXPOSE 8080
