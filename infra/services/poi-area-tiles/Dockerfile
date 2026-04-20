FROM debian:bookworm-slim

ARG TIPPECANOE_GIT_REF=2.79.0
ARG TIPPECANOE_REPO_URL=https://github.com/felt/tippecanoe.git

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        git \
        libsqlite3-dev \
        python3 \
        python3-pip \
        sqlite3 \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${TIPPECANOE_GIT_REF}" "${TIPPECANOE_REPO_URL}" /tmp/tippecanoe \
    && make -C /tmp/tippecanoe -j"$(nproc)" \
    && make -C /tmp/tippecanoe install \
    && rm -rf /tmp/tippecanoe

RUN pip3 install --break-system-packages --no-cache-dir osmium

WORKDIR /app
COPY extract_poi_area_geojson.py /app/extract_poi_area_geojson.py

ENTRYPOINT ["/bin/bash"]
