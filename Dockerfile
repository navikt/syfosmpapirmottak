FROM navikt/java:10
COPY build/install/syfosmpapirmottak/bin/syfosmpapirmottak bin/app
COPY build/install/syfosmpapirmottak/lib lib/
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
