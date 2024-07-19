FROM openjdk:17
RUN mkdir -p deploy
WORKDIR /deploy
COPY ./build/libs/userApi-0.1.jar userApi.jar
ENV MAILGUN_DOMAIN = ""
ENV MAILGUN_APIKEY = ""
ENTRYPOINT ["java", "-Dmailgun.domain=${MAILGUN_DOMAIN}", "-Dmailgun.apiKey=${MAILGUN_APIKEY}", "-jar", "/deploy/userApi.jar"]