FROM node:20-alpine
WORKDIR /app
COPY . .
# PORT is injected by Render at runtime; defaults to 3000 locally
EXPOSE 10000
CMD ["node", "backend/server.js"]
