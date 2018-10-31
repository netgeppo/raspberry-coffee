FROM debian
#
# To run on a laptop.
# Demos the WebComponents
#
LABEL maintainer="Olivier LeDiouris <olivier@lediouris.net>"

# From the host to the image
# COPY bashrc $HOME/.bashrc

RUN echo "alias ll='ls -lisah'" >> $HOME/.bashrc

RUN apt-get update
RUN apt-get install -y curl git build-essential default-jdk
RUN curl -sL https://deb.nodesource.com/setup_9.x | bash -
RUN apt-get install -y nodejs

RUN echo "git --version" >> $HOME/.bashrc
RUN echo "echo -n 'node:' && node -v" >> $HOME/.bashrc
RUN echo "echo -n 'npm:' && npm -v" >> $HOME/.bashrc
RUN echo "java -version" >> $HOME/.bashrc

RUN mkdir /workdir
WORKDIR /workdir
RUN git clone https://github.com/OlivierLD/WebComponents.git
WORKDIR /workdir/WebComponents

EXPOSE 8080
CMD ["npm", "start"]
