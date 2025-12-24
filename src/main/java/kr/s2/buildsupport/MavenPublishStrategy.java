package kr.s2.buildsupport;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;

/**
 * Maven 배포 전략을 결정하는 유틸리티 클래스
 *
 * <p>
 * 이 클래스는 GitHub Packages에 아티팩트를 배포하기 전에
 * 중복 배포를 방지하고 효율적인 배포 전략을 수립하는 기능 제공
 * </p>
 *
 * <p>
 * 배포 결정 로직:
 * </p>
 * <ol>
 * <li>POM 파일이 없으면 전체 배포 (POM + 모든 JAR)</li>
 * <li>POM 파일이 있으면 누락된 JAR만 배포</li>
 * <li>모든 아티팩트가 있으면 배포 스킵</li>
 * </ol>
 *
 * @see GitHubPackagesClient
 */
public class MavenPublishStrategy {

    /**
     * 배포 결정 결과를 담는 데이터 클래스
     *
     * <p>
     * 배포 여부, 이유, 누락된 아티팩트 목록 포함
     * </p>
     */
    public static class PublishDecision {
        /** 배포를 진행해야 하는지 여부 */
        public final boolean shouldPublish;
        /** 배포 결정 이유 (로깅용) */
        public final String reason;
        /** 누락된 아티팩트 목록 */
        public final List<String> missingArtifacts;

        public PublishDecision(boolean shouldPublish, String reason) {
            this.shouldPublish = shouldPublish;
            this.reason = reason;
            this.missingArtifacts = new ArrayList<>();
        }

        public PublishDecision(boolean shouldPublish, String reason, List<String> missingArtifacts) {
            this.shouldPublish = shouldPublish;
            this.reason = reason;
            this.missingArtifacts = missingArtifacts;
        }
    }

    /**
     * 배포 여부를 결정하는 전략 로직
     *
     * <p>
     * 처리 단계:
     * </p>
     * <ol>
     * <li>POM 파일 존재 여부 확인</li>
     * <li>POM이 없으면 즉시 전체 배포 결정</li>
     * <li>POM이 있으면 각 아티팩트(JAR) 존재 여부 확인</li>
     * <li>누락된 아티팩트가 있으면 배포, 모두 있으면 스킵</li>
     * </ol>
     *
     * @param baseUrl    아티팩트 베이스 URL (예: https://maven.pkg.github.com/owner/repo/group/id/artifact/version)
     * @param artifactId 아티팩트 ID
     * @param version    버전
     * @param artifacts  배포할 아티팩트 목록 (classifier 포함)
     * @param user       GitHub 사용자명 (인증용)
     * @param token      GitHub 토큰 (인증용)
     * @return PublishDecision 객체 (배포 여부, 이유, 누락 아티팩트 포함)
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
            if (artifact.classifier != null && !artifact.classifier.isEmpty()) {
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
     * 아티팩트 정보를 담는 데이터 클래스
     *
     * <p>
     * 배포할 아티팩트의 classifier와 확장자 정보 저장
     * </p>
     */
    public static class ArtifactInfo {
        /** Classifier (sources, javadoc 등, 일반 jar는 null) */
        public final String classifier;
        /** 파일 확장자 (보통 "jar") */
        public final String extension;

        public ArtifactInfo(String classifier, String extension) {
            this.classifier = classifier;
            this.extension = extension;
        }
    }

    /**
     * 스마트 배포 로직을 Gradle 태스크에 적용 (기본 리포지토리: s2-packages)
     *
     * <p>
     * 1. POM 없음 → 전체 배포
     * 2. POM 있음 + 모든 JAR 있음 → 배포 스킵
     * 3. POM 있음 + 일부 JAR 누락 → 누락된 JAR만 배포 시도 (POM Conflict 무시)
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     */
    public static void configureSmartPublishing(Project project) {
        configureSmartPublishing(project, "s2-packages");
    }

    /**
     * 스마트 배포 로직을 Gradle 태스크에 적용
     *
     * @param project        Gradle 프로젝트 객체
     * @param repositoryName 적용할 리포지토리 이름
     */
    public static void configureSmartPublishing(Project project, String repositoryName) {
        project.getTasks().withType(PublishToMavenRepository.class).configureEach(task -> {
            // 특정 리포지토리에만 적용
            if (repositoryName.equals(task.getRepository().getName())) {
                task.onlyIf(t -> {
                    PublishToMavenRepository pubTask = (PublishToMavenRepository) t;
                    MavenPublication pub = pubTask.getPublication();
                    String groupPath = project.getGroup().toString().replace('.', '/');
                    String artifactId = pub.getArtifactId();
                    String version = pub.getVersion();

                    // REPO_BASE_URL 가져오기
                    Object repoBaseUrlObj = project.getExtensions().getExtraProperties().get("REPO_BASE_URL");
                    String repoBaseUrl = repoBaseUrlObj != null ? repoBaseUrlObj.toString() : "";

                    // GITHUB_TOKEN 가져오기
                    Object githubUserObj = project.getExtensions().getExtraProperties().get("GITHUB_USER");
                    String githubUser = githubUserObj != null ? githubUserObj.toString() : "";
                    Object githubTokenObj = project.getExtensions().getExtraProperties().get("GITHUB_TOKEN");
                    String githubToken = githubTokenObj != null ? githubTokenObj.toString() : "";

                    String baseUrl = String.format("%s/%s/%s/%s", repoBaseUrl, groupPath, artifactId, version);

                    project.getLogger().lifecycle("");
                    project.getLogger().lifecycle("🔍 [CHECK] 아티팩트 상태 확인 중: {}:{}...", artifactId, version);

                    List<ArtifactInfo> artifacts = new ArrayList<>();
                    pub.getArtifacts().forEach(a -> {
                        project.getLogger().lifecycle(
                                "  - Artifact: {} (Classifier: {}, Ext: {})",
                                a.getFile().getName(), a.getClassifier(), a.getExtension()
                        );
                        artifacts.add(new ArtifactInfo(a.getClassifier(), a.getExtension()));
                    });

                    PublishDecision decision = shouldPublish(baseUrl, artifactId, version, artifacts, githubUser, githubToken);

                    if (!decision.shouldPublish) {
                        project.getLogger().lifecycle("⏭️  [SKIP] {}:{} 버전은 이미 완전히 배포되어 있습니다.", artifactId, version);
                        return false;
                    }

                    if (decision.reason.contains("POM이 없으므로")) {
                        project.getLogger().lifecycle("🆕 [REGISTER] {}:{} - 신규 버전 배포 (기존 POM 없음)", artifactId, version);
                        return true;
                    } else {
                        project.getLogger().lifecycle("⚠️  [REGISTER] {}:{} - 일부 아티팩트 누락", artifactId, version);
                        project.getLogger().lifecycle("    누락된 파일 목록:");
                        decision.missingArtifacts.forEach(missing -> project.getLogger().lifecycle("      - {}", missing));
                        project.getLogger().lifecycle("");
                        project.getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        project.getLogger().error("⚠️  [주의] 이미 배포된 버전 (기존 POM 존재)");
                        project.getLogger().error("");
                        project.getLogger().error("  GitHub Packages에 이미 동일한 버전의 POM 파일이 존재합니다.");
                        project.getLogger().error("  이로 인해 빌드 완료 시 '409 Conflict' 오류가 발생할 수 있습니다.");
                        project.getLogger().error("  하지만 누락되었던 JAR 파일들은 정상적으로 업로드됩니다.");
                        project.getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        return true;
                    }
                });
            }
        });
    }
}
