# s2-build-support AI 에이전트 원칙

## 필수: 수정 후 전체 검증
Java·Gradle 설정·문서를 수정했으면 완료 보고·커밋·push 전에 `./gradlew check` 전수 통과(플러그인 유효성 검증·단위 테스트)를 확인한다. 개별 테스트만으로 끝내지 않는다.

## 1. 부트스트래핑·연관 프로젝트·배포
- 자기 자신을 빌드하는 중이라 자신의 플러그인(`io.github.devers2.buildsupport`)을 `build.gradle.kts` 에 apply 할 수 없다.
  Central Portal Zip 번들링 등 공통 로직은 `build.gradle.kts` 인라인 구현과 `S2BuildUtils` 를 서로 맞춰 둔다.
- `s2-util`·`s2-kit` 이 composite build(`includeBuild("../s2-build-support")`)로 참조한다. `S2BuildUtils` 공개 API·기본 동작을 바꾸면 두 프로젝트의 빌드 영향을 함께 확인한다.
- 사용자가 요청하지 않으면 배포 태스크(`publish`, `publishAllPublicationsToCentralPortalRepository` 등)를 실행하지 않는다.
- 평소에는 `develop` 브랜치에서 commit·push 한다. `main` 은 Maven Central 배포본과 같아야 하므로, 사용자가 배포를 요청할 때만 `develop` 을 `main` 에 fast-forward 하고 버전 태그를 단다.

## 2. Java 버전
- 플러그인은 여러 CI·Gradle 실행 환경 호환을 위해 JDK 17 툴체인(`libs.versions.java = 17`)으로 빌드한다. Java 17 을 넘는 문법·API 를 쓰지 않는다.
- `S2BuildUtils.configureJavaCompatibility()` 등은 하위 프로젝트(`s2-util`, `s2-kit`)가 JDK 21 툴체인으로 컴파일해도 바이트코드를 Java 17(`--release 17`)로 제한해야 한다.

## 3. 인코딩
- 모든 소스·설정 파일은 UTF-8.

## 4. 언어 (문서·주석·커밋)
| 대상 | 언어 |
|---|---|
| `README`·`GUIDE`·`MANUAL`·`CHANGELOG` 등 가이드 문서 | 영문(`*.md`)·한글(`*.ko.md`) 쌍으로 함께 갱신 |
| Javadoc·JSDoc | 영문 설명 → `<p><b>[한국어 설명]</b></p>` → 한글 설명 (기존 형식) |
| 그 밖의 주석(`//`, 메서드 내부 블록 주석, 빌드 스크립트) | 한국어 |
| 커밋 메시지 | 한국어. 접두어(`feat:`, `fix:`, `docs:`, `build:` …)·식별자는 영어 — 예: `fix: 날짜 검증에서 null 처리 누락 수정` |
| 예외·로그 등 사용자에게 노출되는 문자열 | 영어 (다국어 메시지는 기존 i18n 구조) |
| 이슈·PR 답변 | 작성자의 언어 |

- 기존 주석·커밋 이력은 번역·정리 목적으로 바꾸지 않는다. 주석은 설명하는 코드가 바뀌었거나 틀렸을 때만 그 부분을 고치고, 고친 주석은 위 원칙의 언어로 쓴다.
