FROM node:22-alpine

RUN addgroup -g 1001 -S dmap && adduser -S dmap -u 1001 -G dmap

WORKDIR /srv/ortofoto-proxy
COPY --chown=dmap:dmap server.js ./

USER 1001
EXPOSE 8080

CMD ["node", "server.js"]
