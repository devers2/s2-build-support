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

    @Input
    public abstract ListProperty<Map<String, Object>> getVariants();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void build() {
        String rootDir = getProject().getRootDir().getAbsolutePath();
        String gradlew = System.getProperty("os.name").toLowerCase().contains("windows") ? "gradlew.bat" : "gradlew";
        String gradlewPath = new File(rootDir, gradlew).getAbsolutePath();

        // 현재 실행 중인 Java 홈 경로
        String javaHome = System.getProperty("java.home");

        // 메인 빌드 결과물 보존을 위해 clean 제외
        // 기존: getExecOperations().exec(spec -> { ... clean ... });

        // 각 변형 빌드 (jar, sourcesJar, javadocJar 실행)
        for (Map<String, Object> variant : getVariants().get()) {
            JavaVersion javaVersion = (JavaVersion) variant.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> additionalSource = (Set<String>) variant.get("additionalSource");

            String classifier = S2BuildUtils.generateClassifier(javaVersion, additionalSource);

            // 메인 변형(classifier 없는 경우)은 루트 프로젝트 빌드에서 처리되므로 제외
            if (classifier == null || classifier.isEmpty()) {
                getLogger().lifecycle("⏭️  Skipping main variant (handled by root build): Java {}", javaVersion);
                continue;
            }

            getLogger().lifecycle("🚀 Building variant: Java {}, Sources: {} (Classifier: {})", javaVersion, additionalSource, classifier);

            getExecOperations().exec(spec -> {
                Map<String, Object> env = new HashMap<>(System.getenv());
                env.put("JAVA_HOME", javaHome);
                spec.environment(env);

                spec.commandLine(
                        gradlewPath,
                        "jar", "sourcesJar", "javadocJar", // 각 변형마다 jar, sources.jar, javadoc.jar 생성
                        "-Dorg.gradle.java.home=" + javaHome,
                        "-PtargetJavaVersion=" + javaVersion,
                        "-PtargetSources=" + String.join(",", additionalSource),
                        "-PisSubBuild=true"
                // clean 없이 실행하여 기존 파일 유지 (동일 이름은 덮어씀)
                );
            });

            getLogger().lifecycle("✅ Variant built successfully: {}", classifier);
        }
    }

    /**
     * Variants 목록을 기반으로 MavenPublication에 아티팩트 등록
     * s2-util/build.gradle의 publishing 로직을 이곳으로 캡슐화함
     */
    public static void configureVariantArtifacts(Project project, MavenPublication publication, List<Map<String, Object>> variants) {
        Logger logger = project.getLogger();
        Object archivesNameObj = project.getProperties().get("archivesBaseName"); // or base.archivesName
        String archivesName = archivesNameObj != null ? archivesNameObj.toString() : project.getName();
        Object version = project.getVersion();

        // 중복 방지를 위한 classifier 추적
        Set<String> addedClassifiers = new HashSet<>();

        // 1. Classifier 기준 정렬: 메인(빈값) 우선 (등록은 스킵)
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
        @SuppressWarnings("unchecked")
        Set<String> safeTasks = (Set<String>) project.getExtensions().getExtraProperties().get("safeTasks");
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

            // 메인 아티팩트(빈 classifier)는 components.java에서 처리되므로 제외
            if (variantClassifier == null || variantClassifier.isEmpty()) {
                continue;
            }

            // --- Main JAR ---
            if (!addedClassifiers.contains(variantClassifier)) {
                String jarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), variantClassifier);
                File jarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + jarName);

                logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", jarFile.getName(), variantClassifier);

                publication.artifact(jarFile, artifact -> {
                    artifact.setExtension("jar");
                    artifact.setClassifier(variantClassifier);
                    artifact.builtBy(project.getTasks().named("buildAllVariants"));
                });
                addedClassifiers.add(variantClassifier);
            }

            // --- Sources JAR ---
            if (shouldIncludeSourcesJar) {
                String sourcesClassifier = variantClassifier + "-sources";
                if (!addedClassifiers.contains(sourcesClassifier)) {
                    String sourcesJarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), sourcesClassifier);
                    File sourcesJarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + sourcesJarName);

                    logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", sourcesJarFile.getName(), sourcesClassifier);

                    publication.artifact(sourcesJarFile, artifact -> {
                        artifact.setExtension("jar");
                        artifact.setClassifier(sourcesClassifier);
                        artifact.builtBy(project.getTasks().named("buildAllVariants"));
                    });
                    addedClassifiers.add(sourcesClassifier);
                }
            }

            // --- Javadoc JAR ---
            String javadocClassifier = variantClassifier + "-javadoc";
            if (!addedClassifiers.contains(javadocClassifier)) {
                String javadocJarName = S2BuildUtils.getJarFileName(archivesName, version.toString(), javadocClassifier);
                File javadocJarFile = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + javadocJarName);

                logger.lifecycle("📦 Configuring artifact: {} (Classifier: {})", javadocJarFile.getName(), javadocClassifier);

                publication.artifact(javadocJarFile, artifact -> {
                    artifact.setExtension("jar");
                    artifact.setClassifier(javadocClassifier);
                    artifact.builtBy(project.getTasks().named("buildAllVariants"));
                });
                addedClassifiers.add(javadocClassifier);
            }
        }
    }
}
