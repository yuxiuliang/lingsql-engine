# syntax=docker/dockerfile:1.7

FROM node:24-alpine AS web-build
WORKDIR /workspace
COPY web_src/package*.json ./web_src/
WORKDIR /workspace/web_src
RUN npm ci
WORKDIR /workspace
COPY web_src ./web_src
COPY src ./src
RUN npm --prefix web_src run build

FROM maven:3.9.16-eclipse-temurin-25 AS app-build
ARG LINGSQL_BUILD_COMMIT=local
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
COPY --from=web-build /workspace/src/main/resources/static/admin ./src/main/resources/static/admin
RUN mvn -B -ntp -DskipTests package -Dlingsql.build.commit="${LINGSQL_BUILD_COMMIT}"

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=app-build /workspace/target/lingsql-engine-*.jar /app/lingsql-engine.jar
EXPOSE 8000
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/lingsql-engine.jar"]

