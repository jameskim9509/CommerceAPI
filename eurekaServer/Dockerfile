FROM eclipse-temurin:17-jre
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/eurekaServer-0.1.jar eureka-server.jar
ENTRYPOINT ["java", "-jar", "/deploy/eureka-server.jar"]
