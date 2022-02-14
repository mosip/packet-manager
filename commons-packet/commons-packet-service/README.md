# Commons Packet Service

## About
* This service provides to read and write packets in object store.
* The service provides /validate API which is used by regproc.
* It provides API to add/update/delete tags associated with packet.

## Default context-path and port
Refer [`bootstrap.properties`](src/main/resources/bootstrap.properties)

## External dependency
* This service caches packet information to respond faster. Hence cache-provider jar to be passed during runtime. The default implementation uses Hazelcast cache.
* The idobject refence validatior to be passed during runtime.
