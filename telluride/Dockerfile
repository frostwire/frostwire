FROM ubuntu:18.04

ENV DEBIAN_FRONTEND noninteractive

RUN apt update -y
RUN apt install python3 -y
RUN apt install python3-pip -y
RUN apt install pylint3 -y
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 10
