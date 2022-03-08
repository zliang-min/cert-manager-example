FROM openjdk:17.0.2-jdk-bullseye
LABEL org.opencontainers.image.authors="Gimi Liang" \
      org.opencontainers.image.version="v1.0.0" \
      org.opencontainers.image.title="Certificate Monitor" \
      org.opencontainers.image.description="Monitors Certificate resource from cert-manager."
COPY . /app/
WORKDIR /app
RUN curl -Ls https://sh.jbang.dev | bash -s - build --verbose Main.java
RUN curl -Ls https://sh.jbang.dev | bash -s - export portable --verbose Main.java

ENTRYPOINT ["java"]
CMD ["-jar", "Main.jar"]
