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
   - `s2-util`과 `s2-support`가 composite build(`includeBuild("../s2-build-support")`)로 이 프로젝트를 참조합니다.
   - `S2BuildUtils`의 공개 API 시그니처나 기본 동작을 변경할 때는 연관 프로젝트들의 빌드 영향도를 반드시 함께 확인하십시오.
3. **배포 안전성**:
   - 사용자가 명시적으로 배포를 요청하지 않는 한 임의로 배포 태스크(`publish`, `publishAllPublicationsToCentralPortalRepository` 등)를 실행하지 마십시오.
   - 평상시에는 `git commit` 및 `git push`만 수행합니다.

---

## 2. 인코딩 원칙

- 모든 소스 코드 및 설정 파일은 UTF-8 인코딩을 준수해야 합니다.
