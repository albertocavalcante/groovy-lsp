ARG BASE_IMAGE=registry.access.redhat.com/ubi9/ubi-minimal
ARG BASE_IMAGE_TAG=latest

FROM ${BASE_IMAGE}:${BASE_IMAGE_TAG}

ARG JAVA_VERSION=17
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

USER root

# -----------------------------------------------------------------------------
# Layer 1: Bootstrap Dependencies
# Install minimal tools needed for downloading artifacts (curl, checksums).
# This layer is highly stable and rarely changes.
# -----------------------------------------------------------------------------
RUN microdnf update -y \
    && microdnf reinstall -y curl-minimal \
    && microdnf install -y unzip \
    && microdnf clean all \
    && rm -rf /var/cache/yum

# -----------------------------------------------------------------------------
# Layer 2: Kotlin LSP VSIX
# Download and cache the VSIX.
# Placed BEFORE the main package install so that adding dev tools (like make/jq)
# does not trigger a re-download of this artifact.
# -----------------------------------------------------------------------------
# See: https://github.com/Kotlin/kotlin-lsp/releases
ARG KOTLIN_LSP_VERSION=0.253.10629
ARG KOTLIN_LSP_SHA256=638ec1552fde7e5158f8c7df06443cfb97c5c09957273790586cbd59115bf11c

# Copy local cache if available (from .devcontainer/cache/)
COPY cache/. /tmp/cache/

RUN if [ -f "/tmp/cache/kotlin-${KOTLIN_LSP_VERSION}.vsix" ]; then \
        echo "Using local cache for Kotlin LSP VSIX..."; \
        cp "/tmp/cache/kotlin-${KOTLIN_LSP_VERSION}.vsix" /tmp/kotlin.vsix; \
    else \
        echo "Downloading Kotlin LSP VSIX..."; \
        curl -fSL -o /tmp/kotlin.vsix https://download-cdn.jetbrains.com/kotlin-lsp/${KOTLIN_LSP_VERSION}/kotlin-${KOTLIN_LSP_VERSION}.vsix; \
    fi \
    # Verify checksum
    && echo "${KOTLIN_LSP_SHA256}  /tmp/kotlin.vsix" | sha256sum -c - \
    # Verify it's a valid zip file (VSIX is a zip) to detect corruption early
    && unzip -t /tmp/kotlin.vsix \
    && mkdir -p /usr/local/share/vscode-extensions \
    # NB: Record build timestamp for debugging purposes
    && date > /usr/local/share/vscode-extensions/.build_time \
    && mv /tmp/kotlin.vsix /usr/local/share/vscode-extensions/kotlin.vsix \
    && chmod 644 /usr/local/share/vscode-extensions/kotlin.vsix \
    && rm -rf /tmp/cache /tmp/kotlin.vsix

# -----------------------------------------------------------------------------
# Layer 3: Java Toolchain (Heavy / Stable)
# Installed first so that adding lightweight tools below doesn't trigger a JDK reinstall.
# -----------------------------------------------------------------------------
RUN microdnf install -y --nodocs \
        java-${JAVA_VERSION}-openjdk-devel \
    && microdnf clean all \
    && rm -rf /var/cache/yum

# -----------------------------------------------------------------------------
# Layer 4: Base Development Tools (Light / Volatile)
# Install small utilities. This layer is fast to rebuild.
# -----------------------------------------------------------------------------
RUN microdnf install -y --nodocs \
        shadow-utils \
        sudo \
        git \
        make \
        zip \
        procps-ng \
        findutils \
        tar \
        gzip \
        vim \
    && microdnf clean all \
    && rm -rf /var/cache/yum

# -----------------------------------------------------------------------------
# Layer 5: Modern CLI Tools (rg, fd, bat, fzf, jq)
# High-performance replacements for standard tools.
# -----------------------------------------------------------------------------
ARG JQ_VERSION=1.7.1
ARG RG_VERSION=14.1.0
ARG FD_VERSION=10.2.0
ARG BAT_VERSION=0.24.0
ARG FZF_VERSION=0.54.0

RUN ARCH=$(uname -m) \
    && case $ARCH in \
        x86_64) RUST_ARCH="x86_64-unknown-linux-musl"; JQ_ARCH="linux64"; FZF_ARCH="linux_amd64"; ;; \
        aarch64) RUST_ARCH="aarch64-unknown-linux-musl"; JQ_ARCH="linux-arm64"; FZF_ARCH="linux_arm64"; ;; \
        *) echo "Unsupported arch: $ARCH"; exit 1; ;; \
    esac \
    # JQ
    && curl -fL -o /usr/local/bin/jq https://github.com/jqlang/jq/releases/download/jq-${JQ_VERSION}/jq-${JQ_ARCH} \
    && chmod +x /usr/local/bin/jq \
    # Ripgrep
    && curl -fL -o /tmp/rg.tar.gz https://github.com/BurntSushi/ripgrep/releases/download/${RG_VERSION}/ripgrep-${RG_VERSION}-${RUST_ARCH}.tar.gz \
    && tar -xzf /tmp/rg.tar.gz -C /tmp \
    && mv /tmp/ripgrep-${RG_VERSION}-${RUST_ARCH}/rg /usr/local/bin/ \
    # FD
    && curl -fL -o /tmp/fd.tar.gz https://github.com/sharkdp/fd/releases/download/v${FD_VERSION}/fd-v${FD_VERSION}-${RUST_ARCH}.tar.gz \
    && tar -xzf /tmp/fd.tar.gz -C /tmp \
    && mv /tmp/fd-v${FD_VERSION}-${RUST_ARCH}/fd /usr/local/bin/ \
    # Bat
    && curl -fL -o /tmp/bat.tar.gz https://github.com/sharkdp/bat/releases/download/v${BAT_VERSION}/bat-v${BAT_VERSION}-${RUST_ARCH}.tar.gz \
    && tar -xzf /tmp/bat.tar.gz -C /tmp \
    && mv /tmp/bat-v${BAT_VERSION}-${RUST_ARCH}/bat /usr/local/bin/ \
    # FZF
    && curl -fL -o /tmp/fzf.tar.gz https://github.com/junegunn/fzf/releases/download/v${FZF_VERSION}/fzf-${FZF_VERSION}-${FZF_ARCH}.tar.gz \
    && tar -xzf /tmp/fzf.tar.gz -C /usr/local/bin/ \
    # Cleanup
    && rm -rf /tmp/*

# -----------------------------------------------------------------------------
# Layer 6: User Setup
# -----------------------------------------------------------------------------
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

USER $USERNAME
WORKDIR /home/$USERNAME

# Set JAVA_HOME (path may vary slightly but this is standard for RHEL OpenJDK)
ENV JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-openjdk
ENV PATH=${JAVA_HOME}/bin:${PATH}

CMD ["/bin/bash"]
