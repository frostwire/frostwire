FROM --platform=linux/amd64 ubuntu:18.04
ENV DEBIAN_FRONTEND noninteractive
ARG PYTHON_VERSION=3.10.1
RUN apt update -y
RUN apt upgrade -y
RUN apt install libssl1.1 -y
RUN apt install pylint3 -y
RUN apt install less -y
RUN apt install emacs-nox -y
RUN apt install wget -y
RUN apt install xz-utils -y
RUN apt install build-essential checkinstall -y
RUN apt install libreadline-gplv2-dev -y
RUN apt install libncursesw5-dev -y
RUN apt install libssl-dev -y
RUN apt install openssl -y
RUN apt install libsqlite3-dev -y
RUN apt install tk-dev -y
RUN apt install libgdbm-dev -y
RUN apt install libc6-dev -y
RUN apt install libbz2-dev -y
RUN apt install libffi-dev -y
RUN wget https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tar.xz
RUN tar xf Python-${PYTHON_VERSION}.tar.xz
RUN cd Python-${PYTHON_VERSION} && \
 ./configure\
 --enable-optimizations\
 --prefix=/usr/local\
 --enable-shared\
 LDFLAGS="-Wl,-rpath /usr/local/lib" &&\
 make -j4 build_all &&\
 make altinstall
RUN rm Python-${PYTHON_VERSION}.tar.xz
RUN rm /usr/bin/python3 && ln -s /usr/local/bin/python3.10 /usr/bin/python3
RUN ln -s /usr/local/bin/pip3.10 /usr/bin/pip3
RUN pip3 install --upgrade pip
RUN pip3 install pyinstaller
RUN pip3 install pylint