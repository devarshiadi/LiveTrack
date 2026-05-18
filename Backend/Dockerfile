# ---- build stage ----
FROM golang:1.25-alpine AS build

WORKDIR /src

# Cache module downloads.
COPY go.mod go.sum ./
RUN go mod download

# Build a static binary. modernc.org/sqlite is pure Go, so CGO is off — no gcc.
# templates/ and static/ are embedded via //go:embed, so the binary is self-contained.
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/gpsbackend .

# ---- runtime stage ----
FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata \
    && adduser -D -u 1000 appuser

COPY --from=build /out/gpsbackend /usr/local/bin/gpsbackend

# /data is the Hugging Face Spaces persistent-storage mount. Everything else in
# the container filesystem is ephemeral, so the SQLite database lives here.
RUN mkdir -p /data && chown appuser:appuser /data

USER appuser

ENV GPS_HOST=0.0.0.0 \
    GPS_PORT=7860 \
    GPS_DB_PATH=/data/gps.db

# Hugging Face Spaces routes external traffic to this port.
EXPOSE 7860

CMD ["gpsbackend"]
