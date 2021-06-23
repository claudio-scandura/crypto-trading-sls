FROM adoptopenjdk/openjdk11:x86_64-alpine-jdk-11.0.10_9-slim

ARG RUNNABLE_NAME

COPY *.tar /opt/
RUN tar xf /opt/*.tar -C /opt/ --strip-components 1 && rm /opt/*.tar

ENV RUN_SCRIPT "./opt/bin/${RUNNABLE_NAME}"

EXPOSE 8080

ENTRYPOINT [ "sh", "-c" , "JAVA_OPTS=\"$DEFAULT_JAVA_OPTS $JAVA_OPTS\" $RUN_SCRIPT"]
