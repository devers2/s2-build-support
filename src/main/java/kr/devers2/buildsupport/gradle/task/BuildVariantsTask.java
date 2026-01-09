package kr.devers2.buildsupport.gradle.task;

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
 *
 * <p>
 * <b>[원래 설계 의도]</b><br>
 * 이 태스크는 원래 <b>여러 Java 버전과 여러 소스 조합으로 다양한 classifier를 가진
 * 아티팩트를 자동으로 생성</b>하기 위해 설계되었다.
 * </p>
 *
 * <p>
 * <b>예시 사용 사례:</b>
 * <ul>
 * <li>Java 8용 아티팩트: {@code s2-util-25.10.0.jar}</li>
 * <li>Java 11용 아티팩트: {@code s2-util-25.10.0-jdk11.jar}</li>
 * <li>추가 기능 포함: {@code s2-util-25.10.0-pdf.jar}</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>[현재 상태]</b><br>
 * 현재 s2-util 프로젝트는 <b>단일 설정(단일 Java 버전, 단일 소스 조합)만 사용</b>하므로,
 * 이 태스크는 과도하게 복잡하다. 따라서 {@code build.gradle}에서는
 * 간단한 태스크 조합({@code dependsOn 'clean', 'jar', 'sourcesJar', 'javadocJar'})으로
 * 대체하여 사용하고 있다.
 * </p>
 *
 * <p>
 * <b>[유지 이유]</b><br>
 * 이 클래스는 향후 다시 여러 variant를 빌드해야 할 경우를 대비하여 유지된다.
 * 현재는 사용되지 않지만, 필요 시 {@code build.gradle}에서 다시 활성화할 수 있다.
 * </p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph">Gradle Variants</a>
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
        // 빌드 환경 정보 추출
        String gradlewPath = getGradlewPath();
        String javaHome = System.getProperty("java.home");

        JavaVersion javaVersion = getJavaVersion().get();
        Set<String> additionalSource = getAdditionalSource().get();
        boolean generateSources = getGenerateSources().getOrElse(false);

        String displayInfo = buildDisplayInfo(javaVersion, additionalSource);
        getLogger().lifecycle("🚀 Starting build for: {}", displayInfo);

        try {
            // 1. Clean 실행
            executeClean(gradlewPath, javaHome);

            // 2. Build 실행 (jar, sourcesJar?, javadocJar)
            executeBuild(gradlewPath, javaHome, javaVersion, additionalSource, generateSources);

            getLogger().lifecycle("✅ Build completed successfully for: {}", displayInfo);

        } catch (Exception e) {
            handleBuildFailure(displayInfo, javaVersion, additionalSource, e);
        }

        // 3. 작업 완료 후 Workspace 복원
        restoreWorkspace(gradlewPath, javaHome, javaVersion, additionalSource);
    }

    // ========================================================================
    // Private 헬퍼 메서드 (Helper Methods)
    // ========================================================================

    /**
     * Gradlew 실행 파일 경로를 반환한다.
     *
     * @return Gradlew 절대 경로
     */
    private String getGradlewPath() {
        String rootDir = getProject().getRootDir().getAbsolutePath();
        String gradlew = System.getProperty("os.name").toLowerCase().contains("windows") ? GRADLEW_WINDOWS : GRADLEW_UNIX;
        return new File(rootDir, gradlew).getAbsolutePath();
    }

    /**
     * 빌드 정보 표시 문자열을 생성한다.
     *
     * @param javaVersion      Java 버전
     * @param additionalSource 추가 소스 목록
     * @return 빌드 정보 문자열
     */
    private String buildDisplayInfo(JavaVersion javaVersion, Set<String> additionalSource) {
        String displayInfo = String.format("Java %s", javaVersion);
        if (!additionalSource.isEmpty()) {
            displayInfo += ", Sources: " + additionalSource;
        }
        return displayInfo;
    }

    /**
     * Clean 태스크를 실행한다.
     *
     * @param gradlewPath Gradlew 경로
     * @param javaHome    JAVA_HOME 경로
     */
    private void executeClean(String gradlewPath, String javaHome) {
        getLogger().lifecycle("🧹 Cleaning build directory...");
        getExecOperations().exec(spec -> {
            configureEnvironment(spec, javaHome);
            spec.commandLine(gradlewPath, TASK_CLEAN, "-PisSubBuild=true");
        });
    }

    /**
     * 아티팩트 빌드 태스크를 실행한다.
     *
     * @param gradlewPath      Gradlew 경로
     * @param javaHome         JAVA_HOME 경로
     * @param javaVersion      Java 버전
     * @param additionalSource 추가 소스 목록
     * @param generateSources  소스 JAR 생성 여부
     */
    private void executeBuild(String gradlewPath, String javaHome, JavaVersion javaVersion,
            Set<String> additionalSource, boolean generateSources) {
        getLogger().lifecycle("📦 Building artifacts...");
        getExecOperations().exec(spec -> {
            configureEnvironment(spec, javaHome);

            List<String> command = buildCommand(gradlewPath, javaHome, javaVersion, additionalSource, generateSources);
            spec.commandLine(command);
        });
    }

    /**
     * 빌드 명령어를 구성한다.
     *
     * @param gradlewPath      Gradlew 경로
     * @param javaHome         JAVA_HOME 경로
     * @param javaVersion      Java 버전
     * @param additionalSource 추가 소스 목록
     * @param generateSources  소스 JAR 생성 여부
     * @return 명령어 목록
     */
    private List<String> buildCommand(String gradlewPath, String javaHome, JavaVersion javaVersion,
            Set<String> additionalSource, boolean generateSources) {
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
        if (isPublishing()) {
            command.add("-PbuildFatJar=false");
        }

        return command;
    }

    /**
     * 현재 배포 태스크가 실행 중인지 확인한다.
     *
     * @return true: 배포 중, false: 배포 아님
     */
    private boolean isPublishing() {
        return getProject().getGradle().getStartParameter().getTaskNames().stream()
                .anyMatch(t -> t.toLowerCase().contains("publish"));
    }

    /**
     * 빌드 실패 시 상세한 오류 메시지를 출력하고 예외를 다시 던진다.
     *
     * @param displayInfo      빌드 정보 문자열
     * @param javaVersion      Java 버전
     * @param additionalSource 추가 소스 목록
     * @param e                원본 예외
     */
    private void handleBuildFailure(String displayInfo, JavaVersion javaVersion,
            Set<String> additionalSource, Exception e) {
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
