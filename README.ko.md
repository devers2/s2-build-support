# S2BuildSupport Plugin

🌐 [English](README.md) | **한국어**

[![Java CI](https://github.com/devers2/s2-build-support/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-build-support/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-build-support?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-build-support)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> 개인 S2 프로젝트 제품군 전반의 빌드 로직, 라이선스, 배포 표준화를 위해 설계된 **주관적(Opinionated) Gradle 빌드 컨벤션 플러그인**입니다.

---

## 📖 개요 (Overview)

**s2-build-support** 플러그인은 S2 프로젝트 전반의 Gradle 빌드 자동화, 중앙 저장소(Maven Central / Central Portal) 배포, 라이선스 및 저작권 유지보수, 그리고 README 내 버전 동기화 작업을 표준화하는 빌드 유틸리티입니다.

---

## 🔧 설치 (Installation)

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

## 🚀 사용법 (Usage)

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
- `S2BuildUtils.updateReadmeWithVersionAndDependencies(project, patterns)` — 프로젝트 버전을 README 문서 및 코드 블록 내 의존성 버전에 자동으로 동기화합니다

Maven Central에 아티팩트 하나를 배포하는 일반적인 라이브러리 예시:

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.2.1"
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

## ⚙️ 요구사항 (Requirements)

- 이 모듈을 사용하려면 **Java 17 이상**이 필요합니다.

---

## 📜 라이선스 및 저작권 (License & Copyright)

본 라이브러리는 **Apache License 2.0** 하에 제공됩니다. 사용자는 라이선스의 의무 사항(저작권 고지, 소스 코드 공개 범위 등)을 준수하는 조건 하에 자유롭게 사용, 수정 및 재배포가 가능합니다. 상세한 조건은 **[LICENSE](./LICENSE)** 파일을 반드시 확인해 주세요.

- **저작권 2020 - 2026 devers2 (이승수, 대한민국 대전)**
- 문의: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**제3자 라이브러리 고지:** 본 프로젝트는 외부 라이브러리를 사용합니다. 상세한 제3자 라이브러리 고지사항은 **[licenses/NOTICE](./licenses/NOTICE)** 파일을 참조해 주세요.

---

## 📦 의존성 (Dependencies)

본 모듈은 다음 오픈소스 라이브러리를 활용합니다:

- **Shadow Gradle Plugin**: Fat JAR 생성 및 의존성 재배치에 사용 (Apache 2.0 라이선스)

---

s2-build-support Version: 0.2.1 (2026-09-10)
