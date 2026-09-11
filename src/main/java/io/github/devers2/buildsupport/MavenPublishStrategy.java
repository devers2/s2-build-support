/**
 * S2BuildSupport Plugin
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

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;

/**
 * Utility class for determining Maven publication strategy.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * Maven 배포 전략을 결정하는 유틸리티 클래스입니다.
 * <p>
 * 이 클래스는 GitHub Packages에 아티팩트를 배포하기 전에 중복 배포를 방지하고 효율적인 배포 전략을 수립하는 기능을 제공합니다.
 * 배포 결정 로직:
 * 1. POM 파일이 없으면 전체 배포 (POM + 모든 JAR)
 * 2. POM 파일이 있으면 누락된 JAR만 배포
 * 3. 모든 아티팩트가 있으면 배포 스킵
 * </p>
 *
 * @see GitHubPackagesClient
 */
public class MavenPublishStrategy {

    /**
     * Data class containing the result of a publication decision.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 결정 결과를 담는 데이터 클래스입니다.
     * <p>
     * 배포 여부, 이유, 누락된 아티팩트 목록을 포함합니다.
     * </p>
     */
    public static class PublishDecision {
        /** 배포를 진행해야 하는지 여부 */
        public final boolean shouldPublish;
        /** 배포 결정 이유 (로깅용) */
        public final String reason;
        /** 누락된 아티팩트 목록 */
        public final List<String> missingArtifacts;

        /**
         * Constructs a new PublishDecision.
         *
         * @param shouldPublish Whether to publish
         * @param reason        Decision reason
         */
        public PublishDecision(boolean shouldPublish, String reason) {
            this.shouldPublish = shouldPublish;
            this.reason = reason;
            this.missingArtifacts = new ArrayList<>();
        }

        /**
         * Constructs a new PublishDecision with missing artifacts.
         *
         * @param shouldPublish    Whether to publish
         * @param reason           Decision reason
         * @param missingArtifacts List of missing artifacts
         */
        public PublishDecision(boolean shouldPublish, String reason, List<String> missingArtifacts) {
            this.shouldPublish = shouldPublish;
            this.reason = reason;
            this.missingArtifacts = missingArtifacts;
        }
    }

    /**
     * Strategic logic to determine whether to publish.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 여부를 결정하는 전략 로직입니다.
     * <p>
     * 처리 단계:
     * 1. POM 파일 존재 여부 확인
     * 2. POM이 없으면 즉시 전체 배포 결정
     * 3. POM이 있으면 각 아티팩트(JAR) 존재 여부 확인
     * 4. 누락된 아티팩트가 있으면 배포, 모두 있으면 스킵
     * </p>
     *
     * @param baseUrl    Artifact base URL | 아티팩트 베이스 URL (예: https://maven.pkg.github.com/owner/repo/group/id/artifact/version)
     * @param artifactId Artifact ID | 아티팩트 ID
     * @param version    Version | 버전
     * @param artifacts  List of artifacts to publish | 배포할 아티팩트 목록 (classifier 포함)
     * @param user       GitHub username | GitHub 사용자명 (인증용)
     * @param token      GitHub token | GitHub 토큰 (인증용)
     * @return {@link PublishDecision} object | 배포 결정 결과 객체 (배포 여부, 이유, 누락 아티팩트 포함)
     */
    public static PublishDecision shouldPublish(
            String baseUrl,
            String artifactId,
            String version,
            List<ArtifactInfo> artifacts,
            String user,
            String token) {

        // 1. POM 존재 여부 확인
        String pomUrl = baseUrl + "/" + artifactId + "-" + version + ".pom";
        boolean pomExists = GitHubPackagesClient.checkArtifactExists(pomUrl, user, token);

        if (!pomExists) {
            // POM이 없으면 전체 배포
            return new PublishDecision(true, "POM이 없으므로 전체 배포");
        }

        // 2. POM이 있으면 각 아티팩트 확인
        List<String> missingArtifacts = new ArrayList<>();

        for (ArtifactInfo artifact : artifacts) {
            // 아티팩트 파일명 생성 (classifier 고려)
            String artifactName;
            if (artifact.classifier != null && !artifact.classifier.isBlank()) {
                artifactName = artifactId + "-" + version + "-" + artifact.classifier + "." + artifact.extension;
            } else {
                artifactName = artifactId + "-" + version + "." + artifact.extension;
            }

            String artifactUrl = baseUrl + "/" + artifactName;
            boolean exists = GitHubPackagesClient.checkArtifactExists(artifactUrl, user, token);

            if (!exists) {
                missingArtifacts.add(artifactName);
            }
        }

        if (missingArtifacts.isEmpty()) {
            // 모든 아티팩트가 존재 -&gt; 배포 스킵
            return new PublishDecision(false, "모든 아티팩트가 이미 존재");
        } else {
            // 일부 아티팩트 누락 -&gt; 배포 진행
            return new PublishDecision(true, "누락된 아티팩트 배포", missingArtifacts);
        }
    }

    /**
     * Data class for artifact information.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 아티팩트 정보를 담는 데이터 클래스입니다.
     * <p>
     * 배포할 아티팩트의 classifier와 확장자 정보를 저장합니다.
     * </p>
     */
    public static class ArtifactInfo {
        /** Classifier (sources, javadoc 등, 일반 jar는 null) */
        public final String classifier;
        /** 파일 확장자 (보통 "jar") */
        public final String extension;

        /**
         * Constructs a new ArtifactInfo.
         *
         * @param classifier Artifact classifier
         * @param extension  File extension
         */
        public ArtifactInfo(String classifier, String extension) {
            this.classifier = classifier;
            this.extension = extension;
        }
    }

    /**
     * Configures smart publishing for Gradle tasks (Default repo: s2-packages).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 스마트 배포 로직을 Gradle 태스크에 적용합니다 (기본 리포지토리: s2-packages).
     *
     * <p>
     * 1. POM 없음 → 전체 배포
     * 2. POM 있음 + 모든 JAR 있음 → 배포 스킵
     * 3. POM 있음 + 일부 JAR 누락 → 누락된 JAR만 배포 시도 (POM Conflict 무시)
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     */
    public static void configureSmartPublishing(Project project) {
        configureSmartPublishing(project, "s2-packages");
    }

    /**
     * Configures smart publishing for Gradle tasks.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 스마트 배포 로직을 Gradle 태스크에 적용합니다.
     *
     * @param project        The Gradle project instance | Gradle 프로젝트 객체
     * @param repositoryName Target repository name | 적용할 리포지토리 이름
     */
    public static void configureSmartPublishing(Project project, String repositoryName) {
        project.getTasks().withType(PublishToMavenRepository.class).configureEach(task -> {
            // 특정 리포지토리에만 적용
            if (task.getRepository() != null && repositoryName.equals(task.getRepository().getName())) {
                task.onlyIf(t -> evaluatePublishDecision(project, (PublishToMavenRepository) t));
            }
        });
    }

    // ========================================================================
    // Private 헬퍼 메서드 (Helper Methods)
    // ========================================================================

    /**
     * Evaluates the publishing decision and logs results.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 결정을 평가하고 결과를 로깅합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @param pubTask The publication task | 배포 태스크
     * @return {@code true} to proceed with publication | 배포 진행 여부
     */
    private static boolean evaluatePublishDecision(Project project, PublishToMavenRepository pubTask) {
        MavenPublication pub = pubTask.getPublication();
        String groupPath = project.getGroup().toString().replace('.', '/');
        String artifactId = pub.getArtifactId();
        String version = pub.getVersion();

        // GitHub Packages 정보 추출
        String repoBaseUrl = getPropertyOrEmpty(project, "REPO_BASE_URL");
        String githubUser = getPropertyOrEmpty(project, "GITHUB_USER");
        String githubToken = getPropertyOrEmpty(project, "GITHUB_TOKEN");

        String baseUrl = String.format("%s/%s/%s/%s", repoBaseUrl, groupPath, artifactId, version);

        // 아티팩트 정보 수집
        List<ArtifactInfo> artifacts = collectArtifactInfo(project, pub);

        // 배포 결정
        PublishDecision decision = shouldPublish(baseUrl, artifactId, version, artifacts, githubUser, githubToken);

        // 결과 로깅 및 반환
        return logPublishDecision(project, artifactId, version, decision);
    }

    /**
     * Returns property value from project or empty string.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 프로젝트 속성 값을 반환하거나 값이 없으면 빈 문자열을 반환합니다.
     *
     * @param project      The Gradle project instance | Gradle 프로젝트 객체
     * @param propertyName Property name | 속성명
     * @return Property value | 속성 값
     */
    private static String getPropertyOrEmpty(Project project, String propertyName) {
        // getExtraProperties().get()은 ext에 없으면 MissingPropertyException을 던지므로 사용하지 않는다.
        // gradle.properties / -P 옵션으로 설정된 값도 안전하게 조회하기 위해 findProperty를 사용한다.
        // determineSourceJarStatus와 동일하게 rootProject 기준으로 조회하여 서브프로젝트에도 일관되게 적용한다.
        Object value = project.getRootProject().findProperty(propertyName);
        return value != null ? value.toString() : "";
    }

    /**
     * Collects and logs artifact information from publication.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 출판물(Publication)에서 아티팩트 정보를 수집하고 로깅합니다.
     *
     * @param project     The Gradle project instance | Gradle 프로젝트 객체
     * @param publication Maven publication object | Maven 출판물 객체
     * @return List of artifact information | 아티팩트 정보 목록
     */
    private static List<ArtifactInfo> collectArtifactInfo(Project project, MavenPublication publication) {
        project.getLogger().lifecycle("");
        if (S2BuildUtils.isKorean()) {
            project.getLogger().lifecycle(
                    "🔍 [CHECK] 아티팩트 상태 확인 중: {}:{}...",
                    publication.getArtifactId(), publication.getVersion());
        } else {
            project.getLogger().lifecycle(
                    "🔍 [CHECK] Verifying artifact status: {}:{}...",
                    publication.getArtifactId(), publication.getVersion());
        }

        List<ArtifactInfo> artifacts = new ArrayList<>();
        publication.getArtifacts().forEach(a -> {
            project.getLogger().lifecycle(
                    "  - Artifact: {} (Classifier: {}, Ext: {})",
                    a.getFile().getName(), a.getClassifier(), a.getExtension());
            artifacts.add(new ArtifactInfo(a.getClassifier(), a.getExtension()));
        });
        return artifacts;
    }

    /**
     * Logs the publishing decision result and returns boolean status.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 결정 결과를 로깅하고 불리언 상태를 반환합니다.
     *
     * @param project    The Gradle project instance | Gradle 프로젝트 객체
     * @param artifactId Artifact ID | 아티팩트 ID
     * @param version    Version | 버전
     * @param decision   Publication decision | 배포 결정 결과
     * @return {@code true} to proceed with publication | 배포 진행 여부
     */
    private static boolean logPublishDecision(Project project, String artifactId, String version,
            PublishDecision decision) {
        boolean isKo = S2BuildUtils.isKorean();

        if (!decision.shouldPublish) {
            if (isKo) {
                project.getLogger().lifecycle("⏭️  [SKIP] {}:{} 버전은 이미 완전히 배포되어 있습니다.", artifactId, version);
            } else {
                project.getLogger().lifecycle("⏭️  [SKIP] Version {}:{} is already fully deployed.", artifactId,
                        version);
            }
            return false;
        }

        if (decision.reason.contains("POM")) {
            if (isKo) {
                project.getLogger().lifecycle("🆕 [REGISTER] {}:{} - 신규 버전 배포 (기존 POM 없음)", artifactId, version);
            } else {
                project.getLogger().lifecycle("🆕 [REGISTER] {}:{} - New version deployment (POM missing)", artifactId,
                        version);
            }
            return true;
        } else {
            // 일부 아티팩트 누락 시 경고 메시지 출력
            if (isKo) {
                project.getLogger().lifecycle("⚠️  [REGISTER] {}:{} - 일부 아티팩트 누락", artifactId, version);
                project.getLogger().lifecycle("    누락된 파일 목록:");
            } else {
                project.getLogger().lifecycle("⚠️  [REGISTER] {}:{} - Some artifacts missing", artifactId, version);
                project.getLogger().lifecycle("    Missing files:");
            }

            decision.missingArtifacts.forEach(missing -> project.getLogger().lifecycle("      - {}", missing));
            project.getLogger().lifecycle("");

            project.getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            if (isKo) {
                project.getLogger().error("⚠️  [주의] 이미 배포된 버전 (기존 POM 존재)");
                project.getLogger().error("");
                project.getLogger().error("  GitHub Packages에 이미 동일한 버전의 POM 파일이 존재합니다.");
                project.getLogger().error("  이로 인해 빌드 완료 시 '409 Conflict' 오류가 발생할 수 있습니다.");
                project.getLogger().error("  하지만 누락되었던 JAR 파일들은 정상적으로 업로드됩니다.");
            } else {
                project.getLogger().error("⚠️  [CAUTION] Version already published (POM exists)");
                project.getLogger().error("");
                project.getLogger().error("  A POM file for the same version already exists in GitHub Packages.");
                project.getLogger().error("  This may result in '409 Conflict' errors at the end of the build.");
                project.getLogger().error("  However, the missing JAR files WILL be uploaded successfully.");
            }
            project.getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }
    }
}
