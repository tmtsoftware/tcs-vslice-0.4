FROM ubuntu:21.04
LABEL MAINTAINER="TMT Software"
COPY bin /opt/docker/bin
COPY lib /opt/docker/lib
COPY conf /opt/docker/conf
EXPOSE 9753
RUN apt update -y
RUN apt install -y openjdk-11-jdk
ENTRYPOINT ["/opt/docker/bin/tcs-deploy", "--standalone", "--local", "/opt/docker/conf/PkStandalone.conf"]
