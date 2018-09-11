FROM navikt/java:10
COPY build/install/syfosmpapirmottak bin/app
COPY build/install/syfosmpapirmottak lib/
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
