# Stage 1: Build
FROM gradle:7.6-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar --no-daemon

# Stage 2: Runtime
FROM amazoncorretto:17
RUN yum install -y curl && yum clean all
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 6060
ENTRYPOINT ["java", "-jar", "app.jar"]
