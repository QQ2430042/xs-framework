FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/xs-app-tenant-*-boot.jar app.jar
EXPOSE 18082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
