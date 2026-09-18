# Contributing

Contributions are welcome. Please open an issue or pull request on GitHub.

## Prerequisites

- JDK 17 or later
- Maven 3.9.6 or later

## Building

```bash
mvn clean verify
```

This compiles the plugin, runs the tests and static analysis, and produces `target/active-git-branches.hpi`.

## Running locally

```bash
mvn hpi:run
```

This starts a local Jenkins instance at `http://localhost:8080/jenkins` with the plugin installed.

## Running tests

```bash
mvn test
```

## Releases

Releases are published automatically through the Jenkins
[continuous delivery](https://www.jenkins.io/doc/developer/publishing/releasing-cd/) workflow.
Release notes are generated as GitHub releases, which are shown on the plugin page, so there is no separate changelog file.
