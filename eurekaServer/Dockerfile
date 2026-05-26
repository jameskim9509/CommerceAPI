FROM openjdk:17
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/eurekaServer-0.1.jar eureka-server.jar
ENTRYPOINT ["java", "-jar", "/deploy/eureka-server.jar"]
