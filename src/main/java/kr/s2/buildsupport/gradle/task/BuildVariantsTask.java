package kr.s2.buildsupport.gradle.task;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import kr.s2.buildsupport.GitHubPackagesClient;
import kr.s2.buildsupport.S2BuildUtils;

/**
 * 정의된 모든 빌드 변형(Variants)을 순차적으로 빌드하는 태스크
 */
public abstract class BuildVariantsTask extends DefaultTask {

    // 태스크 이름 상수
    private static final String TASK_JAR = "jar";
    private static final String TASK_SOURCES_JAR = "sourcesJar";
    private static final String TASK_JAVADOC_JAR = "javadocJar";
    private static final String TASK_CLEAN = "clean";
    private static final String TASK_HELP = "help";
    private static final String TASK_BUILD_ALL_VARIANTS = "buildAllVariants";
    private static final String TASK_JAVADOC = "javadoc";

    // Gradle 실행 파일 이름
    private static final String GRADLEW_WINDOWS = "gradlew.bat";
    private static final String GRADLEW_UNIX = "gradlew";

    @Input
    public abstract ListProperty<Map<String, Object>> getVariants();

    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * ExecSpec에 JAVA_HOME 환경 변수를 설정하는 헬퍼 메서드
     */
    private void configureEnvironment(org.gradle.process.ExecSpec spec, String javaHome) {
        Map<String, Object> env = new HashMap<>(System.getenv());
        env.put("JAVA_HOME", javaHome);
        spec.environment(env);
    }

    /**
     * MavenArtifact의 builtBy 의존성을 설정하는 헬퍼 메서드
     *
     * @param artifact      Maven 아티팩트
     * @param project       Gradle 프로젝트
     * @param isMainVariant 메인 variant 여부
     * @param mainTaskName  메인 variant의 빌드 태스크 이름
     */
    private static void configureArtifactBuiltBy(
            org.gradle.api.publish.maven.MavenArtifact artifact,
            Project project,
            boolean isMainVariant,
            String mainTaskName) {
        if (!isMainVariant) {
            artifact.builtBy(project.getTasks().named(TASK_BUILD_ALL_VARIANTS));
        } else {
            artifact.builtBy(project.getTasks().named(mainTaskName));
        }
    }

    @TaskAction
    public void build() {
        String rootDir = getProject().getRootDir().getAbsolutePath();
        String gradlew = System.getProperty("os.name").toLowerCase().contains("windows") ? GRADLEW_WINDOWS : GRADLEW_UNIX;
        String gradlewPath = new File(rootDir, gradlew).getAbsolutePath();

        // 현재 실행 중인 Java 홈 경로
        String javaHome = System.getProperty("java.home");

        // 모든 빌드 시작 전 clean 한 번만 실행
        getLogger().lifecycle("🧹 Cleaning build directory...");
        getExecOperations().exec(spec -> {
            configureEnvironment(spec, javaHome);
            spec.commandLine(gradlewPath, TASK_CLEAN, "-PisSubBuild=true");
        });

        // 각 변형 빌드 (clean 없이 jar만 실행)
        for (Map<String, Object> variant : getVariants().get()) {
            JavaVersion javaVersion = (JavaVersion) variant.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> additionalSource = (Set<String>) variant.get("additionalSource");

            String classifier = S2BuildUtils.generateClassifier(javaVersion, additionalSource);
            String displayClassifier = classifier.isEmpty() ? "(main)" : classifier;

            getLogger().lifecycle("🚀 Building variant: Java {}, Sources: {} (Classifier: {})", javaVersion, additionalSource, displayClassifier);

            try {
                getExecOperations().exec(spec -> {
                    configureEnvironment(spec, javaHome);

                    spec.commandLine(
                            gradlewPath,
                            TASK_JAR, TASK_SOURCES_JAR, TASK_JAVADOC_JAR, // 각 변형마다 jar, sources.jar, javadoc.jar 생성
                            "-Dorg.gradle.java.home=" + javaHome,
                            "-PtargetJavaVersion=" + javaVersion,
                            "-PtargetSources=" + String.join(",", additionalSource),
                            "-PisSubBuild=true"
                    );
                });

                getLogger().lifecycle("✅ Variant built successfully: {}", displayClassifier);

            } catch (Exception e) {
                // 빌드 실패 시 명확한 에러 메시지 출력
                getLogger().error("");
                getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                getLogger().error("❌ BUILD FAILED for variant: {}", displayClassifier);
                getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                getLogger().error("Variant Details:");
                getLogger().error("  - Java Version: {}", javaVersion);
                getLogger().error("  - Additional Sources: {}", additionalSource);
                getLogger().error("  - Classifier: {}", displayClassifier);
                getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                getLogger().error("");

                // 원본 예외를 다시 던져서 빌드 중단
                throw new org.gradle.api.GradleException(
                        String.format(
                                "Failed to build variant: %s (Java %s, Sources: %s)",
                                displayClassifier, javaVersion, additionalSource
                        ),
                        e
                );
            }
        }

        /**
         * [프로젝트 기본값 복원]
         * 메인 변형(classifier="")이 SKIP 되었거나, 마지막 변형이 기본값과 다를 수 있기 때문에 모든 변형 빌드가 끝난 후, 워크스페이스(소스 코드 상태)를 프로젝트 기본값으로 복원한다.
         */
        try {
            Object defaultJavaVersion = getProject().getExtensions().getExtraProperties().get("javaVersion");
            Object defaultSourcesObj = getProject().getExtensions().getExtraProperties().get("additionalSource");

            String defaultSourcesStr = "";
            if (defaultSourcesObj instanceof java.util.Collection) {
                @SuppressWarnings("unchecked")
                java.util.Collection<String> srcColl = (java.util.Collection<String>) defaultSourcesObj;
                defaultSourcesStr = String.join(",", srcColl);
            }

            getLogger().lifecycle("🧹 Restoring workspace to default state (Java {}, Sources: [{}])", defaultJavaVersion, defaultSourcesStr);

            String finalDefaultSourcesStr = defaultSourcesStr;
            getExecOperations().exec(spec -> {
                configureEnvironment(spec, javaHome);

                spec.commandLine(
                        gradlewPath,
                        TASK_HELP, // 가벼운 태스크 실행으로 설정 단계(Configuration Phase) 트리거
                        "-Dorg.gradle.java.home=" + javaHome,
                        "-PtargetJavaVersion=" + defaultJavaVersion,
                        "-PtargetSources=" + finalDefaultSourcesStr,
                        "-PisSubBuild=true",
                        "-q" // Quiet 모드
                );
            });
            getLogger().lifecycle("✨ Workspace restored.");

        } catch (Exception e) {
            getLogger().warn("⚠️  Failed to restore workspace to default state: {}", e.getMessage());
        }
    }

    /**
     * Variants 목록을 기반으로 MavenPublication에 아티팩트 등록
     * s2-util/build.gradle의 publishing 로직을 이곳으로 캡슐화
     */
    public static void configureVariantArtifacts(Project project, MavenPublication publication, List<Map<String, Object>> variants) {
        Logger logger = project.getLogger();
        Object archivesNameObj = project.getProperties().get("archivesBaseName"); // or base.archivesName
        String archivesName = archivesNameObj != null ? archivesNameObj.toString() : project.getName();
        Object version = project.getVersion();

        // 중복 방지를 위한 classifier 추적
        Set<String> addedClassifiers = new HashSet<>();

        // 1. Classifier 기준 정렬: 메인(빈값) 우선
        List<Map<String, Object>> sortedVariants = new ArrayList<>(variants);
        sortedVariants.sort((a, b) -> {
            JavaVersion jvA = (JavaVersion) a.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> asA = (Set<String>) a.get("additionalSource");
            String cA = S2BuildUtils.generateClassifier(jvA, asA);

            JavaVersion jvB = (JavaVersion) b.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> asB = (Set<String>) b.get("additionalSource");
            String cB = S2BuildUtils.generateClassifier(jvB, asB);

            if (cA.isEmpty())
                return -1;
            if (cB.isEmpty())
                return 1;
            return cA.compareTo(cB);
        });

        // 2. 환경 정보 확인 (Repo Private 여부 등)
        Object repoBaseUrlObj = project.getExtensions().getExtraProperties().get("REPO_BASE_URL");
        String repoBaseUrl = repoBaseUrlObj != null ? repoBaseUrlObj.toString() : "";
        Object githubTokenObj = project.getExtensions().getExtraProperties().get("GITHUB_TOKEN");
        String githubToken = githubTokenObj != null ? githubTokenObj.toString() : "";
        boolean isRepoPrivate = GitHubPackagesClient.isRepoPrivate(repoBaseUrl, githubToken);

        // Safe Task 확인
        Object safeTasksObj = project.getExtensions().getExtraProperties().has("safeTasks")
                ? project.getExtensions().getExtraProperties().get("safeTasks")
                : null;

        @SuppressWarnings("unchecked")
        Set<String> safeTasks = safeTasksObj != null ? (Set<String>) safeTasksObj : new HashSet<>();
        List<String> currentTasks = project.getGradle().getStartParameter().getTaskNames();

        // 원격 publish 태스크 확인 (로컬 publish 제외)
        boolean isRemotePublish = currentTasks.stream().anyMatch(t -> {
            String lower = t.toLowerCase();
            return lower.contains("publish") && !lower.contains("publishtomavenlocal");
        });

        boolean isSafeTask = currentTasks.stream().anyMatch(safeTasks::contains);

        // 소스 JAR 포함 여부 결정
        boolean shouldIncludeSourcesJar = false;
        if (isRemotePublish) {
            shouldIncludeSourcesJar = isRepoPrivate || isSafeTask;
            if (shouldIncludeSourcesJar) {
                if (isRepoPrivate)
                    logger.lifecycle("✅ 비공개 리포지토리이므로 소스 JAR를 배포합니다.");
                else
                    logger.lifecycle("✅ 안전한 태스크이므로 소스 JAR를 배포합니다.");
            } else {
                logger.warn("⚠️  공개 리포지토리에 publish 실행 시 소스 JAR를 배포하지 않습니다. (소스 코드 노출 방지)");
            }
        } else if (isSafeTask) {
            shouldIncludeSourcesJar = true;
        }

        // 3. 변형 순회 및 아티팩트 등록
        for (Map<String, Object> variant : sortedVariants) {
            JavaVersion javaVersion = (JavaVersion) variant.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> additionalSource = (Set<String>) variant.get("additionalSource");

            String variantClassifier = S2BuildUtils.generateClassifier(javaVersion, additionalSource);

            boolean isMainVariant = variantClassifier == null || variantClassifier.isEmpty();

            // --- Main JAR ---
            // 모든 variant의 main JAR 등록 (메인 포함)
            String mainClassifier = isMainVariant ? "" : variantClassifier;
            if (!addedClassifiers.contains(mainClassifier)) {
                String jarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), variantClassifier);
                File jarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + jarName);

                logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", jarFile.getName(), isMainVariant ? "(main)" : variantClassifier);

                publication.artifact(jarFile, artifact -> {
                    artifact.setExtension("jar");
                    if (!isMainVariant) {
                        artifact.setClassifier(variantClassifier);
                    }
                    configureArtifactBuiltBy(artifact, project, isMainVariant, TASK_JAR);
                });
                addedClassifiers.add(mainClassifier);
            }

            // --- Sources JAR ---
            // 메인 variant 포함 모든 variant의 sources JAR 등록
            if (shouldIncludeSourcesJar) {
                String sourcesClassifier = isMainVariant ? "sources" : variantClassifier + "-sources";
                if (!addedClassifiers.contains(sourcesClassifier)) {
                    String sourcesJarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), sourcesClassifier);
                    File sourcesJarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + sourcesJarName);

                    logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", sourcesJarFile.getName(), sourcesClassifier);

                    publication.artifact(sourcesJarFile, artifact -> {
                        artifact.setExtension("jar");
                        artifact.setClassifier(sourcesClassifier);
                        configureArtifactBuiltBy(artifact, project, isMainVariant, TASK_JAR);
                    });
                    addedClassifiers.add(sourcesClassifier);
                }
            }

            // --- Javadoc JAR ---
            // 메인 variant 포함 모든 variant의 javadoc JAR 등록
            String javadocClassifier = isMainVariant ? "javadoc" : variantClassifier + "-javadoc";
            if (!addedClassifiers.contains(javadocClassifier)) {
                String javadocJarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), javadocClassifier);
                File javadocJarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + javadocJarName);

                logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", javadocJarFile.getName(), javadocClassifier);

                publication.artifact(javadocJarFile, artifact -> {
                    artifact.setExtension("jar");
                    artifact.setClassifier(javadocClassifier);
                    configureArtifactBuiltBy(artifact, project, isMainVariant, TASK_JAVADOC);
                });
                addedClassifiers.add(javadocClassifier);
            }
        }
    }
}
