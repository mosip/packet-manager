# Packet Manager Configuration Guide
## Overview
The guide here lists down some of the important properties that may be customised for a given installation. Note that the listing here is not exhaustive, but a checklist to review properties that are likely to be different from default.  If you would like to see all the properites, then refer to the files listed below.

## Configuration files
Packet Manager uses the following configuration files:
```
application-default.properties
packet-manager-default.properties
```

See [Module Configuration](https://docs.mosip.io/1.2.0/modules/module-configuration) for location of these files.

## Priority
The Packet Manager reads information from packet based on a configurable priority. If there are multiple packets present for same Id then Packet Manager decides which packet should get priority to fetch the information based on below property -
` packetmanager.default.priority=source:REGISTRATION_CLIENT\/process:BIOMETRIC_CORRECTION|NEW|UPDATE|LOST,source:RESIDENT\/process:ACTIVATED|DEACTIVATED|RES_UPDATE|RES_REPRINT `
## Providers
Packet Manager uses reader and writer provider to read and write packet. 

* Reader Provider: MOSIP by defult has a packet structure. But it provides support to read packet in different structure. The reader provider has to be configured in below format -
  * Default Reader Provider
` provider.packetreader.mosip=source:REGISTRATION_CLIENT,process:NEW|UPDATE|LOST|BIOMETRIC_CORRECTION,classname:io.mosip.commons.packet.impl.PacketReaderImpl ` 
  * New Reader Provider example -
 ` provider.packetreader.<the provider name, it can be any name>= <source, process and classname>`
 
 * Writer Provider: MOSIP by defult has a packet structure. But it provides support to write packet in different structure. The writer provider has to be configured in below format -
   * Default Writer Provider
` provider.packetwriter.mosip=source:REGISTRATION_CLIENT,process:NEW|UPDATE|LOST|BIOMETRIC_CORRECTION,classname:io.mosip.commons.packet.impl.PacketWriterImpl ` 
   * New Writer Provider example -
 ` provider.packetwriter.<the provider name, it can be any name>= <source, process and classname>`
 
 ## Object Store Connection
 By default MOSIP provides support for below 3 adapters to connect to object store:
 
 * S3 Adapter: Used for distributed object store
 ```
 object.store.s3.accesskey=******
object.store.s3.secretkey=*****
object.store.s3.url=${mosip.minio.url}
object.store.s3.region=${s3.region}
object.store.s3.readlimit=10000000
 ```
 * Posix Adapter: Used to connect to flat file system
 ```
 object.store.base.location=/home/mosip
 ```
 * Swift Adapter: Used for distributed object store
 ```
object.store.swift.username=test
object.store.swift.password=test
object.store.swift.url=http://localhost:8080
 ```
