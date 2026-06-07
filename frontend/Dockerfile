# Stage 1: build static assets
FROM node:22-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm@11.5.2
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

# Stage 2: serve with Nginx
FROM nginx:alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
