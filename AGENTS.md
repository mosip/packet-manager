# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

**MOSIP Packet Manager** is the central module for creating, reading, validating, and managing registration packets across MOSIP's ID lifecycle. It sits between Registration Client/Resident Service (packet producers) and Registration Processor (packet consumer), abstracting packet structure, encryption, and object store concerns behind a clean SPI.

The module ships as two artifacts:
- **`commons-packet-manager`** — a library embedded in Registration Client and Resident Service for on-device packet operations.
- **`commons-packet-service`** — a Spring Boot REST service used by Registration Processor to read/write packets during server-side processing.

## Agent Rules (speed / token budget)

- **Use the Repository Map below instead of exploring.** No `ls`/`find`/`tree` and no search subagents. Open the exact file you need.
- Grep for the method first, then read only that range. Large files are `PacketReaderImpl`, `PacketReaderService`, `PacketWriterImpl` and `PacketValidator` (450–620 lines).
- Never read `target/`, `licenses/`, `THIRD-PARTY-NOTICES`, `CREDITS`, `NOTICE`, `LICENSE`, `.travis.yml`, `.lgtm.yml` or `helm/**/converters/`. DTOs, exceptions and constants are boilerplate; open them only when a field matters.
- Filter Maven output: `mvn -q ... 2>&1 | grep -E '^\[ERROR\]|Tests run:.*Fail'`. For failures, read `*/target/surefire-reports/*.txt`.
- To verify, build one module (`-pl <module> -am`) and one test class (`-Dtest=`). Add `-o` when dependencies are already cached.
- Work inline. Spawn subagents only when the user asks.
- Keep diffs minimal: edit in place and don't move or reformat unrelated content.

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

Two runtime JARs must be on the classpath (not bundled); `configure_start.sh` downloads them into `loader.path`:
- `kernel-ref-idobjectvalidator.jar` — ID object schema validation
- `cache-provider.jar` — Spring cache backend

## Architecture

### Repository Map

```
packet-manager/
├── docs/configuration.md          config property reference
├── .github/workflows/             push-trigger.yml (build + docker + sonar via mosip/kattu)
├── deploy/*.sh, helm/packetmanager/   helm install into ns packetmanager
└── commons-packet/                aggregator pom (not the modules' parent) — run mvn here
    ├── commons-packet-manager/    LIBRARY  io.mosip.commons.packet — own versions in pom.xml <properties>
    │   ├── facade/                PacketReader, PacketWriter   (@PreAuthorize, @Cacheable, @RefreshScope)
    │   ├── spi/                   IPacketReader, IPacketWriter, IPacketCryptoService
    │   ├── impl/                  PacketReaderImpl, PacketWriterImpl (zip / ID.json / CBEFF),
    │   │                          OnlinePacketCryptoServiceImpl (keymanager REST), OfflinePacketCryptoServiceImpl (TPM)
    │   ├── keeper/PacketKeeper    ONLY object-store I/O + encrypt/decrypt/sign/verify
    │   ├── config/                PacketManagerConfig (provider registry), PacketReaderAndWriterConfig, OfflineConfig, AsyncConfig
    │   ├── util/                  PacketHelper (source/process match), PacketManagerHelper, PacketValidator, IdSchemaUtils, ZipUtils
    │   ├── constants/             PacketManagerConstants (file names, meta keys), ErrorCode, PacketUtilityErrorCodes
    │   └── test/…/test/           facade/, impl/, keeper/, dto/packet/, config/{TestConfig,TestSecurityConfig}
    └── commons-packet-service/    BOOT APP  io.mosip.commons.packetmanager — own versions in pom.xml <properties>
        ├── PacketServiceApp       @SpringBootApplication (excludes unused kernel-core auto-configs)
        ├── controller/            PacketReaderController, PacketWriterController, PacketServiceControllerAdvice
        ├── service/               PacketReaderService (source/process + field priority), PacketWriterService
        ├── config/                PacketServiceConfig, PacketSwaggerConfig, OpenApiProperties
        ├── resources/             bootstrap.properties (config server, springdoc), logback.xml
        └── test/…/test/           TestBootApplication (same excludes as app), controller/, service/
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
3. SonarCloud analysis (project key: `mosip_packet-manager`).

Kubernetes deployment: `deploy/install.sh` installs the `packetmanager` Helm chart into the `packetmanager` namespace with Istio sidecar injection enabled.

## Testing

- Tests use JUnit 4, Mockito, and PowerMock.
- Surefire is configured with `--add-opens` for Java 21 module compatibility.
- SonarQube excludes DTOs, entities, config classes, exceptions, and SPIs from coverage; target is ~70% on business logic.
- Test bootstrap: `commons-packet-service/src/test/java/.../TestBootApplication.java` with `src/test/resources/application.properties`. Manager tests use `application-test.properties`.
- Spring 7 doesn't initialise `@Mock` under `SpringRunner`. Pure-mock tests use `@RunWith(MockitoJUnitRunner.Silent.class)`.
- Spring-context tests use `@MockitoBean` (`@MockBean` was removed). `spring-boot-webmvc-test` and `spring-boot-security-test` are required; without the latter MockMvc returns 401.

## Dependencies

- No `kernel-bom`. Each module imports the `spring-boot-dependencies` and `spring-cloud-dependencies` BOMs. Versions live in each module's grouped `<properties>` (read them there). Keep both modules in sync and don't add a shared parent POM.
- Give every MOSIP dependency a `${property}` version. When an artifact has no snapshot build, use its latest release candidate.
- `kernel-core` bundles idobjectvalidator, logger-logback, pdfgenerator, dataaccess-hibernate, idgenerator, idvalidator, templatemanager and websub. Never add those jars separately, and exclude them where another MOSIP artifact pulls them in.
- `PacketServiceApp` and `TestBootApplication` exclude every kernel-core auto-config except `IdObjectValidatorConfig`. Keep both lists identical.
- `kernel-auth-adapter` is a fat jar that bundles its own copies of Spring, Netty, Jackson, etc.:
  - Service: packaged inside the service jar. Only its `io/mosip/kernel/auth/**` and `io/mosip/kernel/openid/**` classes are unpacked; the jar itself is kept out of `BOOT-INF/lib`. Never add it as a plain packaged dependency, or its bundled classes will override the real Spring jars.
  - Packet manager library (`commons-packet-manager`): must not depend on it. `kernel-keymanager-service` brings it in transitively, so it is excluded there. The host application (e.g. `commons-packet-service`, admin, Registration Client) provides it.
- Don't upgrade `jaxb-runtime` to 4.x while the CBEFF/biometrics APIs use `javax.xml.bind`. Don't move the manager's `mockito-core` past what PowerMock supports.
