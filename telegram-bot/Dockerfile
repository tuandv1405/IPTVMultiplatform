# Same image for Cloud Run (TELEGRAM_MODE=webhook) and a VPS (polling).
# node:sqlite (the own-server store) is built in from Node 22.13.
FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY src ./src
COPY scripts ./scripts

# SQLite database (STORE_DRIVER=sqlite). Mount a volume here so it survives upgrades.
RUN mkdir -p /app/data && chown node:node /app/data
VOLUME /app/data

# Cloud Run sets PORT; 8080 elsewhere.
ENV PORT=8080
EXPOSE 8080

USER node
CMD ["node", "src/index.js"]
