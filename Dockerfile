FROM openjdk:17-jdk
WORKDIR /app
RUN ls
COPY gcpdeployment-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]