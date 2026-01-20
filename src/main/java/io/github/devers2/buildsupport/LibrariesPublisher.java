/**
 * S2Util Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.buildsupport;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * Publisher class for registering JAR files in the {@code libs} directory as Maven Publications.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * libs 디렉토리의 JAR 파일들을 Maven Publication으로 등록하는 퍼블리셔 클래스입니다.
 * <p>
 * {@code libs/} 디렉토리에 있는 서드파티 JAR 파일들을 자동으로 스캔하여 Maven Publication으로 등록합니다.
 * 각 JAR 파일명에서 아티팩트명과 버전을 추출하고, 허용된 Classifier 규칙에 따라 적절히 그룹화하여 배포합니다.
 * </p>
 * <p>
 * 주요 기능:
 * </p>
 * <ul>
 * <li>JAR 파일명 파싱 및 아티팩트 정보 추출</li>
 * <li>Classifier 기반 아티팩트 그룹화</li>
 * <li>Maven Publication 자동 등록</li>
 * </ul>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 */
public class LibrariesPublisher {

    /**
     * Scans files in specified directories and registers them as Maven Publications.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 지정된 디렉토리의 파일들을 스캔하여 Maven Publication으로 등록합니다.
     * <p>
     * 처리 과정:
     * </p>
     * <ol>
     * <li>지정된 디렉토리들의 모든 파일을 스캔</li>
     * <li>파일명에서 classifier, 아티팩트명, 버전 추출</li>
     * <li>동일한 artifactId:version을 가진 파일들을 그룹화</li>
     * <li>각 그룹을 Maven Publication으로 등록</li>
     * </ol>
     *
     * @param project             The Gradle project instance | Gradle 프로젝트 객체
     * @param fileScanRules       Array of scan rules [Directory, Extension] | 스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers  Array of allowed classifier rules [Classifier, Separator] | 허용된 classifier 규칙 배열
     * @param exceptionalVersions Array of non-standard version strings | 예외적인 버전 형태 배열 (각 항목은 [classifier명, 구분자] 형태, 예: {{"for_bcprov", "_"}} → 파일명이 _for_bcprov로 끝나는 경우 classifier로 인식)
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     */
    public static void registerPublications(Project project, Object[][] fileScanRules, String[][] allowedClassifiers, String[] exceptionalVersions) {
        registerPublicationsInternal(project, fileScanRules, allowedClassifiers, exceptionalVersions);
    }

    /**
     * Scans files and registers them as Maven Publications.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 파일들을 스캔하여 Maven Publication으로 등록합니다.
     *
     * @param project            The Gradle project instance | Gradle 프로젝트 객체
     * @param fileScanRules      Array of scan rules | 스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers Array of allowed classifiers | 허용된 classifier 규칙 배열
     */
    public static void registerPublications(Project project, Object[][] fileScanRules, String[][] allowedClassifiers) {
        registerPublicationsInternal(project, fileScanRules, allowedClassifiers, null);
    }

    /**
     * Internal implementation for registering publications.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 출판물 등록을 위한 내부 구현 메서드입니다.
     *
     * @param project             The Gradle project instance | Gradle 프로젝트 객체
     * @param fileScanRules       Array of scan rules | 스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers  Array of allowed classifiers | 허용된 classifier 규칙 배열 (각 항목은 [classifier명, 구분자] 형태, 예: {{"for_bcprov", "_"}} → 파일명이 _for_bcprov로 끝나는 경우 classifier로 인식)
     * @param exceptionalVersions Array of non-standard version strings | 예외적인 버전 형태 배열
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     */
    private static void registerPublicationsInternal(Project project, Object[][] fileScanRules, String[][] allowedClassifiers, String[] exceptionalVersions) {
        // artifactId:version을 키로 하는 아티팩트 맵
        Map<String, List<ArtifactItem>> artifactsMap = new HashMap<>();

        // 모든 파일을 수집
        List<File> allFiles = new ArrayList<>();

        // 각 스캔 규칙에 따라 파일 수집
        if (fileScanRules != null && fileScanRules.length > 0) {
            for (Object[] rule : fileScanRules) {
                if (rule == null || rule.length < 2)
                    continue;

                Object dirObject = rule[0];
                Object extObject = rule[1];

                if (!(dirObject instanceof File) || !(extObject instanceof String)) {
                    S2BuildUtils.warn(
                            project,
                            "⚠️ [LibsPublishHelper] 잘못된 규칙 형식입니다. [File, String] 기대됨.",
                            "⚠️ [LibsPublishHelper] Invalid rule format. Expected [File, String]."
                    );
                    continue;
                }

                File scanDir = (File) dirObject;
                String extension = (String) extObject;

                if (!scanDir.exists() || !scanDir.isDirectory()) {
                    S2BuildUtils.warn(
                            project,
                            "⚠️ [LibsPublishHelper] 디렉토리를 찾을 수 없습니다: " + scanDir.getAbsolutePath(),
                            "⚠️ [LibsPublishHelper] Directory not found: " + scanDir.getAbsolutePath()
                    );
                    continue;
                }

                // 지정된 확장자로 파일 필터링
                File[] files = scanDir.listFiles((dir, name) -> name.endsWith("." + extension));

                if (files != null) {
                    for (File file : files) {
                        allFiles.add(file);
                    }
                }
            }
        }

        if (allFiles.isEmpty()) {
            S2BuildUtils.warn(
                    project,
                    "⚠️ [LibsPublishHelper] 배포할 파일을 찾지 못했습니다.",
                    "⚠️ [LibsPublishHelper] No files found to publish."
            );
            return;
        }

        // 각 파일 처리
        for (File jarFile : allFiles) {
            String fileName = jarFile.getName();
            // 파일 확장자 제거 (마지막 . 이후)
            int lastDot = fileName.lastIndexOf('.');
            String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
            String distClassifier = null;

            if (allowedClassifiers != null && allowedClassifiers.length > 0) {
                // Classifier 규칙 적용
                for (String[] rule : allowedClassifiers) {
                    String clf = rule[0];
                    String sep = rule[1];
                    String pattern = sep + clf;

                    if (baseName.endsWith(pattern)) {
                        String before = baseName.substring(0, baseName.length() - pattern.length());
                        VersionInfo verCheck = extractVersion(before, exceptionalVersions);
                        if (verCheck.hasVersion) {
                            baseName = before;
                            distClassifier = clf;
                            break;
                        }
                    }
                }
            }

            // 버전 정보 추출 및 아티팩트 ID 생성
            VersionInfo verInfo = extractVersion(baseName, exceptionalVersions);
            String distArtifactId = toMavenArtifactId(verInfo.baseName);
            String distVersion = verInfo.version != null ? verInfo.version : project.getVersion().toString();

            // 그룹화 키: artifactId:version
            String key = distArtifactId + ":" + distVersion;
            artifactsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(
                    new ArtifactItem(jarFile, distClassifier, distArtifactId, distVersion)
            );
        }

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

        // 각 그룹에 대해 Publication 생성
        artifactsMap.forEach((key, items) -> {
            ArtifactItem firstItem = items.get(0);
            String distArtifactId = firstItem.artifactId;
            String distVersion = firstItem.version;
            String pubName = (distArtifactId + "_" + distVersion).toLowerCase().replaceAll("[^a-z0-9]", "_");

            publishing.getPublications().create(pubName, MavenPublication.class, publication -> {
                publication.setGroupId(project.getGroup().toString());
                publication.setArtifactId(distArtifactId);
                publication.setVersion(distVersion);

                // 동일 그룹의 모든 아티팩트 추가 (classifier 포함)
                for (ArtifactItem item : items) {
                    publication.artifact(item.file, artifact -> {
                        artifact.setExtension("jar");
                        if (item.classifier != null) {
                            artifact.setClassifier(item.classifier);
                        }
                    });
                }

                publication.pom(pom -> {
                    pom.getName().set(distArtifactId);
                    pom.getDescription().set("Republished from libs (GAV: " + distArtifactId + ":" + distVersion + ")");
                });
            });

            S2BuildUtils.info(
                    project,
                    "등록된 그룹 ▶ " + distArtifactId + ":" + distVersion + " (개수: " + items.size() + ")",
                    "Registered Group ▶ " + distArtifactId + ":" + distVersion + " (Count: " + items.size() + ")"
            );
            for (ArtifactItem item : items) {
                S2BuildUtils.info(
                        project,
                        "  - 파일: " + item.file.getName() + " (Classifier: " + item.classifier + ")",
                        "  - File: " + item.file.getName() + " (Classifier: " + item.classifier + ")"
                );
            }
        });
    }

    /**
     * Converts a string to Maven artifact ID compliant kebab-case.
     *
     * 파일명을 Maven 아티팩트 ID 규격의 kebab-case로 변환
     *
     * <p>
     * 이 메서드는 일반 kebab-case 변환과 달리 Maven 아티팩트 ID 규격을 엄격히 따릅니다.
     * 최종 결과물은 소문자, 숫자, dash(-)만 포함하며 dash로 시작하거나 끝나지 않습니다.
     * </p>
     *
     * <p>
     * 상세 변환 규칙 (순서대로 적용):
     * </p>
     * <ol>
     * <li>CamelCase 구분: MyLibrary → My-Library</li>
     * <li>소문자+대문자 경계 구분: abc1Def → abc1-Def</li>
     * <li>밑줄을 dash로 변환: my_lib → my-lib</li>
     * <li>전체 소문자 변환: My-Library → my-library</li>
     * <li>허용되지 않는 문자를 dash로 치환: my.lib → my-lib (a-z, 0-9, - 만 허용)</li>
     * <li>연속된 dash 병합: my--lib → my-lib</li>
     * <li>양 끝 dash 제거: -mylib- → mylib</li>
     * </ol>
     *
     * <p>
     * 변환 예시:
     * </p>
     * <ul>
     * <li>bcprov-jdk18on → bcprov-jdk18on (이미 올바른 형식)</li>
     * <li>MyAwesomeLib → my-awesome-lib</li>
     * <li>some_library_v2 → some-library-v2</li>
     * <li>lib.name.1.0 → lib-name-1-0</li>
     * </ul>
     *
     * @param input The input string | 변환할 문자열 (보통 JAR 파일의 베이스 이름)
     * @return Kebab-case artifact ID | kebab-case 아티팩트 ID
     */
    public static String toMavenArtifactId(String input) {
        if (input == null || input.isBlank())
            return "";
        return input.trim()
                .replaceAll("(.)([A-Z][a-z]+)", "$1-$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("_+", "-")
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Regex pattern for strict version string validation.
     *
     * 엄격한 버전 문자열 검증을 위한 정규표현식 패턴
     * <p>
     * 이 패턴은 Maven, Gradle, Spring Boot, JDK 등에서 실제로 사용되는 버전 형식만 정확히 허용하며,
     * 비표준이거나 의미 없는 버전 문자열은 철저히 차단한다.
     * </p>
     *
     * <h3>허용되는 버전 예시</h3>
     * <ul>
     * <li>{@code 1.0} → 기본 릴리스 버전</li>
     * <li>{@code v1.2.3}, {@code V2.0.1} → Git 태그 스타일 (v 접두사 허용)</li>
     * <li>{@code 1.0.0}, {@code 2.3.4.5} → SemVer 표준 숫자 버전</li>
     * <li>{@code 1.0.0-RC1}, {@code 2.0.0-rc2}, {@code 3.1.0-BETA5} → Release Candidate, Beta</li>
     * <li>{@code 1.0.0-ALPHA}, {@code 1.0.0-alpha12} → Alpha 버전</li>
     * <li>{@code 1.0.0-M1}, {@code 1.0.0-m3} → Milestone</li>
     * <li>{@code 1.0.0-SNAPSHOT}, {@code 2.1.0-final} → 개발/최종 릴리스 태그</li>
     * <li>{@code 1.0.0+20251203}, {@code 17.0.12+7}, {@code 1.8.0_422+8} → 빌드 메타데이터 (JDK, CI 필수!)</li>
     * <li>{@code v1.0.0-RC1+build.123} → v 접두사 + 프리릴리스 + 메타데이터 조합</li>
     * </ul>
     *
     * <h3>차단되는 잘못된 예시 (의도된 대로 차단됨)</h3>
     * <ul>
     * <li>{@code 1} → 점(.)과 Minor 버전 없음</li>
     * <li>{@code 1.0-jdk17}, {@code 1.0-openjdk21} → 비표준 qualifier</li>
     * <li>{@code 1.0-hello}, {@code 1.0-test} → 의미 없는 태그</li>
     * <li>{@code 1.0-RC.1}, {@code 1.0-rc.2} → npm 스타일 점 구분자 (자바에선 사용 안 됨)</li>
     * <li>{@code 1.0-SNAPSHOT1} → SNAPSHOT 뒤에 숫자 붙음 금지</li>
     * <li>{@code 1.0-alpha-abc} → 키워드 뒤 추가 문자열 금지</li>
     * <li>{@code 2025}, {@code latest}, {@code stable} → 숫자.숫자 형태 아님</li>
     * </ul>
     */
    private static final Pattern STRICT_VERSION_PATTERN = Pattern.compile(
            // 1. v 접두사 선택적
            "^[vV]?" +

            // 2. 숫자.숫자 필수 (1.0 이상)
                    "\\d+(?:\\.\\d+)+" +

                    // 3. 선택적 프리릴리스 태그 (-로 시작)
                    "(?:-" +
                    "(?:" +
                    "SNAPSHOT|FINAL|RELEASE" + // 정확한 키워드
                    "|" +
                    "(?:ALPHA|BETA|RC|MILESTONE|M|B|A)\\d*" + // RC1, beta5 등
                    ")" +
                    ")?" +

                    // 4. 선택적 빌드 메타데이터 → 반드시 허용해야 함!
                    // 예: +20251203, +8, +sha.1a2b3c, +jdk-17 등
                    "(?:\\+[\\da-zA-Z.-]+)?" +

                    "$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts version information from a filename.
     *
     * 파일명에서 버전 정보를 추출 (예외 버전 형태 없음)
     *
     * <h3>허용되는 버전 예시</h3>
     * <ul>
     * <li>{@code 1.0} → 기본 릴리스 버전</li>
     * <li>{@code v1.2.3}, {@code V2.0.1} → Git 태그 스타일 (v 접두사 허용)</li>
     * <li>{@code 1.0.0}, {@code 2.3.4.5} → SemVer 표준 숫자 버전</li>
     * <li>{@code 1.0.0-RC1}, {@code 2.0.0-rc2}, {@code 3.1.0-BETA5} → Release Candidate, Beta</li>
     * <li>{@code 1.0.0-ALPHA}, {@code 1.0.0-alpha12} → Alpha 버전</li>
     * <li>{@code 1.0.0-M1}, {@code 1.0.0-m3} → Milestone</li>
     * <li>{@code 1.0.0-SNAPSHOT}, {@code 2.1.0-final} → 개발/최종 릴리스 태그</li>
     * <li>{@code 1.0.0+20251203}, {@code 17.0.12+7}, {@code 1.8.0_422+8} → 빌드 메타데이터 (JDK, CI 필수!)</li>
     * <li>{@code v1.0.0-RC1+build.123} → v 접두사 + 프리릴리스 + 메타데이터 조합</li>
     * </ul>
     *
     * <h3>차단되는 잘못된 예시 (의도된 대로 차단됨)</h3>
     * <ul>
     * <li>{@code 1} → 점(.)과 Minor 버전 없음</li>
     * <li>{@code 1.0-jdk17}, {@code 1.0-openjdk21} → 비표준 qualifier</li>
     * <li>{@code 1.0-hello}, {@code 1.0-test} → 의미 없는 태그</li>
     * <li>{@code 1.0-RC.1}, {@code 1.0-rc.2} → npm 스타일 점 구분자 (자바에선 사용 안 됨)</li>
     * <li>{@code 1.0-SNAPSHOT1} → SNAPSHOT 뒤에 숫자 붙음 금지</li>
     * <li>{@code 1.0-alpha-abc} → 키워드 뒤 추가 문자열 금지</li>
     * <li>{@code 2025}, {@code latest}, {@code stable} → 숫자.숫자 형태 아님</li>
     * </ul>
     *
     *
     * @param fileName The filename without extension | 확장자를 제외한 파일명 (.jar 확장자 제외)
     * @return {@link VersionInfo} object | 버전 정보 객체 (baseName, version, hasVersion 포함)
     */
    public static VersionInfo extractVersion(String fileName) {
        return extractVersion(fileName, null);
    }

    /**
     * Extracts version information from a filename with optional exceptional versions.
     *
     * 파일명에서 버전 정보를 추출
     *
     * <ul>
     * <li>{@code 1.0} → 기본 릴리스 버전</li>
     * <li>{@code v1.2.3}, {@code V2.0.1} → Git 태그 스타일 (v 접두사 허용)</li>
     * <li>{@code 1.0.0}, {@code 2.3.4.5} → SemVer 표준 숫자 버전</li>
     * <li>{@code 1.0.0-RC1}, {@code 2.0.0-rc2}, {@code 3.1.0-BETA5} → Release Candidate, Beta</li>
     * <li>{@code 1.0.0-ALPHA}, {@code 1.0.0-alpha12} → Alpha 버전</li>
     * <li>{@code 1.0.0-M1}, {@code 1.0.0-m3} → Milestone</li>
     * <li>{@code 1.0.0-SNAPSHOT}, {@code 2.1.0-final} → 개발/최종 릴리스 태그</li>
     * <li>{@code 1.0.0+20251203}, {@code 17.0.12+7}, {@code 1.8.0_422+8} → 빌드 메타데이터 (JDK, CI 필수!)</li>
     * <li>{@code v1.0.0-RC1+build.123} → v 접두사 + 프리릴리스 + 메타데이터 조합</li>
     * </ul>
     *
     * <h3>차단되는 잘못된 예시 (의도된 대로 차단됨)</h3>
     * <ul>
     * <li>{@code 1} → 점(.)과 Minor 버전 없음</li>
     * <li>{@code 1.0-jdk17}, {@code 1.0-openjdk21} → 비표준 qualifier</li>
     * <li>{@code 1.0-hello}, {@code 1.0-test} → 의미 없는 태그</li>
     * <li>{@code 1.0-RC.1}, {@code 1.0-rc.2} → npm 스타일 점 구분자 (자바에선 사용 안 됨)</li>
     * <li>{@code 1.0-SNAPSHOT1} → SNAPSHOT 뒤에 숫자 붙음 금지</li>
     * <li>{@code 1.0-alpha-abc} → 키워드 뒤 추가 문자열 금지</li>
     * <li>{@code 2025}, {@code latest}, {@code stable} → 숫자.숫자 형태 아님</li>
     * </ul>
     *
     * <p>
     * 예외적인 버전: exceptionalVersions 배열에 정의된 문자열
     * </p>
     * <p>
     * 우선순위: 1) 뒤에서부터 예외 버전 찾기, 2) 예외 버전 없으면 뒤에서부터 일반 패턴 찾기
     * </p>
     * <p>
     * 예:
     * </p>
     * <ul>
     * <li>bcprov-1.78 -&gt; baseName: bcprov, version: 1.78 (일반 패턴 매칭)</li>
     * <li>bcprov-jdk18on-1.78 -&gt; baseName: bcprov-jdk18on, version: 1.78 (예외 버전이 없으면 일반 패턴 뒤에서부터 매칭)</li>
     * <li>bcprov-jdk18on -&gt; baseName: bcprov, version: jdk18on (jdk18on이 예외 버전에 포함된 경우)</li>
     * </ul>
     *
     * @param fileName            The filename without extension | 확장자를 제외한 파일명 (.jar 확장자 제외)
     * @param exceptionalVersions Non-standard version strings | 예외적인 버전 형태 배열
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     * @return {@link VersionInfo} object | 버전 정보 객체 (baseName, version, hasVersion 포함)
     */
    public static VersionInfo extractVersion(String fileName, String[] exceptionalVersions) {
        if (fileName == null || fileName.isBlank()) {
            return new VersionInfo("", null, false);
        }

        String trimmedFileName = fileName.trim();

        // 파일명을 토큰화 ([-_]로 분리)
        String[] parts = trimmedFileName.split("[-_]");

        // 뒤에서부터 버전 찾기 (baseName 최소 1개 요소 필요)
        for (int i = parts.length - 1; i > 0; i--) {
            // parts 배열의 i번째 요소를 기준으로 원본 파일명에서 버전 후보 문자열을 자름
            // 이렇게 하면 '1.8.0_422'와 같이 '_'가 포함된 버전도 원본 그대로 유지됨
            String candidateVersion = trimmedFileName.substring(
                    trimmedFileName.lastIndexOf(parts[i - 1]) + parts[i - 1].length() + 1
            );

            // 1단계: 예외적인 버전 패턴 확인
            if (exceptionalVersions != null && exceptionalVersions.length > 0) {
                for (String exceptionalVersion : exceptionalVersions) {
                    if (candidateVersion.equals(exceptionalVersion) || candidateVersion.startsWith(exceptionalVersion + "-")) {
                        String baseName = String.join("-", java.util.Arrays.copyOfRange(parts, 0, i));
                        return new VersionInfo(baseName, candidateVersion, true);
                    }
                }
            }

            // 2단계: 엄격한 버전 패턴 확인
            if (STRICT_VERSION_PATTERN.matcher(candidateVersion).matches()) {
                String baseName = String.join("-", java.util.Arrays.copyOfRange(parts, 0, i));
                return new VersionInfo(baseName, candidateVersion, true);
            }
        }

        return new VersionInfo(trimmedFileName, null, false);
    }

    /**
     * Data class for version information.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 버전 정보를 담는 데이터 클래스입니다.
     */
    public static class VersionInfo {
        /** Base name excluding version | 버전을 제외한 기본 이름 */
        public final String baseName;
        /** Extracted version string | 추출된 버전 문자열 (없으면 null) */
        public final String version;
        /** Whether version extraction was successful | 버전 추출 성공 여부 */
        public final boolean hasVersion;

        public VersionInfo(String baseName, String version, boolean hasVersion) {
            this.baseName = baseName;
            this.version = version;
            this.hasVersion = hasVersion;
        }
    }

    /**
     * Data class for individual artifact items.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 개별 아티팩트 항목을 담는 데이터 클래스입니다.
     */
    public static class ArtifactItem {
        /** JAR file object | JAR 파일 객체 */
        public final File file;
        /** Classifier (sources, javadoc, etc.) | Classifier (sources, javadoc 등, 없으면 null) */
        public final String classifier;
        /** Artifact ID | 아티팩트 ID */
        public final String artifactId;
        /** Version string | 버전 문자열 */
        public final String version;

        public ArtifactItem(File file, String classifier, String artifactId, String version) {
            this.file = file;
            this.classifier = classifier;
            this.artifactId = artifactId;
            this.version = version;
        }
    }
}
