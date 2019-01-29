FROM navikt/java:10
COPY config-preprod.json .
COPY config-prod.json .
COPY build/libs/syfosmpapirmottak-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
