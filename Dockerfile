FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY src src

RUN chmod +x gradlew && ./gradlew clean jar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080

EXPOSE 8080

CMD ["sh", "-c", "java -jar app.jar"]
