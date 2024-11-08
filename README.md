[![Maven Package upon a push](https://github.com/mosip/packet-manager/actions/workflows/push_trigger.yml/badge.svg?branch=release-1.2.0.1)](https://github.com/mosip/packet-manager/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?branch=release-1.2.0.1&project=mosip_packet-manager&id=mosip_packet-manager2&metric=alert_status)](https://sonarcloud.io/dashboard?branch=release-1.2.0.1&id=mosip_packet-manager)

# Packet Manager

## About
* Its used by "Registration Client" and "Resident Service" to create packets.
* Its used by "Registration Processor" to read packet.

## Overview

* This repository contains the source code MOSIP Packet Manager module.  For an overview refer [here](https://docs.mosip.io/1.2.0/modules/packet-manager). This Module Exposes some API endpoints that we can explore in this [Swagger](https://api-internal.dev.mosip.net/commons/v1/packetmanager/swagger-ui/index.html?configUrl=/commons/v1/packetmanager/v3/api-docs/swagger-config#/).  
---
## Build & run (for developers)
The project requires JDK 21.0.3  and mvn version 3.9.6

1. Build and install:
   $ cd commons-packet <br>$ mvn clean install -Dgpg.skip=true
___
## Build Docker for a service
>$ cd `<service folder>` <br>
$ docker build -f Dockerfile
# Property file used by the application
We are using two property files that are accessible in this [repository](https://github.com/mosip/mosip-config/blob/develop/).
> 1: [application-default.properties](https://github.com/mosip/mosip-config/blob/develop/application-default.properties)<br> 2:[ packet-manager-default.properties](https://github.com/mosip/mosip-config/blob/develop/packet-manager-default.properties)

## Deployment 
To deploy services on Kubernetes cluster using Dockers. Refer to [Sandbox Deployment](https://docs.mosip.io/1.2.0/deploymentnew/v3-installation).
## Test 
Tests are performed for this repository using [DSL](https://github.com/mosip/mosip-automation-tests) automation testing.

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).