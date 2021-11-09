# OMP OPC UA Test Server based on Eclipse&nbsp;Milo™

This is an OPC UA test server implemented with Milo to test and verify different IoT connectivity scenarios in the
[OMP](https://open-manufacturing.org/).

This repository is dedicated to OMP IoT Connectivity Working Group

## Build and run locally

Run the following command from the root of the repository:

    mvn quarkus:dev

## Run locally using a containers

    podman run --rm -ti -p 12686 ghcr.io/ctron/omp-opcua-milo-test-server-jvm:latest

## Connecting

| Property | Value |
| - | - |
| **URL** | `opc.tcp://localhost:12686/milo` |
| **Username** | `milo` |
| **Password** | `open-by-default` |
