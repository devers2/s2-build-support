# S2BuildSupport Plugin

## Overview (개요)

### [English]

The **s2-build-support** plugin is a plugin for Gradle builds.

### [한국어]

**s2-build-support** 플러그인은 Gradle 빌드를 위한 플러그인입니다.

---

## 🔧 Installation (설치)

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
    }
}
```

### build.gradle.kts

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.1.6"
}
```

---

## 🚀 Usage (사용법)

### [English]

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

A typical library that publishes a single artifact to Maven Central:

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.1.0"
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

### [한국어]

플러그인을 적용하면 배포 여부·방식과 무관하게 **어떤** Gradle Java 프로젝트에도 안전한 기본값 몇 가지가 자동으로 활성화됩니다:

- Gradle Wrapper 버전 정합성 검사
- UTF-8 인코딩 강제 (컴파일 / 테스트 / 실행 / Javadoc)
- `-parameters` 컴파일러 옵션 (리플렉션 기반 프레임워크가 파라미터명을 인식할 수 있도록)

나머지는 전부 선택 사항이며, `build.gradle.kts`에서 명시적으로 호출하는 **`S2BuildUtils`의 정적 메서드**로 제공됩니다. 두 계층으로 나뉩니다:

**범용** (배포 여부와 무관하게 어떤 Gradle Java 프로젝트에도 사용 가능)

- `S2BuildUtils.configureJavaCompatibility(project)` — `javaVersion`/`releaseCompatibility` extra 프로퍼티를 기반으로 툴체인과 source/target 호환성을 설정합니다 (`releaseCompatibility`는 선택 사항이며, 설정하지 않으면 `javaVersion`과 동일하게 처리되므로 단일 버전만 쓰는 프로젝트는 아예 설정할 필요가 없습니다)
- `S2BuildUtils.configureTestDefaults(project)` — JUnit 5 테스트를 쓰는 프로젝트를 위한 JUnit Platform 설정 + 추가 콘솔/IO 인코딩 강화

**라이브러리 배포** (Central Portal로 Maven Central에 배포하는 JAR 라이브러리)

- `S2BuildUtils.configureLibraryPublishing(project, name, description, repoUrl)` — 가장 흔한 경우를 위한 원콜(one-call) 설정: 아티팩트 ID 접미사, 툴체인/호환성, Javadoc/Sources JAR, 표준 POM(Apache 2.0 라이선스/개발자/SCM)이 채워진 `"mavenJava"` Publication, Central Portal 리포지토리(서명 포함)까지 한 번에 처리합니다
- `S2BuildUtils.applyStandardPom(publication, name, description, repoUrl)` — 어떤 `MavenPublication`에든 라이선스/개발자/SCM 상용구를 채워줍니다 (`configureLibraryPublishing`이 내부적으로 사용하며, 자체 Publication 체계를 쓰는 프로젝트 — 예: `java-gradle-plugin`의 `pluginMaven`/marker Publication — 에서는 직접 호출하면 됩니다)
- `S2BuildUtils.configureCentralPortalRepository(project)` / `configureGitHubPackagesRepository(project, owner, repo)` — 위 원콜 메서드보다 세밀한 제어가 필요할 때, 리포지토리 등록만 개별적으로 수행합니다
- `S2BuildUtils.configureProject(project)` — 동적 기능/패키징 엔진(의존성 주입, 소스 토글, Shadow JAR, README 동기화)입니다. 배포 방식과 무관한 별개의 관심사이므로, 어떤 배포 방식을 쓰든 별도로 호출해야 합니다

Maven Central에 아티팩트 하나를 배포하는 일반적인 라이브러리 예시:

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.1.0"
    `java-library`
    `maven-publish`
    signing
}

import io.github.devers2.buildsupport.S2BuildUtils

extra["javaVersion"] = JavaVersion.VERSION_21
// 선택 사항: 최신 JDK로 컴파일하면서 이전 Java 버전과 호환되는 바이트코드를 생성
extra["releaseCompatibility"] = JavaVersion.VERSION_17

S2BuildUtils.configureProject(project) // 의존성 주입, 패키징, README 동기화
S2BuildUtils.configureLibraryPublishing(
    project,
    project.name,
    "My library description",
    "https://github.com/owner/repo"
)
S2BuildUtils.configureTestDefaults(project)
```

그 외 개별 유틸리티(`updateVersionInFile`, `updateServletImports`, `updateCopyright` 등)도 필요한 위치에서 동일한 방식으로 호출하면 됩니다. 전체 목록과 설정 옵션은 `S2BuildUtils`의 Javadoc을 참고하세요.

---

## ⚙️ Requirements (요구사항)

### [English]

- **Java 17 or higher** is required to use this module.

### [한국어]

- 이 모듈을 사용하려면 **Java 17 이상**이 필요합니다.

---

## 📜 License & Copyright

### [English]

This library is provided under the **Apache License 2.0**. You are free to use, modify, and distribute this software, provided that you comply with the obligations of the license (such as copyright notice and source code disclosure requirements). For detailed terms and conditions, please refer to the **[LICENSE](./LICENSE)** file.

- **Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)**
- Contact: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**Third-party Notice:** This project uses external libraries. For detailed third-party license notices, please refer to the **[licenses/NOTICE](./licenses/NOTICE)** file.

### [한국어]

본 라이브러리는 **Apache License 2.0** 하에 제공됩니다. 사용자는 라이선스의 의무 사항(저작권 고지, 소스 코드 공개 범위 등)을 준수하는 조건 하에 자유롭게 사용, 수정 및 재배포가 가능합니다. 상세한 조건은 **[LICENSE](./LICENSE)** 파일을 반드시 확인해 주세요.

- **저작권 2020 - 2026 devers2 (이승수, 대한민국 대전)**
- 문의: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**제3자 라이브러리 고지:** 본 프로젝트는 외부 라이브러리를 사용합니다. 상세한 제3자 라이브러리 고지사항은 **[licenses/NOTICE](./licenses/NOTICE)** 파일을 참조해 주세요.

---

## 📦 Dependencies (의존성)

This module utilizes the following high-quality open-source library:

- **Shadow Gradle Plugin**: Used for creating fat JARs and relocating dependencies (Licensed under Apache 2.0).

---

s2-build-support Version: 0.1.6 (2026-08-14)
