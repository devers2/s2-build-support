# 변경 이력

[English](./CHANGELOG.md) | **한국어**

## [1.0.0] - 미배포

첫 안정 버전입니다. 이후 API 를 고정하고, 오류 수정과 Gradle 새 버전 대응만 합니다.

### ⚠️ 동작 변경

- **`configureJavaCompatibility`가 `--release`로 컴파일합니다.** `releaseCompatibility`가 툴체인보다 낮으면(예: JDK 21 로 Java 17
  바이트코드) `List.getFirst()` 같은 새 JDK API 호출이 컴파일 오류가 됩니다. 이전에는 `-source`/`-target`만 지정해 이런 호출이
  컴파일되고 이전 버전 런타임에서야 실패했습니다.
- **Central Portal 배포가 성공으로 보이지 않고 실패합니다**: 인증 정보가 없을 때, 아티팩트나 서명(`.asc`)이 없을 때, 업로드가 거부될 때.
  이전에는 인증 정보가 없으면 경고만 남겼고, 서명 없는 파일은 조용히 빠졌으며, 이 플러그인 자체 배포에서는 업로드 거부도 출력만 했습니다.
- **Central 번들은 Publication 의 아티팩트**(`artifactId-version[-classifier].ext`)**와 POM 만 담습니다.** 이전에는 이름에 버전이
  들어간 `build/libs`의 모든 파일을 골랐습니다.
- **Javadoc 은 public·protected 멤버만 담습니다**(`-private` 제거). 제목은 "S2Util API Documentation" 대신 프로젝트 이름과 버전입니다.
- **`BuildVariantsTask` 삭제** (여러 변형을 빌드하지 않게 된 뒤 사용처 없음).

### 수정

- **Gradle 10 대응:** `excludedSources`, `activeFeatures`, `skipPackaging`, `shadedPackagePrefix`, `buildFatJar`,
  `artifactTestClassNames` 설정을 `Project.hasProperty`/`property`로 읽어 부모 프로젝트에서 암묵적으로 찾았습니다(비권장, Gradle 10
  에서 오류). 이제 자기 프로젝트의 extra 속성 → 루트 프로젝트의 extra 속성 → Gradle 속성(`gradle.properties`, `-P`) 순으로 읽습니다.
- **테스트·실행 인코딩:** `-Dsun.jnu.encoding=UTF-8`(`configureTestDefaults`)과 애플리케이션 실행 태스크의 `-Dfile.encoding=UTF-8`이
  `getJvmArgs()`가 돌려주는 사본에 추가되어 실제 JVM 에 전달되지 않았습니다. 이제 `jvmArgs(...)`와 `defaultCharacterEncoding`을 쓰며,
  `application` 플러그인을 나중에 적용해도 실행 태스크에 반영됩니다.
- **소스 토글이 덮어쓰지 않습니다:** 기능을 켜고 끌 때 `.java` ↔ `.java.txt` 이름 바꾸기 결과를 확인하지 않았고, 리눅스에서는 대상 파일을
  덮어써 수정한 `X.java`가 오래된 `X.java.txt`로 바뀔 수 있었습니다. 이제 둘 다 있으면 바꾸지 않고 경고합니다.
- **README 동기화**는 내용이 바뀐 경우에만 파일을 씁니다.
- **서명**을 `publishToMavenLocal`에는 요구하지 않습니다.
- **`S2TestLauncher`**가 JUnit Platform 1.10 / 6.x 에서 콘솔 런처의 `execute` 하위 명령을 씁니다.
- **`GitHubPackagesClient`**가 빈 `Authorization` 헤더를 보내지 않고, 비권장 `new URL(String)`을 쓰지 않습니다.
- 디버그 출력("[패키징 디버그] ...")을 debug 로그 수준으로 옮겼습니다.
