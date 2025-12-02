package kr.s2.buildsupport;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * libs 디렉토리의 JAR 파일들을 Maven Publication으로 등록하는 퍼블리셔 클래스
 *
 * <p>
 * 이 클래스는 {@code libs/} 디렉토리에 있는 서드파티 JAR 파일들을
 * 자동으로 스캔하여 Maven Publication으로 등록합니다.
 * 각 JAR 파일명에서 아티팩트명과 버전을 추출하고,
 * 허용된 classifier 규칙에 따라 적절히 그룹화하여 배포합니다.
 * </p>
 *
 * <p>
 * 주요 기능:
 * </p>
 * <ul>
 * <li>JAR 파일명 파싱 및 아티팩트 정보 추출</li>
 * <li>Classifier 기반 아티팩트 그룹화</li>
 * <li>Maven Publication 자동 등록</li>
 * </ul>
 */
public class LibrariesPublisher {

    /**
     * 지정된 디렉토리의 파일들을 스캔하여 Maven Publication으로 등록
     *
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
     * @param project             Gradle 프로젝트 객체
     * @param fileScanRules       스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers  허용된 classifier 규칙 배열 (각 항목은 [classifier명, 구분자] 형태, 예: {{"for_bcprov", "_"}} → 파일명이 _for_bcprov로 끝나는 경우 classifier로 인식)
     * @param exceptionalVersions 예외적인 버전 형태 배열
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     */
    public static void registerPublications(Project project, Object[][] fileScanRules, String[][] allowedClassifiers, String[] exceptionalVersions) {
        registerPublicationsInternal(project, fileScanRules, allowedClassifiers, exceptionalVersions);
    }

    /**
     * 지정된 디렉토리의 파일들을 스캔하여 Maven Publication으로 등록 (예외 버전 없음)
     *
     * @param project            Gradle 프로젝트 객체
     * @param fileScanRules      스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers 허용된 classifier 규칙 배열
     */
    public static void registerPublications(Project project, Object[][] fileScanRules, String[][] allowedClassifiers) {
        registerPublicationsInternal(project, fileScanRules, allowedClassifiers, null);
    }

    /**
     * 내부 구현: 지정된 디렉토리의 파일들을 스캔하여 Maven Publication으로 등록
     *
     * @param project             Gradle 프로젝트 객체
     * @param fileScanRules       스캔 규칙 배열 → 각 항목은 [디렉토리 경로(File 객체), 확장자(String)] 형태, 예: [[project.file('libs'), 'jar']]
     * @param allowedClassifiers  허용된 classifier 규칙 배열 (각 항목은 [classifier명, 구분자] 형태, 예: {{"for_bcprov", "_"}} → 파일명이 _for_bcprov로 끝나는 경우 classifier로 인식)
     * @param exceptionalVersions 예외적인 버전 형태 배열
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     *
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
                    System.out.println(
                            "⚠️  [LibsPublishHelper] Invalid rule format. Expected [File, String], but got [" +
                                    (dirObject != null ? dirObject.getClass().getSimpleName() : "null") + ", " +
                                    (extObject != null ? extObject.getClass().getSimpleName() : "null") + "]"
                    );
                    continue;
                }

                File scanDir = (File) dirObject;
                String extension = (String) extObject;

                if (!scanDir.exists() || !scanDir.isDirectory()) {
                    System.out.println("⚠️  [LibsPublishHelper] directory not found: " + scanDir.getAbsolutePath());
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
            System.out.println("⚠️  [LibsPublishHelper] No files found to publish");
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

            System.out.println("Registered Group ▶ " + distArtifactId + ":" + distVersion + " (Count: " + items.size() + ")");
            for (ArtifactItem item : items) {
                System.out.println("  - File: " + item.file.getName() + " (Classifier: " + item.classifier + ")");
            }
        });
    }

    /**
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
     * @param input 변환할 문자열 (보통 JAR 파일의 베이스 이름)
     * @return Maven 아티팩트 ID 규격에 맞는 kebab-case 문자열
     */
    public static String toMavenArtifactId(String input) {
        if (input == null || input.trim().isEmpty())
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
     * 파일명에서 버전 정보를 추출 (예외 버전 형태 없음)
     *
     * <p>
     * 파일명 패턴: {baseName}[-_]{version}
     * </p>
     * <p>
     * 예: bcprov-1.78 -&gt; baseName: bcprov, version: 1.78 (일반 패턴)
     * </p>
     *
     * @param fileName 버전을 추출할 파일명 (.jar 확장자 제외)
     * @return VersionInfo 객체 (baseName, version, hasVersion 포함)
     */
    public static VersionInfo extractVersion(String fileName) {
        return extractVersion(fileName, null);
    }

    /**
     * 파일명에서 버전 정보를 추출
     *
     * <p>
     * 파일명 패턴: {baseName}[-_]{version}
     * </p>
     * <p>
     * 일반적인 버전 패턴: 숫자로 시작하고 숫자, 점(.), 대시(-), 플러스(+)로만 구성
     * </p>
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
     * @param fileName            버전을 추출할 파일명 (.jar 확장자 제외)
     * @param exceptionalVersions 예외적인 버전 형태 배열
     *                            - 일반 패턴 (숫자, 점, 대시, 플러스만 포함, 예: "1.78", "2.0.1", "3.5-1", "1.0+20251201")
     *                            - 예외 패턴 (일반 패턴이 아닌 버전 문자열, 예: {"jdk18on", "jdk15on"})
     * @return VersionInfo 객체 (baseName, version, hasVersion 포함)
     */
    public static VersionInfo extractVersion(String fileName, String[] exceptionalVersions) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return new VersionInfo("", null, false);
        }

        String trimmedFileName = fileName.trim();

        // 파일명을 토큰화 ([-_]로 분리)
        String[] parts = trimmedFileName.split("[-_]");

        // 뒤에서부터 버전 찾기 (baseName 최소 1개 요소 필요)
        for (int i = parts.length - 1; i > 0; i--) {
            // parts 배열의 i번째 요소부터 끝까지를 dash(-)로 연결한 버전 후보 생성
            // 예: parts = ["bcprov", "jdk18on", "1.78"]일 때
            // - i=2: candidateVersion = "1.78"
            // - i=1: candidateVersion = "jdk18on-1.78"
            String candidateVersion = String.join("-", java.util.Arrays.copyOfRange(parts, i, parts.length));

            // 1단계: 예외적인 버전 패턴 확인
            if (exceptionalVersions != null && exceptionalVersions.length > 0) {
                for (String exceptionalVersion : exceptionalVersions) {
                    if (candidateVersion.equals(exceptionalVersion) || candidateVersion.startsWith(exceptionalVersion + "-")) {
                        String baseName = String.join("-", java.util.Arrays.copyOfRange(parts, 0, i));
                        return new VersionInfo(baseName, candidateVersion, true);
                    }
                }
            }

            // 2단계: 일반적인 버전 패턴 확인 (숫자로 시작, 숫자/점/대시/플러스만 포함)
            if (candidateVersion.matches("^[0-9][0-9.+\\-]*$")) {
                String baseName = String.join("-", java.util.Arrays.copyOfRange(parts, 0, i));
                return new VersionInfo(baseName, candidateVersion, true);
            }
        }

        return new VersionInfo(trimmedFileName, null, false);
    }

    /**
     * 버전 정보를 담는 데이터 클래스
     */
    public static class VersionInfo {
        /** 버전을 제외한 기본 이름 */
        public final String baseName;
        /** 추출된 버전 문자열 (없으면 null) */
        public final String version;
        /** 버전이 성공적으로 추출되었는지 여부 */
        public final boolean hasVersion;

        public VersionInfo(String baseName, String version, boolean hasVersion) {
            this.baseName = baseName;
            this.version = version;
            this.hasVersion = hasVersion;
        }
    }

    /**
     * 개별 아티팩트 항목 정보를 담는 데이터 클래스
     */
    public static class ArtifactItem {
        /** JAR 파일 객체 */
        public final File file;
        /** Classifier (sources, javadoc 등, 없으면 null) */
        public final String classifier;
        /** 아티팩트 ID */
        public final String artifactId;
        /** 버전 문자열 */
        public final String version;

        public ArtifactItem(File file, String classifier, String artifactId, String version) {
            this.file = file;
            this.classifier = classifier;
            this.artifactId = artifactId;
            this.version = version;
        }
    }
}
