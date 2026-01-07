FROM amazoncorretto:17
RUN yum install -y curl && yum clean all
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 6060
ENTRYPOINT ["java", "-jar", "app.jar"]
