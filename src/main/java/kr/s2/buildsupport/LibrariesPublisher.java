package kr.s2.buildsupport;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 허용된 Classifier 목록과 구분자 규칙
     * <p>
     * 각 규칙은 [classifier명, 구분자] 형식으로 정의됩니다.
     * </p>
     * <p>
     * 예: for_bcprov classifier는 밑줄(_)로 구분
     * </p>
     */
    private static final String[][] ALLOWED_CLASSIFIERS = {
            {"for_bcprov", "_"}
    };

    /**
     * libs 디렉토리의 JAR 파일들을 스캔하여 Maven Publication으로 등록
     *
     * <p>
     * 처리 과정:
     * </p>
     * <ol>
     * <li>libs 디렉토리의 모든 .jar 파일 스캔</li>
     * <li>파일명에서 classifier, 아티팩트명, 버전 추출</li>
     * <li>동일한 artifactId:version을 가진 파일들을 그룹화</li>
     * <li>각 그룹을 Maven Publication으로 등록</li>
     * </ol>
     *
     * @param project Gradle 프로젝트 객체
     * @param libsDir libs 디렉토리 (서드파티 JAR 파일들이 위치한 디렉토리)
     */
    public static void registerPublications(Project project, File libsDir) {
        if (!libsDir.exists() || !libsDir.isDirectory()) {
            System.out.println("⚠️  [LibsPublishHelper] libs directory not found: " + libsDir.getAbsolutePath());
            return;
        }

        // artifactId:version을 키로 하는 아티팩트 맵
        Map<String, List<ArtifactItem>> artifactsMap = new HashMap<>();
        File[] files = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (files == null)
            return;

        // 각 JAR 파일 처리
        for (File jarFile : files) {
            String fileName = jarFile.getName();
            String baseName = fileName.substring(0, fileName.length() - 4); // remove .jar
            String distClassifier = null;

            // Classifier 규칙 적용
            for (String[] rule : ALLOWED_CLASSIFIERS) {
                String clf = rule[0];
                String sep = rule[1];
                String pattern = sep + clf;

                if (baseName.endsWith(pattern)) {
                    String before = baseName.substring(0, baseName.length() - pattern.length());
                    VersionInfo verCheck = extractVersion(before);
                    if (verCheck.hasVersion) {
                        baseName = before;
                        distClassifier = clf;
                        break;
                    }
                }
            }

            // 버전 정보 추출 및 아티팩트 ID 생성
            VersionInfo verInfo = extractVersion(baseName);
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
     * 파일명에서 버전 정보를 추출
     *
     * <p>
     * 파일명 패턴: {baseName}[-_]{version}
     * </p>
     * <p>
     * 예: bcprov-jdk18on-1.78 -&gt; baseName: bcprov-jdk18on, version: 1.78
     * </p>
     *
     * @param fileName 버전을 추출할 파일명 (.jar 확장자 제외)
     * @return VersionInfo 객체 (baseName, version, hasVersion 포함)
     */
    public static VersionInfo extractVersion(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return new VersionInfo("", null, false);
        }

        // 버전 패턴: 숫자로 시작하는 버전 문자열
        Pattern pattern = Pattern.compile("^(.+?)[-_]([0-9][A-Za-z0-9._\\+-]+)$");
        Matcher m = pattern.matcher(fileName);

        if (m.matches()) {
            return new VersionInfo(m.group(1).trim(), m.group(2), true);
        }
        return new VersionInfo(fileName.trim(), null, false);
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
