FROM openjdk:17
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/orderApi-0.1.jar orderApi.jar
ENTRYPOINT ["java", "-jar", "/deploy/orderApi.jar"]