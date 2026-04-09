FROM ubuntu:24.04

ARG TIPPECANOE_REF=2.79.0

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      build-essential \
      ca-certificates \
      git \
      libsqlite3-dev \
      sqlite3 \
      zlib1g-dev && \
    rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${TIPPECANOE_REF}" https://github.com/felt/tippecanoe.git /tmp/tippecanoe && \
    make -C /tmp/tippecanoe -j"$(nproc)" && \
    make -C /tmp/tippecanoe install && \
    rm -rf /tmp/tippecanoe

ENTRYPOINT ["tippecanoe"]
