FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && . /etc/os-release \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/postgresql.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-18 postgresql-contrib-18 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
