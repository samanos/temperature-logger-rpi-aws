FROM resin/rpi-raspbian:jessie

RUN echo "deb http://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823

RUN echo 'deb http://archive.raspberrypi.org/debian/ jessie main' >> /etc/apt/sources.list.d/raspi.list && \
    echo oracle-java8-jdk shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv CF8A1AF502A2AA2D763BAE7E82B129927FA3303E

RUN apt-get update && \
    apt-get -y install oracle-java8-jdk sbt && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY . /app

WORKDIR /app

RUN sbt stage

CMD ["target/universal/stage/bin/tlog", "reporter"]
