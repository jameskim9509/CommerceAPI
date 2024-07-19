FROM openjdk:17
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/gateway-0.1.jar gateway.jar
ENTRYPOINT ["java", "-jar", "/deploy/gateway.jar"]