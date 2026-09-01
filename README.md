# Keycloak Spring Boot Starter

A reusable Spring Boot starter for integrating applications with Keycloak.

## Status

🚧 **Work in Progress**

This project is currently being extracted from an existing application into a reusable Spring Boot starter.

The API, configuration properties, and supported Keycloak features are subject to change until the first stable release.

## Goals

The starter is intended to:

- Integrate Keycloak into Spring Boot applications.
- Allow consumers to provide their own Keycloak configuration.
- Initialize the configured Keycloak realm, clients, roles, and required permissions at application startup.
- Fail application startup when required configuration is missing or Keycloak initialization fails.
- Provide a simple public API for authentication and user management.
- Keep Keycloak implementation details internal to the starter.

## Requirements

- Java 21
- Spring Boot 3.4.x
- A running Keycloak server

## Usage

Usage and configuration instructions will be added once the starter API and configuration contract are finalized.

## Development

Build the project using the Maven wrapper:

```bash
./mvnw clean package