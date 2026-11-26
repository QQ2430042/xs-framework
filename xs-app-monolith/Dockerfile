FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/xs-app-monolith-*-boot.jar app.jar
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
