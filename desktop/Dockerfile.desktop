# Create your own 'frostwire:desktop' image by invoking
# docker build -t frostwire:desktop . -f Dockerfile.desktop .
FROM ubuntu:16.04
RUN apt update
RUN apt install -y git gradle openjdk-8-jdk
RUN git clone https://github.com/gubatron/frostwire
RUN export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

