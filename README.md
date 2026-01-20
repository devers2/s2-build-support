# S2BuildSupport Plugin

## Overview (개요)

### [English]

The **s2-build-support** plugin is a plugin for Gradle builds.

### [한국어]

**s2-build-support** 플러그인은 Gradle 빌드를 위한 플러그인입니다.

---

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

## ⚙️ Requirements (요구사항)

### [English]

- **Java 17 or higher** is required to use this module.

### [한국어]

- 이 모듈을 사용하려면 **Java 17 이상**이 필요합니다.

---

## 📦 Dependencies (의존성)

This module utilizes the following high-quality open-source library:

- **Shadow Gradle Plugin**: Used for creating fat JARs and relocating dependencies (Licensed under Apache 2.0).

---

## 🔧 Installation (설치)

### [English]

#### settings.gradle

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

#### build.gradle

```groovy
plugins {
    id 'io.github.devers2.buildsupport' version '0.1.0'
}
```

### [한국어]

#### settings.gradle

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

#### build.gradle

```groovy
plugins {
    id 'io.github.devers2.buildsupport' version '0.1.0'
}
```

---

s2-build-support Version: 0.1.0 (2026-01-19)
