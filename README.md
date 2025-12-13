[![Maven Package upon a push](https://github.com/mosip/packet-manager/actions/workflows/push-trigger.yml/badge.svg?branch=master)](https://github.com/mosip/packet-manager/actions/workflows/push-trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?branch=master&project=mosip_packet-manager&id=mosip_packet-manager2&metric=alert_status)](https://sonarcloud.io/dashboard?branch=master&id=mosip_packet-manager)
# Packet Manager

## Overview

The **Packet Manager** module provides a secure and standardized system for:

- Creating encrypted packets with demographic and biometric information.
- Reading and extracting data from encrypted packets stored in object stores.
- Validating packet structure and compliance with ID schema.
- Managing packet tags for better organization and retrieval.
- Supporting multiple sources (Registration Client, Resident Service) and processes (NEW, UPDATE, LOST, BIOMETRIC_CORRECTION).
- Extensible reader/writer providers for custom packet structures.
- Integration with multiple object store adapters (S3, POSIX, Swift).

Packets are encrypted ZIP files containing demographic and biometric data handled throughout the registration and identity lifecycle processes in MOSIP.

For a complete functional overview and capabilities, refer to the **[official documentation](https://docs.mosip.io/1.2.0/modules/packet-manager)**.

## Features

- Packet creation and reading APIs
- Packet validation against ID schema
- Tag management (add/update/delete)
- Priority-based packet reading
- Multi-source and multi-process support
- Object store integration (S3, POSIX, Swift)
- Caching for faster response
- Encryption/decryption support

## Services

The Packet Manager module contains the following services:

1. **[commons-packet-manager](commons-packet/commons-packet-manager)** - Library used as JAR dependency by Registration Client, Resident Service, and Registration Processor for packet operations
2. **[commons-packet-service](commons-packet/commons-packet-service)** - REST service providing APIs for packet read/write operations in object stores

## Local Setup

The project can be set up in two ways:

1. [Local Setup (for Development or Contribution)](#local-setup-for-development-or-contribution)
2. [Local Setup with Docker](#local-setup-with-docker)

### Prerequisites

Before you begin, ensure you have the following installed:

- **JDK**: 21.0.3
- **Maven**: 3.9.6
- **Docker**: Latest stable version (for Docker-based setup)
- **PostgreSQL**: Latest (if database persistence is required)

### Runtime Dependencies

- `kernel-auth-adapter.jar` - IAM authentication adapter

### Configuration

Packet Manager uses the following configuration files that are accessible in this [repository](https://github.com/mosip/mosip-config).
Please refer to the required released tagged version for configuration:
- [application-default.properties](https://github.com/mosip/mosip-config/blob/master/application-default.properties)
- [packet-manager-default.properties](https://github.com/mosip/mosip-config/blob/master/packet-manager-default.properties)

For detailed configuration options, refer to the [Configuration Guide](docs/configuration.md).

## Installation

### Local Setup (for Development or Contribution)

1. Make sure the config server is running with the properties mentioned above configured properly.

2. Clone the repository:

```text
git clone https://github.com/mosip/packet-manager.git
cd packet-manager
```

3. Build the project:

```text
cd commons-packet
mvn clean install -Dgpg.skip=true
```

4. For library usage (`commons-packet-manager`):
   - Include as a Maven dependency in your project
   - The library is used by Registration Client, Resident Service, and Registration Processor

5. For running the service (`commons-packet-service`):

```text
cd commons-packet-service
java -jar target/commons-packet-service-<version>.jar
```

6. Verify Swagger is accessible at: `http://localhost:8086/app/generic/swagger-ui.html`

### Local Setup with Docker

The Packet Manager service can be deployed using Docker in two ways:

#### Option 1: Pull from Docker Hub

Recommended for quick setup and demos.

```text
docker pull mosipid/kernel-packet-manager:1.2.1
```

Run the service:

```text
docker run -d -p 8086:8086 \
  -e spring_config_label_env=develop \
  -e spring_config_url_env=http://config-server:8888 \
  -e active_profile_env=default \
  -e iam_adapter_url_env=<IAM_ADAPTER_JAR_URL> \
  -e kernel_ref_idobjectvalidator_url=<VALIDATOR_JAR_URL> \
  -e cache_provider_url_env=<CACHE_PROVIDER_JAR_URL> \
  --name packet-manager \
  mosipid/kernel-packet-manager:1.2.1
```

#### Option 2: Build Docker Image Locally

Recommended for contributors or developers.

1. Clone and build the project:

```text
git clone https://github.com/mosip/packet-manager.git
cd packet-manager/commons-packet
mvn clean install -Dgpg.skip=true
```

2. Build the Docker image:

```text
cd commons-packet-service
docker build -t packet-manager:local .
```

3. Run the service:

```text
docker run -d -p 8086:8086 \
  -e spring_config_label_env=develop \
  -e spring_config_url_env=http://config-server:8888 \
  -e active_profile_env=default \
  --name packet-manager \
  packet-manager:local
```


#### Verify Installation

```text
docker ps
curl http://localhost:8086/app/generic/actuator/health
```


## Deployment

### Kubernetes

To deploy Packet Manager on a Kubernetes cluster, refer to the [Sandbox Deployment Guide](https://docs.mosip.io/1.2.0/deploymentnew/v3-installation).

Install using Helm:

```text
cd deploy
./install.sh [kubeconfig]
```

Verify deployment:

```text
kubectl get pods -n packetmanager
kubectl logs -n packetmanager <pod-name>
```

## Upgrade

### Upgrade Steps

1. Backup your current configuration from config server
2. Backup database (if applicable)
3. Stop the current service
4. Update version in deployment scripts
5. Deploy the new version
6. Verify logs and health endpoints
7. Run validation tests

For major version upgrades, refer to the [MOSIP Release Notes](https://docs.mosip.io/1.2.0/releases) for breaking changes.

## Documentation

For more detailed documentation, check the [docs](docs) directory.

### API Documentation

API endpoints, base URL, and mock server details are available via Stoplight and Swagger documentation:

- **Swagger UI**: `http://localhost:8086/app/generic/swagger-ui.html`
- **GitHub Pages**: [MOSIP API Documentation](https://mosip.github.io/documentation/)

### Product Documentation

To learn more about Packet Manager from a functional perspective and use case scenarios, refer to our main documentation: [Packet Manager Module](https://docs.mosip.io/1.2.0/modules/packet-manager).

## Contribution & Community

• To learn how you can contribute code to this application, [click here](https://docs.mosip.io/1.2.0/community/code-contributions).

• If you have questions or encounter issues, visit the [MOSIP Community](https://community.mosip.io/) for support.

• For any GitHub issues: [Report here](https://github.com/mosip/packet-manager/issues)

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).