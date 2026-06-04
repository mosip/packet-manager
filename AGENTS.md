# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

**MOSIP Packet Manager** is the central module for creating, reading, validating, and managing registration packets across MOSIP's ID lifecycle. It sits between Registration Client/Resident Service (packet producers) and Registration Processor (packet consumer), abstracting packet structure, encryption, and object store concerns behind a clean SPI.

The module ships as two artifacts:
- **`commons-packet-manager`** — a library embedded in Registration Client and Resident Service for on-device packet operations.
- **`commons-packet-service`** — a Spring Boot REST service used by Registration Processor to read/write packets during server-side processing.

## Build Commands

All build commands run from `commons-packet/` (the Maven parent):

```bash
# Full build (skip Javadoc and GPG for local dev)
mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip=true

# Run tests only
mvn test

# Single test class
mvn test -Dtest=ClassName

# Single test method
mvn test -Dtest=ClassName#methodName

# Build a specific module from the parent
mvn clean install -pl commons-packet-service -am -Dmaven.javadoc.skip=true -Dgpg.skip=true

# Code coverage report (output: target/site/jacoco/index.html)
mvn clean verify

# SonarCloud analysis
mvn clean verify -Psonar
```

## Running the Service

The service requires a running Spring Cloud Config Server at the URL configured in `bootstrap.properties` (default: `http://localhost:51000/config`). Configuration is fetched from the [mosip-config](https://github.com/mosip/mosip-config) repository; the key files are `packet-manager-default.properties` and `application-default.properties`.

```bash
java -jar commons-packet/commons-packet-service/target/commons-packet-service-*.jar
```

Swagger UI: `http://localhost:8093/commons/v1/packetmanager/swagger-ui/index.html`

Three runtime JARs must be on the classpath (not bundled):
- `kernel-auth-adapter.jar` — adds MOSIP IAM headers to outbound REST calls
- `kernel-ref-idobjectvalidator.jar` — ID object schema validation
- `cache-provider.jar` — Spring cache backend

## Architecture

### Two-Module Split

```
commons-packet/
├── commons-packet-manager/   # Core library — SPI definitions, facades, PacketKeeper, crypto, OfflineConfig
└── commons-packet-service/   # Spring Boot app — REST controllers, PacketReaderService, PacketWriterService
```

### Provider SPI Pattern

The central design: facades (`PacketReader`, `PacketWriter`) do not contain format logic. They select a provider implementation at runtime by matching `source` and `process` values against Spring configuration properties.

- **`IPacketReader`** and **`IPacketWriter`** (`commons-packet-manager/spi/`) are the extension points.
- Providers are registered via Spring config properties under `provider.packetreader.*` and `provider.packetwriter.*`, each entry specifying `source:`, `process:`, and `classname:`.
- `PacketManagerConfig` reads these at startup, validates that the named beans exist, and exposes `List<IPacketReader>` / `List<IPacketWriter>` beans (`referenceReaderProviders` / `referenceWriterProviders`).
- `PacketHelper.isSourceAndProcessPresent()` does the runtime provider matching.

To add a new provider: implement `IPacketReader` or `IPacketWriter`, register the bean, and add the entry to `provider.packetreader.*` / `provider.packetwriter.*` properties.

### Layering

```
REST Controller (PacketReaderController / PacketWriterController)
    ↓
Service (PacketReaderService / PacketWriterService)         ← source/process resolution, field priority
    ↓
Facade (PacketReader / PacketWriter)                        ← Spring Security (@PreAuthorize), caching (@Cacheable)
    ↓
IPacketReader / IPacketWriter implementations              ← format-specific logic (zip, CBEFF, ID.json)
    ↓
PacketKeeper                                               ← object store I/O, encrypt/decrypt, integrity/signature
    ↓
ObjectStoreAdapter (Khazana SPI)                           ← S3 / POSIX / Swift backends
```

### PacketKeeper

`PacketKeeper` is the only layer that touches the object store. It:
1. Selects the `ObjectStoreAdapter` by matching `objectstore.adapter.name` to `SwiftAdapter`, `S3Adapter`, or `PosixAdapter`.
2. Selects the crypto service by matching `objectstore.crypto.name` to `OnlinePacketCryptoServiceImpl` (Key Manager REST call) or `OfflinePacketCryptoServiceImpl` (TPM-based, used on Registration Client).
3. On `getPacket`: fetches encrypted bytes → decrypts → verifies HMAC hash and JWS signature.
4. On `putPacket`: encrypts → stores → signs → stores metadata with encrypted hash.

Signature verification can be bypassed for testing via `packetmanager.packet.signature.disable-verification=true`.

### Source / Process Resolution

When a caller omits `source`, `PacketReaderService.getSourceAndProcess()` resolves it using the `identity-mapping.json` file fetched from the config server and the `packetmanager.default.priority` property (comma-separated `source:<name>/process:<name>` entries ordered by priority). Fields are looked up in the highest-priority container that contains them.

Process names may carry an iteration suffix (e.g., `CORRECTION-2`). `PacketHelper.getProcessWithoutIteration()` strips trailing `-<number>` before matching. The `info` API merges multi-iteration containers of the same source/process into one.

### Caching

`PacketReader` is annotated with `@RefreshScope` and uses Spring cache (`@Cacheable`):
- `packets` cache: documents, metaInfo, audits — keyed by `id-source-process`.
- `tags` cache: keyed by `id`.
- `info` cache: toggled by `packetmanager.cache.info.enabled` (default `false`; see comment in `PacketReader.java` about BIOMETRIC_CORRECTION race).

Cache is backed by `cache-provider.jar` (typically Hazelcast or a local implementation).

### Packet Structure on Disk

A registration packet is stored in the object store as multiple encrypted sub-packet ZIP files, one per `(source, process)` pair. Each sub-packet ZIP contains:
- `ID.json` — demographic identity fields
- `audit.json` — audit trail
- `packet_meta_info.json` — metadata (registration type, machine ID, center ID, schema version, etc.)
- `<field>_bio_CBEFF.xml` — CBEFF biometric data per biometric schema field
- Document files

Constants in `PacketManagerConstants` define all filenames and metadata keys.

## Key Configuration Properties

All runtime config lives in the Spring Cloud Config Server. Important properties in `packet-manager-default.properties`:

| Property | Purpose |
|---|---|
| `objectstore.adapter.name` | `S3Adapter`, `PosixAdapter`, or `SwiftAdapter` |
| `objectstore.crypto.name` | `OnlinePacketCryptoServiceImpl` or `OfflinePacketCryptoServiceImpl` |
| `packet.manager.account.name` | Object store bucket/account name |
| `provider.packetreader.<key>` | Reader provider registration: `source:X,process:Y,classname:FQCN` |
| `provider.packetwriter.<key>` | Writer provider registration |
| `packetmanager.default.priority` | Ordered source/process priority for field resolution |
| `packetmanager.default.read.strategy` | `DEFAULT_PRIORITY` (only supported strategy) |
| `packetmanager.cache.info.enabled` | Enable/disable info cache (default `false`) |
| `packetmanager.packet.signature.disable-verification` | Skip signature check (default `false`) |
| `registration.processor.identityjson` | Filename of `identity-mapping.json` on config server |

## CI/CD

GitHub Actions (`.github/workflows/push-trigger.yml`) triggers on push to `develop`, `master`, `release*`, and `MOSIP*` branches:
1. Maven build via `mosip/kattu` reusable workflow (Java 21).
2. Build and push `commons-packet-service` Docker image to Docker Hub.
4. SonarCloud analysis (project key: `mosip_packet-manager`).

Kubernetes deployment: `deploy/install.sh` installs the `packetmanager` Helm chart into the `packetmanager` namespace with Istio sidecar injection enabled.

## Testing

- Tests use JUnit 4, Mockito, and PowerMock.
- Surefire is configured with `--add-opens` for Java 21 module compatibility.
- SonarQube excludes DTOs, entities, config classes, exceptions, and SPIs from coverage; target is ~70% on business logic.
- Test bootstrap: `commons-packet-service/src/test/java/.../TestBootApplication.java` with `application-test.properties`.
