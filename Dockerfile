# One Dockerfile for all Spring Boot modules.
# Usage: docker build --build-arg MODULE=mock-ecc -t mock-ecc .
# In docker-compose.yml: build: { context: ., args: { MODULE: mock-ecc } }

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
ARG MODULE
# -pl builds only this module, -am also builds the modules it depends on (canonical-model)
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
# curl is needed by the Compose health checks; check first with:
#   docker run --rm eclipse-temurin:25-jre sh -c "command -v curl"
# and uncomment the next line only if nothing is printed
# RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/${MODULE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
