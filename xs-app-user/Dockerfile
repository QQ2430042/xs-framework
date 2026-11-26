FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/xs-app-user-*-boot.jar app.jar
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
