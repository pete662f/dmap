FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 1001 photon \
    && useradd --system --uid 1001 --gid photon --home-dir /srv/photon photon

WORKDIR /srv/photon

COPY docker-entrypoint.sh /usr/local/bin/dmap2-photon-entrypoint

RUN chmod +x /usr/local/bin/dmap2-photon-entrypoint \
    && chown -R photon:photon /srv/photon

EXPOSE 2322

USER 1001

ENTRYPOINT ["/usr/local/bin/dmap2-photon-entrypoint"]
