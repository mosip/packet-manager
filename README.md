# Packet Manager

[![Maven Package upon a push](https://github.com/mosip/packet-manager/actions/workflows/push-trigger.yml/badge.svg?branch=release-1.3.x)](https://github.com/mosip/packet-manager/actions/workflows/push-trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?branch=release-1.3.x&project=mosip_packet-manager&id=mosip_packet-manager2&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mosip_packet-manager&branch=release-1.3.x)

## Overview

The **Packet Manager** is a MOSIP module that creates, reads, validates, and manages packets across registration and identity lifecycle processes with support for multiple sources, processes and object store adapters.

The registration packet structure is available here: [Packet structure](https://docs.mosip.io/1.2.0/id-lifecycle-management/supporting-components/packet-manager/registration-packet-structure)

## Features

- Packet creation and reading APIs
- Packet validation against ID schema
- Tag management (add/update/delete)
- Priority-based packet reading
- Multi-source and multi-process support
- Object store integration (S3, POSIX, Swift)


## Services

The Packet Manager module contains the following:

1. **[Packet Manager Library](commons-packet/commons-packet-manager)** (`commons-packet-manager`) - Core library utilized by Registration Client and Resident Service for packet operations.
2. **[Packet Service](commons-packet/commons-packet-service)** (`commons-packet-service`) - A RESTful service providing APIs for managing packets in the object store.

## Database
NA (Not applicable)

## Local Setup

The project can be set up in two ways:

1. [Local Setup (for Development or Contribution)](#local-setup-for-development-or-contribution)
2. [Local Setup with Docker (Easy Setup for Demos)](#local-setup-with-docker-easy-setup-for-demos)

### Prerequisites

Before you begin, ensure you have the following installed:

- **JDK**: 21
- **Maven**: 3.9.6
- **Docker**: Latest stable version
- **Keycloak**: [Check here](https://github.com/mosip/keycloak)

### Runtime Dependencies

Ensure the following artifacts are available in the classpath or loader path:

- `kernel-auth-adapter.jar` - For IAM authentication.
- `kernel-ref-idobjectvalidator.jar` - For ID object validation.
- `cache-provider.jar` - For caching support.

### Configuration

- Packet Manager uses configuration files from the **[mosip-config repository](https://github.com/mosip/mosip-config/tree/master)**.
- Refer to the tagged version corresponding to your release.
- Key configuration files:
    - [packet-manager-default.properties](https://github.com/mosip/mosip-config/blob/master/packet-manager-default.properties)
    - [application-default.properties](https://github.com/mosip/mosip-config/blob/master/application-default.properties)

## Installation

### Local Setup (for Development or Contribution)

1. Ensure the **Config Server** is running and accessible.

2. Clone the repository:

```text
git clone https://github.com/mosip/packet-manager.git
cd packet-manager
```

3. Build the project:

```text
cd commons-packet
mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip=true
```

4. Start the application:
    - Run via IDE or command line: `java -jar commons-packet-service/target/commons-packet-service-*.jar`

5. Verify Swagger is accessible at: `http://localhost:<port>/packetmanager/swagger-ui/index.html`

### Local Setup with Docker (Easy Setup for Demos)

#### Option 1: Pull from Docker Hub

Recommended for quick demos and testing.

```text
docker pull mosipid/kernel-packet-manager:1.3.0
```

Run the service:

```text
docker run -d -p 8086:8086 --name packet-manager mosipid/kernel-packet-manager:1.3.0
```

#### Option 2: Build Docker Images Locally

Recommended for developers.

1. Build the project (as shown in Local Setup).

2. Navigate to the service directory and build the Docker image:

```text
cd commons-packet/commons-packet-service
docker build -t packet-manager:local .
```

3. Run the service:

```text
docker run -d -p <port>:<port> --name <service-name> <service-name>
```

#### Verify Installation

Check that the container is running:

```text
docker ps
```

Access the services at `http://localhost:<port>` using the port mappings listed above.

## Deployment

### Kubernetes

To deploy Packet Manager on a Kubernetes cluster, refer to the [Sandbox Deployment Guide](https://docs.mosip.io/1.2.0/deploymentnew/v3-installation).

## Documentation

For additional details, refer to the documents listed below:

### API Documentation

API endpoints and Swagger documentation are available at: [MOSIP Packet Manager API Documentation](https://mosip.github.io/documentation/1.2.0/commons-packet-service.html).

### Product Documentation

For functional details and use cases, refer to: [Packet Manager Module Documentation](https://docs.mosip.io/1.2.0/modules/packet-manager).

## Contribution & Community

• To learn how you can contribute code to this application, [click here](https://docs.mosip.io/1.2.0/community/code-contributions).

• If you have questions or encounter issues, visit the [MOSIP Community](https://community.mosip.io/) for support.

• For any GitHub issues: [Report here](https://github.com/mosip/packet-manager/issues)

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).