package kr.s2.buildsupport.gradle.task;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

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

        // 모든 빌드 시작 전 clean 한 번만 실행
        getLogger().lifecycle("🧹 Cleaning build directory...");
        getExecOperations().exec(spec -> {
            Map<String, Object> env = new HashMap<>(System.getenv());
            env.put("JAVA_HOME", javaHome);
            spec.environment(env);
            spec.commandLine(gradlewPath, "clean", "-PisSubBuild=true");
        });

        // 각 변형 빌드 (clean 없이 jar만 실행)
        for (Map<String, Object> variant : getVariants().get()) {
            JavaVersion javaVersion = (JavaVersion) variant.get("javaVersion");
            @SuppressWarnings("unchecked")
            Set<String> additionalSource = (Set<String>) variant.get("additionalSource");

            String classifier = S2BuildUtils.generateClassifier(javaVersion, additionalSource);

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
                );
            });

            getLogger().lifecycle("✅ Variant built successfully: {}", classifier);
        }
    }
}
