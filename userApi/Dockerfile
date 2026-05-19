FROM openjdk:17
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/userApi-0.1.jar userApi.jar
ENTRYPOINT ["java", "-jar", "/deploy/userApi.jar"]
