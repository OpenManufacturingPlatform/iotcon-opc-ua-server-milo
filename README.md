# OMP OPC UA Test Server based on Eclipse Milo™

This repository is dedicated to OMP IoT Connectivity Working Group

## Build and run locally

    mvn quarkus:run

## Run locally using container

    podman run ghcr.io/ctron/omp-opca-milo-test-server:latest

## Connecting

| Property | Value |
| - | - |
| **URL** | `opc.tcp://localhost:12686/milo` |
| **Username** | `milo` |
| **Password** | `open-by-default` |
