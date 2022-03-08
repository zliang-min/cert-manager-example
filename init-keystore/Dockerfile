FROM bash:5.1-alpine3.15
LABEL org.opencontainers.image.authors="Gimi Liang" \
      org.opencontainers.image.version="v1.0.0" \
      org.opencontainers.image.title="Keystore Generator" \
      org.opencontainers.image.description="Generates a pkcs12 keystore from a kubernetes tls secret."

RUN apk add openssl
COPY gen-keystore /usr/local/bin/gen-keystore
ENV KEYSTORE_PASS="badSecret123"
CMD ["/usr/local/bin/gen-keystore"]
