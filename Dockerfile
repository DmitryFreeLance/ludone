FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/rollsroms-bot-1.0.0.jar /app/app.jar
ENV DB_PATH=/app/data/bot.db
VOLUME ["/app/data"]
CMD ["java", "-jar", "/app/app.jar"]
