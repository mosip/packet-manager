# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

**Packet Manager** is a MOSIP module that creates, reads, validates, and manages registration packets across identity lifecycle processes. It supports multiple object store adapters (S3, POSIX, Swift) and is designed as a plugin-based, configuration-driven service.

## Build Commands

All build commands run from `commons-packet/`:

```bash
# Full build (skip GPG signing and javadoc for local dev)
mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip=true

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl commons-packet-manager
mvn test -pl commons-packet-service

# Run a single test class
mvn test -pl commons-packet-manager -Dtest=PacketKeeperTest

# Build with Sonar analysis
mvn clean install -Psonar

# Build Docker image
cd commons-packet/commons-packet-service
docker build -t packet-manager:local .
```

## Running the Service

The service depends on an external Spring Cloud Config Server at startup:

```bash
# Run the jar (requires config server)
java -jar commons-packet-service/target/commons-packet-service-1.3.1-SNAPSHOT.jar

# Service runs on port 8093 by default
# API base path: /commons/v1/packetmanager
# Swagger UI: http://localhost:8093/commons/v1/packetmanager/swagger-ui/index.html
```

Docker requires several runtime JARs injected at startup via `configure_start.sh`:
- `kernel-auth-adapter.jar` (IAM, required)
- `kernel-ref-idobjectvalidator.jar` (schema validation, required)
- `cache-provider.jar` (optional)

## Module Structure

This is a two-module Maven project under `commons-packet/`:

- **`commons-packet-manager`** — Core library (published as a JAR). Used directly by Registration Client and Resident Service. Contains all packet read/write/crypto/validation logic.
- **`commons-packet-service`** — Spring Boot REST wrapper around the library. Exposes the library's capabilities as HTTP endpoints.

## Architecture

### Plugin/Provider Pattern

Both readers and writers are configured via a `source:process:classname` pattern. `PacketManagerConfig` validates providers at startup:

```properties
provider.packetreader.mosip=source:registration,process:NEW|UPDATE|CORRECTION,classname:io.mosip.commons.packet.impl.PacketReaderImpl
provider.packetwriter.mosip=source:registration,process:NEW|UPDATE|CORRECTION,classname:io.mosip.commons.packet.impl.PacketWriterImpl
```

`PacketHelper.java` resolves the correct provider based on the source and process at runtime.

### Key Components (commons-packet-manager)

| Component | Role |
|-----------|------|
| `PacketKeeper.java` | Central orchestrator — manages packet lifecycle, caching, and object store interactions |
| `PacketReaderImpl.java` / `PacketWriterImpl.java` | Default SPI implementations |
| `PacketValidator.java` | Schema validation against ID schema registry |
| `OnlinePacketCryptoServiceImpl.java` | Encryption/decryption via Key Manager Service |
| `OfflinePacketCryptoServiceImpl.java` | Symmetric-only encryption (no Key Manager dependency) |
| `ZipUtils.java` | Packet compression/decompression |
| `IdSchemaUtils.java` | ID schema fetch and parsing |

SPI interfaces in `spi/`: `IPacketReader`, `IPacketWriter`, `IPacketCryptoService` — implement these to add new providers.

### Object Store Abstraction

Backed by the **Khazana** library. Adapter selected via:

```properties
objectstore.adapter.name=S3Adapter   # or PosixAdapter, SwiftAdapter
objectstore.crypto.name=OnlinePacketCryptoServiceImpl
```

### Packet Format

Packets are ZIP archives containing a manifest, demographic documents, biometric data (CBEFF format), and metadata. The structure is validated against a versioned ID schema fetched from the schema registry.

### REST Layer (commons-packet-service)

Controllers are thin — they delegate entirely to service classes:
- `PacketReaderController` → `PacketReaderService`
- `PacketWriterController` → `PacketWriterService`

Role-based access is configured per endpoint via properties:

```properties
mosip.role.commons-packet.putcreatepacket=REGISTRATION_PROCESSOR
```

### Caching

Spring Cache abstraction backed by Hazelcast Kubernetes. Packet data and schema lookups are cached aggressively. Cache provider is injected at runtime via `cache-provider.jar`.

### Configuration

Runtime configuration comes from Spring Cloud Config Server (`http://localhost:51000/config` by default). `bootstrap.properties` points to the config server; the actual property values live in an external `packet-manager-default.properties`.

## Testing Notes

- Uses **JUnit 4** (junit-vintage-engine) and **Mockito 3** / **PowerMock**
- Test properties in `src/test/resources/application-test.properties` use an in-memory H2 database and mock S3 credentials
- JVM open-module flags are required for tests — these are pre-configured in the Surefire plugin in `pom.xml`
- JaCoCo generates coverage reports to `target/site/jacoco/`

## Documentation

- Product docs: https://docs.mosip.io/1.2.0/modules/packet-manager
- API reference: https://mosip.github.io/documentation/1.2.0/commons-packet-service.html
- Configuration guide: `docs/configuration.md`
