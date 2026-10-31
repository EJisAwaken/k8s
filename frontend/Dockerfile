# --- Étape 1 : Build ---
FROM node:22-alpine AS build
WORKDIR /app

# Cache des dépendances : copier les fichiers de dépendances AVANT le code source
COPY package*.json ./

RUN npm ci

# Copie du reste du projet et build
COPY . .

ARG BACKEND_INTERNAL_URL=http://backend:8080
ENV BACKEND_INTERNAL_URL=$BACKEND_INTERNAL_URL
RUN npm run build

# --- Étape 3 : Production ---
FROM node:22-alpine AS production
WORKDIR /app

ENV PORT=80
ENV HOSTNAME=0.0.0.0

COPY --from=build  /app/.next/standalone ./
COPY --from=build  /app/.next/static ./.next/static
COPY --from=build  /app/public ./public


EXPOSE 80

CMD ["node", "server.js"]