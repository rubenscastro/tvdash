FROM node:20-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends chromium fonts-liberation ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY render.mjs ./
COPY house ./house
ENV CHROMIUM_PATH=/usr/bin/chromium
CMD ["node", "render.mjs"]
