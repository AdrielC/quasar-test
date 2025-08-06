# Use Ubuntu 22.04 as base image
FROM mcr.microsoft.com/vscode/devcontainers/base:ubuntu-22.04

# Set environment variables
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV JAVA_VERSION=21
ENV SBT_VERSION=1.10.6
ENV SCALA_VERSION=3.7.2
ENV SCALA_CLI_VERSION=1.8.4
ENV BLOOP_VERSION=1.5.10
ENV METALS_VERSION=0.14.1

# Install build tools and utilities
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    curl \
    wget \
    unzip \
    zip \
    jq \
    htop \
    tree \
    vim \
    nano \
    fish \
    tmux \
    postgresql-client \
    redis-tools \
    protobuf-compiler \
    zsh \
    && rm -rf /var/lib/apt/lists/*


# Install Coursier for ARCH of the container
RUN curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > /usr/local/bin/cs && \
    chmod +x /usr/local/bin/cs && \ 
    cs install scala:${SCALA_VERSION} scalac:${SCALA_VERSION} scala-cli:${SCALA_CLI_VERSION} scalafmt scalafix sbt

# Install Oh My Zsh for better terminal experience
RUN sh -c "$(curl -fsSL https://raw.github.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended && \
    chsh -s $(which zsh) vscode && \
    echo 'export PATH="$HOME/.local/share/coursier/bin:$PATH"' >> ~/.zshrc && \
    echo 'export PATH="$HOME/.local/share/coursier/bin:$PATH"' >> ~/.bashrc

RUN for plugin in zsh-autosuggestions zsh-syntax-highlighting zsh-history-substring-search zsh-completions zsh-history-substring-search; do \
    git clone https://github.com/zsh-users/$plugin.git ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/$plugin; \
    omz plugin enable $plugin; \
    done

# Install Node.js and npm (for frontend development)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# Install useful npm packages globally
RUN npm install -g \
    typescript \
    ts-node \
    prettier \
    eslint \
    @typescript-eslint/parser \
    @typescript-eslint/eslint-plugin
    

# Install Docker CLI (for docker-in-docker)
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli

# Install grpcurl for gRPC testing
RUN curl -L https://github.com/fullstorydev/grpcurl/releases/download/v1.8.9/grpcurl_1.8.9_linux_x86_64.tar.gz | tar -xz -C /usr/local/bin

# Create workspace directories and set permissions
RUN mkdir -p /workspace/.ivy2 /workspace/.sbt /workspace/.cache /workspace/.metals /workspace/.bloop && \
    chown -R vscode:vscode /workspace

# Switch to vscode user
USER vscode

# Set up user environment
RUN echo 'export PATH="$HOME/.local/share/coursier/bin:$PATH"' >> ~/.zshrc && \
    echo 'export PATH="$HOME/.local/share/coursier/bin:$PATH"' >> ~/.bashrc

# Pre-warm SBT and Scala
RUN mkdir -p /tmp/warmup && \
    cd /tmp/warmup && \
    echo 'ThisBuild / scalaVersion := "${SCALA_VERSION}"' > build.sbt && \
    echo 'libraryDependencies += "dev.zio" %% "zio" % "2.1.17"' >> build.sbt && \
    echo 'object Main extends zio.ZIOAppDefault { def run = zio.Console.printLine("Hello ZIO!") }' > Main.scala && \
    sbt compile && \
    cd / && rm -rf /tmp/warmup

# Set working directory
WORKDIR /workspace

# Expose common ports for development
EXPOSE 8080 8081 9000 5005 3000

# Default command
CMD ["/bin/zsh"] 