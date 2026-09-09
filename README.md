# S2BuildSupport Plugin

[English](README.md) | [한국어](README.ko.md)

[![Java CI](https://github.com/devers2/s2-build-support/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-build-support/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-build-support?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-build-support)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> An **opinionated Gradle build convention plugin** designed to standardize build logic, licensing, and publishing across personal S2 projects.

---

## 📖 Overview

The **s2-build-support** plugin standardizes Gradle build automation, repository publishing (Maven Central / Central Portal), license and copyright maintenance, and README version synchronization across the S2 project suite.

---

## 🔧 Installation

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### build.gradle.kts

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.2.1"
}
```

---

## 🚀 Usage

Applying the plugin automatically enables a small set of defaults that are safe for **any** Gradle Java project, regardless of whether (or how) it publishes:

- Gradle wrapper version consistency check
- UTF-8 encoding enforcement (compile / test / run / javadoc)
- `-parameters` compiler flag (keeps parameter names available for reflection-based frameworks)

Everything else is opt-in, provided as **static methods on `S2BuildUtils`** that you call explicitly from `build.gradle.kts`. They're split into two layers:

**General-purpose** (any Gradle Java project, publishing or not)

- `S2BuildUtils.configureJavaCompatibility(project)` — sets up the toolchain and source/target compatibility from the `javaVersion` / `releaseCompatibility` extra properties (`releaseCompatibility` is optional; if unset it defaults to `javaVersion`, so a single-version project doesn't need to set it at all)
- `S2BuildUtils.configureTestDefaults(project)` — JUnit Platform + extra console/IO encoding hardening, for projects that run JUnit 5 tests

**Library publishing** (a JAR library publishing to Maven Central via Central Portal)

- `S2BuildUtils.configureLibraryPublishing(project, name, description, repoUrl)` — one-call setup for the common case: artifact-id suffix, toolchain/compatibility, Javadoc/Sources JAR, a `"mavenJava"` publication with a standard POM (Apache 2.0 license, developer, SCM), and the Central Portal repository (incl. signing)
- `S2BuildUtils.applyStandardPom(publication, name, description, repoUrl)` — fills in the license/developer/SCM boilerplate on any `MavenPublication` (used internally by `configureLibraryPublishing`; call it directly for projects with their own publications, e.g. a `java-gradle-plugin`'s `pluginMaven`/marker publications)
- `S2BuildUtils.configureCentralPortalRepository(project)` / `configureGitHubPackagesRepository(project, owner, repo)` — repository registration only, if you need finer control than the one-call method above
- `S2BuildUtils.configureProject(project)` — the dynamic feature/packaging engine (dependency injection, source toggling, Shadow JAR, README sync). This is an orthogonal concern to publishing, so call it separately regardless of which publishing method you use
- `S2BuildUtils.updateReadmeWithVersionAndDependencies(project, patterns)` — automatically synchronizes project versions across README documentation and dependency snippets

A typical library that publishes a single artifact to Maven Central:

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.2.1"
    `java-library`
    `maven-publish`
    signing
}

import io.github.devers2.buildsupport.S2BuildUtils

extra["javaVersion"] = JavaVersion.VERSION_21
// Optional: compile with a newer JDK while targeting an older bytecode release.
extra["releaseCompatibility"] = JavaVersion.VERSION_17

S2BuildUtils.configureProject(project) // dependency injection, packaging, README sync
S2BuildUtils.configureLibraryPublishing(
    project,
    project.name,
    "My library description",
    "https://github.com/owner/repo"
)
S2BuildUtils.configureTestDefaults(project)
```

Other standalone utilities (`updateVersionInFile`, `updateServletImports`, `updateCopyright`, etc.) can be called the same way wherever needed. See the Javadoc on `S2BuildUtils` for the full list and their configuration options.

---

## ⚙️ Requirements

- **Java 17 or higher** is required to use this module.

---

## 📜 License & Copyright

This library is provided under the **Apache License 2.0**. You are free to use, modify, and distribute this software, provided that you comply with the obligations of the license (such as copyright notice and source code disclosure requirements). For detailed terms and conditions, please refer to the **[LICENSE](./LICENSE)** file.

- **Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)**
- Contact: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**Third-party Notice:** This project uses external libraries. For detailed third-party license notices, please refer to the **[licenses/NOTICE](./licenses/NOTICE)** file.

---

## 📦 Dependencies

This module utilizes the following high-quality open-source library:

- **Shadow Gradle Plugin**: Used for creating fat JARs and relocating dependencies (Licensed under Apache 2.0).

---

s2-build-support Version: 0.2.1 (2026-09-10)
