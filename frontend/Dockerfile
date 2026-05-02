FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .

ARG VITE_API_URL=/api
ENV VITE_API_URL=$VITE_API_URL

ARG VITE_KEYCLOAK_URL=http://localhost:8090
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL

ARG VITE_KEYCLOAK_REALM=ecommerce
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM

ARG VITE_KEYCLOAK_CLIENT_ID=new-user-service
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npm run build


FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --quiet --spider http://localhost/ || exit 1