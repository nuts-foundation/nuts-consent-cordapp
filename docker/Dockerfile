FROM adoptopenjdk/openjdk8:alpine as builder
LABEL maintainer="wout.slakhorst@nuts.nl"
COPY / .
RUN ./gradlew jar

FROM adoptopenjdk/openjdk8:alpine
RUN mkdir /opt/nuts
COPY lib/corda-4.4.jar /opt/nuts/corda.jar
COPY --from=builder contract/build/libs/contract-0.14.0.jar /opt/nuts/cordapps/contract-0.14.0.jar
COPY --from=builder flows/build/libs/flows-0.14.0.jar /opt/nuts/cordapps/flows-0.14.0.jar
WORKDIR /opt/nuts

HEALTHCHECK --start-period=90s --timeout=5s --interval=10s \
    CMD netstat -anl | grep 7887 > /dev/null; if [ 0 != $? ]; then exit 1; fi;

EXPOSE 7886 7887 7888 2222
CMD ["java", "-jar", "/opt/nuts/corda.jar", "--network-root-truststore-password=changeit", "--log-to-console"]
