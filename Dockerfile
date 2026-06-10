FROM docker.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY build/libs/pulse-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
