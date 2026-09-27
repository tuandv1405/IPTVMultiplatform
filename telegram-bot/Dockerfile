# Same image for Cloud Run (TELEGRAM_MODE=webhook) and a VPS (polling).
FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY src ./src
COPY scripts ./scripts

# Cloud Run sets PORT; 8080 elsewhere.
ENV PORT=8080
EXPOSE 8080

USER node
CMD ["node", "src/index.js"]
