package kr.s2.buildsupport.gradle.task;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * 설정된 Java 버전과 추가 소스를 기반으로 아티팩트를 빌드하는 태스크
 */
public abstract class BuildVariantsTask extends DefaultTask {

    // 태스크 이름 상수
    private static final String TASK_JAR = "jar";
    private static final String TASK_SOURCES_JAR = "sourcesJar";
    private static final String TASK_JAVADOC_JAR = "javadocJar";
    private static final String TASK_CLEAN = "clean";
    private static final String TASK_HELP = "help";

    // Gradle 실행 파일 이름
    private static final String GRADLEW_WINDOWS = "gradlew.bat";
    private static final String GRADLEW_UNIX = "gradlew";

    @Input
    public abstract Property<JavaVersion> getJavaVersion();

    @Input
    public abstract SetProperty<String> getAdditionalSource();

    @Input
    public abstract Property<Boolean> getGenerateSources();

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

    @TaskAction
    public void build() {
        String rootDir = getProject().getRootDir().getAbsolutePath();
        String gradlew = System.getProperty("os.name").toLowerCase().contains("windows") ? GRADLEW_WINDOWS : GRADLEW_UNIX;
        String gradlewPath = new File(rootDir, gradlew).getAbsolutePath();

        // 현재 실행 중인 Java 홈 경로
        String javaHome = System.getProperty("java.home");

        JavaVersion javaVersion = getJavaVersion().get();
        Set<String> additionalSource = getAdditionalSource().get();
        boolean generateSources = getGenerateSources().getOrElse(false);

        String displayInfo = String.format("Java %s", javaVersion);
        if (!additionalSource.isEmpty()) {
            displayInfo += ", Sources: " + additionalSource;
        }

        getLogger().lifecycle("🚀 Starting build for: {}", displayInfo);

        try {
            // 1. Clean 실행
            getLogger().lifecycle("🧹 Cleaning build directory...");
            getExecOperations().exec(spec -> {
                configureEnvironment(spec, javaHome);
                spec.commandLine(gradlewPath, TASK_CLEAN, "-PisSubBuild=true");
            });

            // 2. Build 실행 (jar, sourcesJar?, javadocJar)
            getLogger().lifecycle("📦 Building artifacts...");
            getExecOperations().exec(spec -> {
                configureEnvironment(spec, javaHome);

                List<String> command = new ArrayList<>();
                command.add(gradlewPath);
                command.add(TASK_JAR);

                if (generateSources) {
                    command.add(TASK_SOURCES_JAR);
                }

                command.add(TASK_JAVADOC_JAR);

                command.add("-Dorg.gradle.java.home=" + javaHome);
                command.add("-PtargetJavaVersion=" + javaVersion);
                command.add("-PtargetSources=" + String.join(",", additionalSource));
                command.add("-PenableSourceJar=" + generateSources);
                command.add("-PisSubBuild=true");

                // 'publish' 계열 태스크 실행 시 Fat JAR 빌드를 비활성화하는 프로퍼티 전달
                boolean isPublishing = getProject().getGradle().getStartParameter().getTaskNames().stream()
                        .anyMatch(t -> t.toLowerCase().contains("publish"));
                if (isPublishing) {
                    command.add("-PbuildFatJar=false");
                }
                spec.commandLine(command);
            });

            getLogger().lifecycle("✅ Build completed successfully for: {}", displayInfo);

        } catch (Exception e) {
            // 빌드 실패 시 명확한 에러 메시지 출력
            getLogger().error("");
            getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().error("❌ BUILD FAILED for: {}", displayInfo);
            getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().error("Details:");
            getLogger().error("  - Java Version: {}", javaVersion);
            getLogger().error("  - Additional Sources: {}", additionalSource);
            getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().error("");

            // 원본 예외를 다시 던져서 빌드 중단
            throw new org.gradle.api.GradleException(
                    String.format("Failed to build: %s", displayInfo),
                    e
            );
        }

        // 3. 작업 완료 후 Workspace 복원 (필요한 경우)
        // 단일 빌드 모드에서는 보통 target properties가 메인 설정과 일치하므로
        // 별도의 복원 과정이 필수적이지 않을 수 있으나, 안전을 위해 기본 상태로 refresh
        restoreWorkspace(gradlewPath, javaHome, javaVersion, additionalSource);
    }

    private void restoreWorkspace(String gradlewPath, String javaHome, JavaVersion javaVersion, Set<String> additionalSource) {
        try {
            String sourcesStr = String.join(",", additionalSource);

            getLogger().lifecycle("🧹 Refreshing workspace state...");

            getExecOperations().exec(spec -> {
                configureEnvironment(spec, javaHome);

                spec.commandLine(
                        gradlewPath,
                        TASK_HELP, // 가벼운 태스크 실행으로 설정 단계(Configuration Phase) 트리거
                        "-Dorg.gradle.java.home=" + javaHome,
                        "-PtargetJavaVersion=" + javaVersion,
                        "-PtargetSources=" + sourcesStr,
                        "-PisSubBuild=true",
                        "-q" // Quiet 모드
                );
            });
            getLogger().lifecycle("✨ Workspace refreshed.");

        } catch (Exception e) {
            getLogger().warn("⚠️  Failed to refresh workspace: {}", e.getMessage());
        }
    }
}
