FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew .
RUN sed -i 's/\r$//' gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew build -Dquarkus.package.jar.type=fast-jar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/build/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/build/quarkus-app/*.jar /app/
COPY --from=build /workspace/build/quarkus-app/app/ /app/app/
COPY --from=build /workspace/build/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]