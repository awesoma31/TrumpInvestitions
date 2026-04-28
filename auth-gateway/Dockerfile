FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/install/auth-gateway/ ./
EXPOSE 8080
ENTRYPOINT ["./bin/auth-gateway"]
