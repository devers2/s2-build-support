package io.github.devers2.buildsupport.gradle.task;

import static io.github.devers2.buildsupport.S2BuildUtils.error;
import static io.github.devers2.buildsupport.S2BuildUtils.info;
import static io.github.devers2.buildsupport.S2BuildUtils.isKorean;
import static io.github.devers2.buildsupport.S2BuildUtils.warn;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Task for building artifacts based on configured Java versions and additional sources.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 설정된 Java 버전과 추가 소스를 기반으로 아티팩트를 빌드하는 태스크입니다.
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
@DisableCachingByDefault(because = "내부적으로 gradlew를 재귀 호출하여 외부 프로세스를 실행하므로 캐시할 수 없다.")
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

    /**
     * Returns the target Java version for this variant.
     *
     * @return Java version property
     */
    @Input
    public abstract Property<JavaVersion> getJavaVersion();

    /**
     * Returns the additional source directories to include.
     *
     * @return Set of additional source paths
     */
    @Input
    public abstract SetProperty<String> getAdditionalSource();

    /**
     * Returns whether to generate a sources JAR.
     *
     * @return Generate sources property
     */
    @Input
    public abstract Property<Boolean> getGenerateSources();

    /**
     * Returns the execution operations service.
     *
     * @return ExecOperations instance
     */
    @javax.inject.Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * Helper method to set JAVA_HOME environment variable in ExecSpec.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@code ExecSpec}에 {@code JAVA_HOME} 환경 변수를 설정하는 헬퍼 메서드입니다.
     *
     * @param spec     Execution specification | 실행 사양
     * @param javaHome JAVA_HOME path | JAVA_HOME 경로
     */
    private void configureEnvironment(org.gradle.process.ExecSpec spec, String javaHome) {
        Map<String, Object> env = new HashMap<>(System.getenv());
        env.put("JAVA_HOME", javaHome);
        spec.environment(env);
    }

    /**
     * Executes the build task for variants.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 변체(Variant) 빌드 태스크를 수행합니다.
     */
    @TaskAction
    public void build() {
        // 빌드 환경 정보 추출
        String gradlewPath = getGradlewPath();
        String javaHome = System.getProperty("java.home");

        JavaVersion javaVersion = getJavaVersion().get();
        Set<String> additionalSource = getAdditionalSource().get();
        boolean generateSources = getGenerateSources().getOrElse(false);

        String displayInfo = buildDisplayInfo(javaVersion, additionalSource);
        info(getProject(), "🚀 [" + displayInfo + "] 빌드를 시작합니다.", "🚀 Starting build for: " + displayInfo);

        try {
            // 1. Clean 실행
            executeClean(gradlewPath, javaHome);

            // 2. Build 실행 (jar, sourcesJar?, javadocJar)
            executeBuild(gradlewPath, javaHome, javaVersion, additionalSource, generateSources);

            info(getProject(), "✅ [" + displayInfo + "] 빌드가 성공적으로 완료되었습니다.", "✅ Build completed successfully for: " + displayInfo);

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
     * Returns the absolute path to the gradlew executable.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Gradlew 실행 파일의 절대 경로를 반환합니다.
     *
     * @return Absolute path to gradlew | Gradlew 절대 경로
     */
    private String getGradlewPath() {
        String rootDir = getProject().getRootDir().getAbsolutePath();
        String gradlew = System.getProperty("os.name").toLowerCase().contains("windows") ? GRADLEW_WINDOWS : GRADLEW_UNIX;
        return new File(rootDir, gradlew).getAbsolutePath();
    }

    /**
     * Builds a display information string for the build.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 빌드 정보 표시 문자열을 생성합니다.
     *
     * @param javaVersion      Java version | Java 버전
     * @param additionalSource Set of additional sources | 추가 소스 목록
     * @return Build info string | 빌드 정보 문자열
     */
    private String buildDisplayInfo(JavaVersion javaVersion, Set<String> additionalSource) {
        String displayInfo = String.format("Java %s", javaVersion);
        if (!additionalSource.isEmpty()) {
            displayInfo += ", Sources: " + additionalSource;
        }
        return displayInfo;
    }

    /**
     * Executes the clean task.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@code clean} 태스크를 실행합니다.
     *
     * @param gradlewPath Path to gradlew | Gradlew 경로
     * @param javaHome    Path to JAVA_HOME | JAVA_HOME 경로
     */
    private void executeClean(String gradlewPath, String javaHome) {
        info(getProject(), "🧹 빌드 디렉토리를 정리합니다(Clean)...", "🧹 Cleaning build directory...");
        getExecOperations().exec(spec -> {
            configureEnvironment(spec, javaHome);
            spec.commandLine(gradlewPath, TASK_CLEAN, "-PisSubBuild=true");
        });
    }

    /**
     * Executes the artifact build task.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 아티팩트 빌드 태스크를 실행합니다.
     *
     * @param gradlewPath      Path to gradlew | Gradlew 경로
     * @param javaHome         Path to JAVA_HOME | JAVA_HOME 경로
     * @param javaVersion      Java version | Java 버전
     * @param additionalSource Set of additional sources | 추가 소스 목록
     * @param generateSources  Whether to generate sources JAR | 소스 JAR 생성 여부
     */
    private void executeBuild(String gradlewPath, String javaHome, JavaVersion javaVersion,
            Set<String> additionalSource, boolean generateSources) {
        info(getProject(), "📦 아티팩트 빌드를 시작합니다...", "📦 Building artifacts...");
        getExecOperations().exec(spec -> {
            configureEnvironment(spec, javaHome);

            List<String> command = buildCommand(gradlewPath, javaHome, javaVersion, additionalSource, generateSources);
            spec.commandLine(command);
        });
    }

    /**
     * Builds the command line for the build task.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 빌드 명령어를 구성합니다.
     *
     * @param gradlewPath      Path to gradlew | Gradlew 경로
     * @param javaHome         Path to JAVA_HOME | JAVA_HOME 경로
     * @param javaVersion      Java version | Java 버전
     * @param additionalSource Set of additional sources | 추가 소스 목록
     * @param generateSources  Whether to generate sources JAR | 소스 JAR 생성 여부
     * @return List of command line arguments | 명령어 목록
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
     * Checks if a publishing task is currently running.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 현재 배포(Publishing) 태스크가 실행 중인지 확인합니다.
     *
     * @return {@code true} if publishing, {@code false} otherwise | 배포 여부
     */
    private boolean isPublishing() {
        return getProject().getGradle().getStartParameter().getTaskNames().stream()
                .anyMatch(t -> t.toLowerCase().contains("publish"));
    }

    /**
     * Handles build failure by logging details and rethrowing the exception.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 빌드 실패 시 상세한 오류 메시지를 출력하고 예외를 다시 던집니다.
     *
     * @param displayInfo      Build info string | 빌드 정보 문자열
     * @param javaVersion      Java version | Java 버전
     * @param additionalSource Set of additional sources | 추가 소스 목록
     * @param e                Source exception | 원본 예외
     */
    private void handleBuildFailure(String displayInfo, JavaVersion javaVersion,
            Set<String> additionalSource, Exception e) {
        String msgKo = "❌ [" + displayInfo + "] 빌드 실패";
        String msgEn = "❌ BUILD FAILED for: " + displayInfo;

        error(getProject(), "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        error(getProject(), isKorean() ? msgKo : msgEn, isKorean() ? msgKo : msgEn);
        error(getProject(), "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        info(getProject(), "상세 정보:", "Details:");
        info(getProject(), "  - Java Version: " + javaVersion, "  - Java Version: " + javaVersion);
        info(getProject(), "  - 추가 소스: " + additionalSource, "  - Additional Sources: " + additionalSource);
        error(getProject(), "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 원본 예외를 다시 던져서 빌드 중단
        throw new org.gradle.api.GradleException(
                isKorean() ? "빌드 실패: " + displayInfo : "Failed to build: " + displayInfo,
                e
        );
    }

    /**
     * Restores the workspace state after variant build.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 변체 빌드 완료 후 워크스페이스 상태를 복원합니다.
     *
     * @param gradlewPath      Path to gradlew | Gradlew 경로
     * @param javaHome         Path to JAVA_HOME | JAVA_HOME 경로
     * @param javaVersion      Java version | Java 버전
     * @param additionalSource Set of additional sources | 추가 소스 목록
     */
    private void restoreWorkspace(String gradlewPath, String javaHome, JavaVersion javaVersion, Set<String> additionalSource) {
        try {
            String sourcesStr = String.join(",", additionalSource);

            info(getProject(), "🧹 워크스페이스 상태를 복원하고 있습니다...", "🧹 Refreshing workspace state...");

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
            info(getProject(), "✨ 워크스페이스가 복원되었습니다.", "✨ Workspace refreshed.");

        } catch (Exception e) {
            warn(getProject(), "⚠️ 워크스페이스 복원 실패: " + e.getMessage(), "⚠️ Failed to refresh workspace: " + e.getMessage());
        }
    }
}
