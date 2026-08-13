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
    id("io.github.devers2.buildsupport") version "0.1.0"
}
```

---

## 🚀 Usage (사용법)

### [English]

Applying the plugin only enables the baseline setup shared by every S2 project: Gradle wrapper version consistency check and UTF-8 encoding enforcement.

The rest of the build logic (dynamic dependency injection, source toggling, JAR/Shadow packaging, README version sync, and Central Portal / GitHub Packages publishing) is provided as **utility methods on `S2BuildUtils`** and must be invoked explicitly from your project's `build.gradle.kts`, since each project may need different configuration (`javaSourceRoot`, `activeFeatures`, `shadedPackagePrefix`, etc.):

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.1.0"
}

// Activates dependency injection, packaging (Standard/Shaded), README sync, and publishing setup.
io.github.devers2.buildsupport.S2BuildUtils.configureProject(project)
```

Other standalone utilities (`updateVersionInFile`, `updateServletImports`, `updateCopyright`, etc.) can be called the same way wherever needed. See the Javadoc on `S2BuildUtils` for the full list and their configuration options.

### [한국어]

플러그인을 적용하는 것만으로는 모든 S2 프로젝트에 공통되는 기본 설정(Gradle Wrapper 버전 정합성 검사, UTF-8 인코딩 강제)만 활성화됩니다.

나머지 빌드 로직(동적 의존성 주입, 소스 토글, JAR/Shadow 패키징, README 버전 동기화, Central Portal/GitHub Packages 배포 설정)은 프로젝트마다 필요한 설정(`javaSourceRoot`, `activeFeatures`, `shadedPackagePrefix` 등)이 다르기 때문에 **`S2BuildUtils`의 유틸리티 메서드**로 제공되며, 각 프로젝트의 `build.gradle.kts`에서 명시적으로 호출해야 합니다:

```kotlin
plugins {
    id("io.github.devers2.buildsupport") version "0.1.0"
}

// 의존성 주입, 패키징(Standard/Shaded), README 동기화, 배포 설정을 활성화합니다.
io.github.devers2.buildsupport.S2BuildUtils.configureProject(project)
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

s2-build-support Version: 0.1.0 (2026-01-19)
