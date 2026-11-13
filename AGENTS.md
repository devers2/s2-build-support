# S2BuildSupport AI 에이전트 개발 및 검증 절대 원칙 (AGENTS.md)

이 문서는 `s2-build-support` 저장소의 코드를 분석, 수정, 생성하는 모든 AI 에이전트가 준수해야 할 **절대 원칙 및 검증 가이드라인**입니다.

---

## 🚨 [CRITICAL RULE] 코드 수정 후 전체 빌드 및 검증 필수 실행

Java 소스 코드, Gradle 설정, 문서를 수정한 후에는 **사용자에게 완료를 보고하거나 커밋/푸시하기 전에 반드시 아래 검증 명령어를 실행하여 전수 통과(100% PASS)를 확인**해야 합니다.

### 필수 실행 명령어
```bash
./gradlew check
```

> ⚠️ 개별 임의 테스트만 실행하고 끝내지 마십시오. 반드시 `./gradlew check`를 통해 모든 플러그인 유효성 검증 및 단위 테스트가 100% 통과함을 검증해야 합니다.

---

## 1. 하위 호환성 및 부트스트래핑(Bootstrapping) 주의사항

1. **자체 플러그인 적용 불가**:
   - `s2-build-support`는 자기 자신을 빌드하는 중이므로, 컴파일되지 않은 자신의 플러그인(`io.github.devers2.buildsupport`)을 `build.gradle.kts`에 직접 apply할 수 없습니다.
   - Central Portal Zip 번들링 등 공통 로직은 `build.gradle.kts`의 인라인 구현과 `S2BuildUtils` 간에 동기화가 유지되어야 합니다.
2. **연관 프로젝트 영향도**:
   - `s2-util`과 `s2-kit`이 composite build(`includeBuild("../s2-build-support")`)로 이 프로젝트를 참조합니다.
   - `S2BuildUtils`의 공개 API 시그니처나 기본 동작을 변경할 때는 연관 프로젝트들의 빌드 영향도를 반드시 함께 확인하십시오.
3. **배포 안전성**:
   - 사용자가 명시적으로 배포를 요청하지 않는 한 임의로 배포 태스크(`publish`, `publishAllPublicationsToCentralPortalRepository` 등)를 실행하지 마십시오.
   - 평상시에는 `git commit` 및 `git push`만 수행합니다.

---

## 2. Java 버전 호환성 원칙 (Java 17 Baseline & Build Toolchain Management)

1. **자체 툴체인 호환성 (Java 17)**:
   - `s2-build-support` 플러그인은 다양한 CI/CD 및 Gradle 실행기 환경과의 호환성을 위해 JDK 17 툴체인(`libs.versions.java = 17`)을 기준으로 빌드됩니다.
   - 플러그인 소스코드 내에 Java 17을 초과하는 상위 버전 전용 문법이나 API를 사용해서는 안 됩니다.
2. **하위 프로젝트 빌드 컨벤션 지원**:
   - `S2BuildUtils.configureJavaCompatibility()` 등을 통해 하위 프로젝트(`s2-util`, `s2-kit`)가 JDK 21 툴체인으로 컴파일하더라도 바이트코드 출력은 Java 17(`--release 17`)로 엄격히 제한되도록 제어해야 합니다.

---

## 3. 인코딩 원칙

- 모든 소스 코드 및 설정 파일은 UTF-8 인코딩을 준수해야 합니다.

---

## 4. 언어 원칙 (문서·주석·커밋)

외부 사용자가 실제로 읽는 곳(가이드 문서·API 문서)만 영문/한글을 병기하고, 나머지는 한국어로 작성합니다.

| 대상 | 언어 |
|---|---|
| `README`·`GUIDE`·`MANUAL`·`CHANGELOG` 등 가이드 문서 | 영문(`*.md`)과 한글(`*.ko.md`) 쌍으로 작성하고 함께 갱신 |
| Javadoc·JSDoc (클래스·메서드·필드 등 API 문서 주석) | 영문 설명 먼저, 이어서 `<p><b>[한국어 설명]</b></p>` 아래 한글 설명 (기존 형식 유지) |
| 그 밖의 코드 주석 (`//`, 메서드 내부 블록 주석, 빌드 스크립트 주석) | 한국어만 |
| 커밋 메시지 | 한국어. Conventional Commits 접두어(`feat:`, `fix:`, `docs:`, `build:` …)와 식별자는 영어 그대로 — 예: `fix: 날짜 검증에서 null 처리 누락 수정` |
| 예외·로그 메시지 등 사용자에게 그대로 노출되는 문자열 | 영어 유지 (검색·이슈 보고에 쓰이는 공개 텍스트 — 다국어 메시지는 기존 i18n 구조를 따름) |
| 이슈·PR 답변 | 작성자의 언어로 |

- 기존 영어 주석·커밋 이력은 일부러 바꾸지 않습니다. 해당 코드를 수정할 때 그 주변 주석만 이 원칙에 맞춥니다.
