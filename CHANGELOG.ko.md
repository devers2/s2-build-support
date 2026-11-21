# 변경 이력

[English](./CHANGELOG.md) | **한국어**

## [1.0.1] - 미배포

### ⚠️ 동작 변경

- **GitHub Packages 스마트 배포**(`MavenPublishStrategy`)가 아티팩트 존재 여부를 확인하지 못하면(200·404 가 아닌 응답, 네트워크 오류)
  배포를 중단하고 원인을 알립니다. 이전에는 "없음"으로 보고 배포를 시도해 409 Conflict 등으로 실패하거나 원인이 가려졌습니다.
  `GitHubPackagesClient.checkArtifactExists`는 그대로입니다.

### 수정

- **패키지 재배치**가 `implementation` 의존성과 앞 두 단계가 같은 `api` 의존성 패키지(예: `org.apache` 아래 `org.apache.commons.lang3`)까지
  옮겨, 공개 API 의 api 클래스 참조가 바뀌어 사용하는 쪽에서 깨졌습니다. 이제 api 패키지는 재배치에서 제외합니다.
- **Shadow 배포 모드**가 `from(components.java)` Publication(`configureLibraryPublishing` 포함)에서 `Cannot publish module metadata`로
  실패했습니다(sources·javadoc jar 가 있을 때). 이제 그 Publication 은 Gradle Module Metadata 를 만들지 않고 POM(api 만 compile)으로 배포합니다.
  아티팩트를 바꾸지 못하던 `from components.java` 금지 검사(실제로는 동작하지 않던 코드)는 지웠습니다.
- `shadedPackagePrefix`와 Shadow 플러그인을 쓰면서 `maven-publish`가 없는 프로젝트가 설정 단계에서 실패했습니다(`PublishingExtension` 없음).
- GitHub Packages 스마트 배포가 존재 확인 경로를 Publication 이 아닌 프로젝트의 groupId 로 만들었습니다.
- `artifactTestClassNames`에 단순 이름이 같은 클래스(`a.Smoke`, `b.Smoke`)가 있으면 같은 이름의 태스크를 두 번 등록하다 실패했습니다.
  겹칠 때만 전체 이름으로 태스크를 만듭니다(`testArtifact_a_Smoke`).
- `REPO_BASE_URL`·`GITHUB_TOKEN`·`centralUsername`·`centralPassword`·`JAVA_SRC_ROOT`가 문자열이 아닌 값이면 `ClassCastException`이 났습니다.
- 이 플러그인 자체의 Central Portal 배포도 인증 정보의 따옴표를 지웁니다(`S2BuildUtils`와 동일).
- 의존성: Shadow 플러그인 9.6.1, Gradle Wrapper 9.9.1.
- **`S2TestLauncher`(`testArtifact`)**가 Gradle API 없는 클래스패스에서 `NoClassDefFoundError: org/gradle/api/JavaVersion`으로
  바로 실패했습니다(내부에서 `S2BuildUtils`를 불러옴). 이제 JDK 만 씁니다.
- **`LibrariesPublisher`**가 스캔 확장자와 관계없이 모든 파일을 `.jar`로 등록했습니다. 이제 파일의 실제 확장자로 등록합니다.
  JDK 업데이트 번호가 붙은 버전(`1.8.0_422`, `1.8.0_422+8`)도 문서대로 버전으로 인식합니다.
- **Central Portal 업로드**에 연결 시간 제한(60초)을 둡니다.
- **README 예시**의 `import`가 `plugins {}` 뒤에 있어 그대로 쓰면 Kotlin DSL 컴파일 오류가 났습니다. 맨 위로 옮겼습니다.
- `S2BuildUtils` Javadoc 의 `configureProject` 실행 순서(없는 `copyDependencies` 단계)와 README 동기화 설명을 실제 동작에 맞췄습니다.

## [1.0.0] - 2026-10-27

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
