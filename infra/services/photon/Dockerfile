FROM eclipse-temurin:21-jre-jammy

WORKDIR /srv/photon

COPY docker-entrypoint.sh /usr/local/bin/dmap2-photon-entrypoint

RUN chmod +x /usr/local/bin/dmap2-photon-entrypoint

EXPOSE 2322

ENTRYPOINT ["/usr/local/bin/dmap2-photon-entrypoint"]
