FROM eclipse-temurin:17-jre
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/userApi-0.1.jar userApi.jar
ENTRYPOINT ["java", "-jar", "/deploy/userApi.jar"]
