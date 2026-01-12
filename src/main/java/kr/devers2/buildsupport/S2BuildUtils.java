package kr.devers2.buildsupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

/**
 * S2 프로젝트 빌드 관련 공통 유틸리티 클래스
 *
 * <p>
 * 이 클래스는 build.gradle에서 사용하는 복잡한 빌드 로직을 Java 코드로 캡슐화하여
 * 재사용성과 유지보수성을 높이는 핵심 유틸리티입니다.
 * </p>
 *
 * <h3>📦 패키징 전략 (Packaging Strategies)</h3>
 *
 * <h4>1. Publishing (Standard) - 'shadedPackagePrefix' 미설정 시</h4>
 * <ul>
 * <li>결과물: Standard JAR (Shadow OFF)</li>
 * <li>특징: 의존성을 포함하지 않음. POM을 통해 api(compile), implementation(runtime) 전이.</li>
 * </ul>
 *
 * <h4>2. Publishing (Shaded) - 'shadedPackagePrefix' 설정 시</h4>
 * <ul>
 * <li>결과물: Shaded JAR (Shadow ON)</li>
 * <li>특징: implementation/runtimeOnly 의존성을 Relocate하여 JAR에 포함.</li>
 * <li>전이: api는 JAR에서 제외하고 POM에 compile 스코프로 주입. implementation은 POM에서 제거.</li>
 * </ul>
 *
 * <h4>3. Build (Fat JAR) - 'shadedPackagePrefix' 미설정 시</h4>
 * <ul>
 * <li>결과물: Fat JAR (Shadow ON)</li>
 * <li>특징: Relocation 없이 모든 의존성을 JAR에 포함.</li>
 * </ul>
 *
 * <h4>4. Build (Relocated Fat JAR) - 'shadedPackagePrefix' 설정 시</h4>
 * <ul>
 * <li>결과물: Fat JAR with Relocation (Shadow ON)</li>
 * <li>특징: 모든 의존성을 지정된 패키지로 Relocate하여 JAR에 포함.</li>
 * </ul>
 *
 * <h3>🔗 의존성 전이 및 패키징 규칙 (Dependency Rules)</h3>
 * <ul>
 * <li><b>api</b>: 라이브러리 공개 인터페이스에 노출되는 의존성.
 * <ul>
 * <li>Standard: POM에 'compile' 스코프로 유지됨.</li>
 * <li>Shaded: JAR에서는 제외되나, POM에 'compile' 스코프로 수동 주입되어 전이됨.</li>
 * </ul>
 * </li>
 * <li><b>implementation</b>: 내부 구현 전용 의존성.
 * <ul>
 * <li>Standard: POM에 'runtime' 스코프로 유지됨.</li>
 * <li>Shaded: JAR에 포함(Relocate)되며, POM에서는 제거됨 (전이 차단).</li>
 * </ul>
 * </li>
 * <li><b>compileOnly / compileOnlyApi / provided</b>: 빌드 시에만 필요하거나 런타임에 별도로 제공됨.
 * <ul>
 * <li>Standard & Shaded: JAR와 POM 모두에서 제외됨.</li>
 * <li>특이사항: S2BuildUtils에 의해 README.md의 'Manual Setup' 권장 목록에 포함될 수 있음.</li>
 * </ul>
 * </li>
 * <li><b>runtimeOnly</b>: 실행 시에만 필요한 의존성.
 * <ul>
 * <li>Standard: POM에 'runtime' 스코프로 유지됨.</li>
 * <li>Shaded: JAR에 포함(Relocate)되며, POM에서는 제거됨.</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @see LibrariesPublisher
 * @see MavenPublishStrategy
 */
public class S2BuildUtils {

    // ANSI Color Constants for Terminal Output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";

    // ========================================================================
    // 프로젝트 통합 설정 메서드 (Unified Configuration)
    // ========================================================================

    /**
     * 프로젝트의 모든 설정을 올바른 순서로 수행하는 통합 메서드
     * <p>
     * 실행 순서:
     * 1. 동적 의존성 주입 (가장 먼저 - compileOnly 의존성 추가)
     * 2. 소스 파일 토글 (활성화된 기능에 따라 .java <-> .java.txt)
     * 3. 패키징 설정 (JAR/Shadow JAR 설정)
     * 4. 의존성 복사 태스크 등록
     * 5. README 파일 업데이트
     * </p>
     *
     * @param p Gradle 프로젝트 객체
     */
    public static void configureProject(Project project) {
        // 모든 의존성 정의가 완료된 후 실행하기 위해 afterEvaluate 사용
        project.afterEvaluate(p -> {
            // 추가할 Variant ID 목록
            Set<String> variantIds = new LinkedHashSet<>();
            // 추가할 소스 목록
            Set<String> extraSources = new LinkedHashSet<>();
            // 제외할 소스 목록
            Set<String> excludedSources = new LinkedHashSet<>();
            // 추가할 의존성 목록
            Map<String, String> extraDependencyMap = new HashMap<>();

            analyzeDynamicSourceInfo(project, variantIds, extraSources, excludedSources, extraDependencyMap);

            // 1. 동적 의존성 주입 (Dynamic Dependency Injection) 및 추가 파일/variantId 정보 수집
            injectDynamicDependencies(p, extraDependencyMap);

            // 2. 라이선스 자동화 설정 (License Plugin Integration)
            configureLicenseAutomation(p);

            // 3. 소스 파일 토글
            performSourceToggle(p, extraSources, excludedSources);

            // 3. 패키징 및 빌드 설정 (경로 자동 계산 포함)
            // ext.skipPackaging = true인 프로젝트는 패키징 스킵
            if (!Boolean.TRUE.equals(p.findProperty("skipPackaging"))) {
                configurePackaging(p);
            }

            // 4. README 파일 버전 & 의존성 가이드 업데이트
            updateReadmeWithVersionAndDependencies(p, p.file("README.md"));
        });
    }

    /**
     * Variant ID, 추가 소스, 제외 소스 정보 분석
     *
     * @param variantIds         추가할 Variant ID 목록
     * @param extraSources       추가할 소스 목록
     * @param excludedSources    제외할 소스 목록
     * @param extraDependencyMap 추가할 의존성 목록
     */
    @SuppressWarnings("unchecked")
    private static void analyzeDynamicSourceInfo(Project project, Set<String> variantIds, Set<String> extraSources, Set<String> excludedSources, Map<String, String> extraDependencyMap) {
        Object activeFeaturesObj = null;
        Object dynamicSourceInfoMapObj = null;
        try {
            if (project.hasProperty("activeFeatures")) {
                activeFeaturesObj = project.property("activeFeatures");
            } else {
                activeFeaturesObj = project.getRootProject().findProperty("activeFeatures");
            }
        } catch (Exception ignored) {
        }
        try {
            if (project.hasProperty("dynamicSourceInfoMap")) {
                dynamicSourceInfoMapObj = project.property("dynamicSourceInfoMap");
            } else {
                dynamicSourceInfoMapObj = project.getRootProject().findProperty("dynamicSourceInfoMap");
            }
        } catch (Exception ignored) {
        }

        if (!(dynamicSourceInfoMapObj instanceof Map)) {
            project.getLogger().lifecycle("🔍 [Dynamic Dependencies] No dynamicSourceInfo found, skipping dynamic injection.");
            return;
        }

        Set<String> activeFeatures = new HashSet<>();
        if (activeFeaturesObj instanceof Collection) {
            for (Object f : (Collection<?>) activeFeaturesObj) {
                if (f != null)
                    activeFeatures.add(String.valueOf(f));
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> dynamicSourceInfoMap = (Map<String, ?>) dynamicSourceInfoMapObj;
        for (String key : dynamicSourceInfoMap.keySet()) {
            Object dynamicSourceInfoObj = dynamicSourceInfoMap.get(key);
            if (dynamicSourceInfoObj instanceof Map) {
                Map<String, ?> dynamicSourceInfo = (Map<String, ?>) dynamicSourceInfoObj;
                if (activeFeatures.contains(key)) {
                    variantIds.add((String) dynamicSourceInfo.get("variantId"));
                    extraSources.addAll((Collection<String>) dynamicSourceInfo.get("sources"));

                    List<Map<String, String>> dependencies = (List<Map<String, String>>) dynamicSourceInfo.get("dependencies");
                    for (Map<String, String> dependency : dependencies) {
                        String config = dependency.getOrDefault("configuration", "implementation");
                        String group = dependency.get("group");
                        String name = dependency.get("name");
                        String version = dependency.get("version");

                        if (group != null && !group.isBlank() && name != null && !name.isBlank()) {
                            String notation = (version != null && !version.isBlank()) ? group + ":" + name + ":" + version : group + ":" + name;
                            extraDependencyMap.put(notation, config);
                        }
                    }
                } else {
                    excludedSources.addAll((Collection<String>) dynamicSourceInfo.get("sources"));
                }
            }
        }
    }

    /**
     * activeFeatures 및 dynamicSourceInfo 기반 의존성 주입 구현
     *
     * @param project            프로젝트
     * @param extraDependencyMap 추가할 의존성 목록
     */
    private static void injectDynamicDependencies(Project project, Map<String, String> extraDependencyMap) {
        for (String notation : extraDependencyMap.keySet()) {
            String config = extraDependencyMap.get(notation);
            try {
                project.getDependencies().add(config, notation);
                project.getLogger().lifecycle("   ➕ Adding dependency [" + config + "]: " + notation);
            } catch (Exception e) {
                project.getLogger().warn("   ⚠️ Failed to add dependency: " + notation + " -> " + e.getMessage());
            }
        }
    }

    /**
     * 소스 파일 토글 수행 (.java <-> .java.txt)
     * 초기화 단계에서 실행되며 활성화된 기능에 따라 파일명 변경
     *
     * @param project        Gradle 프로젝트 객체
     * @param javaSourceRoot Java 소스 루트 경로
     * @param extraSources   활성화된 추가 소스 목록
     */
    private static void performSourceToggle(Project project, Set<String> extraSources, Set<String> excludedSources) {
        String javaSourceRoot = (String) project.getRootProject().findProperty("JAVA_SRC_ROOT");

        for (String extraSource : extraSources) {
            String fullPathBase = javaSourceRoot + extraSource;

            File fileJava = project.file(fullPathBase);
            File fileTxt = project.file(fullPathBase + ".txt");
            if (fileTxt.exists()) {
                fileTxt.renameTo(fileJava);
            }
        }

        for (String excludedSource : excludedSources) {
            String fullPathBase = javaSourceRoot + excludedSource;

            File fileJava = project.file(fullPathBase);
            File fileTxt = project.file(fullPathBase + ".txt");
            if (fileJava.exists()) {
                fileJava.renameTo(fileTxt);
            }
        }
    }

    // ========================================================================
    // 경로 계산 관련 메서드
    // ========================================================================

    /**
     * 비활성화된 기능의 소스 파일 경로(.txt) 목록 반환
     *
     * @param dynamicSourceInfo 동적 소스 설정 정보 (Map<기능명, Map<설정, 값>>)
     * @param activeSources     활성화된 추가 소스 목록
     * @return 제외할 소스 파일 경로 목록
     */
    public static Set<String> resolveExcludedPaths(Map<String, Map<String, Object>> dynamicSourceInfo, Set<String> activeSources) {
        Set<String> excludedPaths = new LinkedHashSet<>();

        if (dynamicSourceInfo == null) {
            return excludedPaths;
        }

        for (String featureName : dynamicSourceInfo.keySet()) {
            // 활성화되지 않은 기능인 경우
            if (activeSources == null || !activeSources.contains(featureName)) {
                Map<String, Object> config = dynamicSourceInfo.get(featureName);
                if (config != null) {
                    Object sourcesObj = config.get("sources");
                    if (sourcesObj instanceof Collection) {
                        for (Object source : (Collection<?>) sourcesObj) {
                            // 제외 목록에는 항상 .txt 확장자가 붙은 경로를 추가 (jar 제외용)
                            excludedPaths.add(String.valueOf(source) + ".txt");
                        }
                    }
                }
            }
        }
        return excludedPaths;
    }

    // ========================================================================
    // 동적 의존성 및 라이선스 자동화 (Dynamic Dependency & License Automation)
    // ========================================================================

    /**
     * 동적 소스 정보에서 라이선스 파일 경로 추출 (Private Helper)
     */
    private static Set<String> extractLicensesFromSourceInfo(Map<String, Map<String, Object>> dynamicSourceInfo, Collection<?> activeFeatures) {
        Set<String> licensePaths = new LinkedHashSet<>();
        if (dynamicSourceInfo == null || activeFeatures == null) {
            return licensePaths;
        }

        Set<String> activeFeatureNames = new HashSet<>();
        for (Object feature : activeFeatures) {
            if (feature != null) {
                activeFeatureNames.add(String.valueOf(feature).trim());
            }
        }

        for (String featureName : dynamicSourceInfo.keySet()) {
            if (activeFeatureNames.contains(featureName)) {
                Object configObj = dynamicSourceInfo.get(featureName);
                if (configObj instanceof Map) {
                    Map<?, ?> config = (Map<?, ?>) configObj;
                    Object licensesObj = config.get("licenses");
                    if (licensesObj instanceof Collection) {
                        for (Object license : (Collection<?>) licensesObj) {
                            if (license != null) {
                                licensePaths.add(String.valueOf(license));
                            }
                        }
                    }
                }
            }
        }
        return licensePaths;
    }

    /**
     * hierynomus.license 플러그인 적용 및 패키징 연동 설정
     */
    private static void configureLicenseAutomation(Project project) {
        // 활성화된 기능 중 compileOnly 의존성이 있는지 여부와 상관없이,
        // 실제 프로젝트에 주입된 의존성을 기반으로 라이선스 자동화를 수행합니다.

        // 플러그인 적용
        project.getPluginManager().apply("com.github.hierynomus.license");

        // Gradle 9+ 호환성 문제 해결 (reporting.baseDir 제거됨)
        try {
            // ReportingExtension에 baseDir 속성이 없으므로, ExtraProperties로 주입 시도
            Object reporting = project.getExtensions().findByName("reporting");
            if (reporting instanceof org.gradle.api.plugins.ExtensionAware) {
                org.gradle.api.plugins.ExtensionAware extAware = (org.gradle.api.plugins.ExtensionAware) reporting;
                if (!extAware.getExtensions().getExtraProperties().has("baseDir")) {
                    extAware.getExtensions().getExtraProperties().set("baseDir", project.getLayout().getBuildDirectory().dir("reports").get().getAsFile());
                    project.getLogger().lifecycle("🔧 [License] Applied workaround for 'reporting.baseDir' (Gradle 9+ compatibility)");
                }
            }
        } catch (Exception e) {
            project.getLogger().warn("⚠️ [License] Failed to apply compatibility workaround: " + e.getMessage());
        }

        // downloadLicenses 태스크 설정
        project.getTasks().named("downloadLicenses").configure(task -> {
            // 태스크 실행 전 출력 디렉토리를 비워주는 로직 추가 (과거 잔적 파일 포함 방지)
            task.doFirst(t -> {
                java.io.File outputDir = project.getLayout().getBuildDirectory().dir("reports/license").get().getAsFile();
                if (outputDir.exists()) {
                    project.delete(outputDir);
                }
                outputDir.mkdirs(); // 디렉토리가 없으면 태스크 실행 시 오류가 발생할 수 있으므로 재생성
                project.getLogger().lifecycle("🧹 [License] Cleared stale license reports before generation.");
            });

            // 설정: 의존성 및 리포트 포함 여부
        });

        // 소스 헤더 체크(LicenseCheck) 기능 비활성화 (LICENSE 파일 없음 오류 방지)
        // 우리는 의존성 리포트(downloadLicenses)만 필요함
        // 컴파일 타임에 플러그인 클래스가 없으므로 이름으로 찾아 비활성화
        try {
            project.getTasks().named("licenseMain").configure(t -> t.setEnabled(false));
            project.getTasks().named("licenseTest").configure(t -> t.setEnabled(false));
        } catch (Exception e) {
            // ignore if tasks don't exist
        }

        // Jar 및 ShadowJar 패키징 시 라이선스 포함 설정
        project.getTasks().named("downloadLicenses").configure(downloadTask -> {
            // downloadLicenses 태스크 완료 후 결과물 경로 확인
            // 기본적으로 build/reports/license 에 생성됨 (플러그인 버전에 따라 다를 수 있음)
            // 여기서는 downloadLicenses의 출력을 패키징 태스크에 연결
        });

        // downloadLicenses 태스크 찾기
        org.gradle.api.Task downloadLicensesTask = project.getTasks().findByName("downloadLicenses");
        if (downloadLicensesTask != null) {
            // 출력 디렉토리 (기본값 가정)
            java.io.File licenseOutputDir = project.getLayout().getBuildDirectory().dir("reports/license").get().getAsFile();

            // 모든 JAR 관련 태스크 (jar, shadowJar)에 포함
            project.getTasks().withType(org.gradle.api.tasks.bundling.Jar.class).configureEach(jarTask -> {
                jarTask.dependsOn(downloadLicensesTask);
                jarTask.from(licenseOutputDir, copySpec -> {
                    copySpec.into("licenses/third-party");
                });
                project.getLogger().lifecycle("🔗 [License] Linked 'downloadLicenses' to task: " + jarTask.getName());
            });
        }
    }

    // ========================================================================
    // 초기화 단계 실행 메서드 (Configuration Phase)
    // ========================================================================

    /**
     * Java 버전에 따라 Servlet Import 구문 업데이트
     *
     * 기능:
     * 1. Java 11 이상이면 javax.servlet -> jakarta.servlet
     * 2. Java 11 미만이면 jakarta.servlet -> javax.servlet
     *
     * @param project        Gradle 프로젝트 객체
     * @param javaSourceRoot Java 소스 루트 경로
     * @param javaVersion    Java 버전
     */
    public static void updateServletImports(Project project, String javaSourceRoot, JavaVersion javaVersion) {
        File sourceDir = project.file(javaSourceRoot);
        if (!sourceDir.exists()) {
            return;
        }

        // Java 11 이상 여부 확인
        boolean java11OrAbove = false;
        try {
            java11OrAbove = javaVersion.isJava11Compatible();
        } catch (Exception ignored) {
            // API 차이 대비: 비교로 대체
            java11OrAbove = javaVersion.compareTo(JavaVersion.VERSION_11) >= 0;
        }

        String fromPackage = java11OrAbove ? "javax.servlet" : "jakarta.servlet";
        String toPackage = java11OrAbove ? "jakarta.servlet" : "javax.servlet";

        processFilesRecursively(sourceDir, file -> {
            if (!file.getName().endsWith(".java")) {
                return;
            }

            try {
                java.nio.file.Path path = file.toPath();
                String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);

                // servlet 관련 import가 없으면 건너뛰기
                if (!content.contains("javax.servlet") && !content.contains("jakarta.servlet")) {
                    return;
                }

                // 클래스 선언(public class) 이전 부분만 변경
                int classIndex = content.indexOf("public class");
                if (classIndex == -1) {
                    // public class가 없다면 전체 파일에서 import 교체 시도
                    if (content.contains(fromPackage)) {
                        String updated = content.replaceAll("import\\s+" + java.util.regex.Pattern.quote(fromPackage) + "\\.", "import " + toPackage + ".");
                        if (!content.equals(updated)) {
                            java.nio.file.Files.write(path, updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            System.out.println("🔄 [Servlet Import] Updated " + file.getName() + " (" + fromPackage + " → " + toPackage + ")");
                        }
                    }
                    return;
                }

                String beforeClass = content.substring(0, classIndex);
                String afterClass = content.substring(classIndex);

                if (beforeClass.contains(fromPackage)) {
                    String updatedBefore = beforeClass.replaceAll("import\\s+" + java.util.regex.Pattern.quote(fromPackage) + "\\.", "import " + toPackage + ".");
                    String newContent = updatedBefore + afterClass;
                    if (!content.equals(newContent)) {
                        java.nio.file.Files.write(path, newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        System.out.println("🔄 [Servlet Import] Updated " + file.getName() + " (" + fromPackage + " → " + toPackage + ")");
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("❌ [Servlet Import] Failed to update " + file.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * 소스 파일의 저작권 연도 업데이트
     *
     * 기능:
     * 1. 지정된 소스 디렉토리의 모든 파일을 순회
     * 2. "Copyright (c) YYYY" 패턴을 찾아 현재 연도로 업데이트
     *
     * @param project     Gradle 프로젝트 객체
     * @param sourcePaths 검사할 소스 경로 배열
     */
    public static void updateCopyright(Project project, String[] sourcePaths) {
        String currentYear = String.valueOf(java.time.Year.now().getValue());
        // 패턴: Copyright (c) 2020 - [연도] devers2
        java.util.regex.Pattern copyrightPattern = java.util.regex.Pattern.compile("Copyright \\(c\\) 2020 - (\\d{4}) devers2");

        // 대상 확장자 목록
        Set<String> targetExtensions = new LinkedHashSet<>();
        targetExtensions.addAll(
                java.util.Arrays.asList(
                        // Java/Kotlin/Python/Groovy/Gradle/기타
                        "java", "kt", "kts", "groovy", "py", "gradle", "txt", "properties",
                        // JavaScript 관련 (표준 및 모듈 형식)
                        "js", "mjs", "cjs",
                        // CSS 관련 (표준 및 전처리기)
                        "css", "scss", "sass", "less",
                        // 문서/XML
                        "md", "xml", "yml", "yaml"
                )
        );

        for (String sourcePath : sourcePaths) {
            File sourceDir = project.file(sourcePath);
            if (!sourceDir.exists()) {
                continue;
            }

            processFilesRecursively(sourceDir, file -> {
                String fileName = file.getName();
                String extension = getFileExtension(fileName);

                if (!targetExtensions.contains(extension)) {
                    return;
                }

                try {
                    java.nio.file.Path path = file.toPath();
                    String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher matcher = copyrightPattern.matcher(content);

                    if (matcher.find()) {
                        String oldYear = matcher.group(1);
                        if (!oldYear.equals(currentYear)) {
                            String newContent = matcher.replaceFirst("Copyright (c) 2020 - " + currentYear + " devers2");
                            java.nio.file.Files.write(path, newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            System.out.println("©️  [Copyright] Updated " + fileName + " (" + oldYear + " → " + currentYear + ")");
                        }
                    }
                } catch (java.io.IOException e) {
                    System.err.println("❌ [Copyright] Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }
    }

    /**
     * 지정된 파일의 버전 정보를 템플릿 기반으로 업데이트한다.
     * - 예: "Version: {{=version}} ({{=release-date}})"
     * - project.version이 파일에 기록된 기존 정보와 다를 때만 갱신한다.
     *
     * <p>
     * <b>Example Usage (in build.gradle):</b>
     * </p>
     *
     * <pre>{@code
     * // 1. build.gradle에서 다음과 같이 호출
     * kr.devers2.buildsupport.S2BuildUtils.updateVersionInFile(project, "README.md", "### Version: {{=version}} ({{=release-date}})", project.version.toString());
     *
     * // 2. README.md 파일에 아래 내용이 있다고 가정:
     * // ### Version: 1.0.0 (2023-01-01)
     *
     * // 3. project.version = '1.1.0'으로 태스크 실행 후, README.md 내용은 아래와 같이 변경됨:
     * // ### Version: 1.1.0 (YYYY-MM-DD) // (여기서 YYYY-MM-DD는 현재 날짜)
     * }</pre>
     *
     * @param project         Gradle 프로젝트 객체
     * @param filePath        업데이트할 파일 경로
     * @param versionTemplate 버전 정보 템플릿. `{{=version}}`과 `{{=release-date}}` 플레이스홀더를 포함해야 한다.
     * @param newVersion      새로운 버전 문자열
     */
    public static void updateVersionInFile(Project project, String filePath, String versionTemplate, String newVersion) {
        File targetFile = project.file(filePath);
        if (!targetFile.exists()) {
            System.err.println("❌ [" + filePath + "] File not found in project root.");
            return;
        }

        try {
            String versionPlaceholder = "{{=version}}";
            String datePlaceholder = "{{=release-date}}";

            if (!versionTemplate.contains(versionPlaceholder) || !versionTemplate.contains(datePlaceholder)) {
                System.err.println(
                        "❌ [" + filePath + "] versionTemplate must contain {{=version}} and {{=release-date}}."
                );
                return;
            }

            // 1. 템플릿을 기반으로 검색할 정규식을 생성한다.
            // 플레이스홀더 순서를 기억하고, 각 부분을 정규식으로 변환한다.
            String tempTemplate = versionTemplate;
            List<String> placeholders = new ArrayList<>();
            Pattern p = Pattern.compile("(\\{\\{=version\\}\\}|\\{\\{=release-date\\}\\})");
            Matcher m = p.matcher(tempTemplate);
            while (m.find()) {
                placeholders.add(m.group(1));
            }

            String[] literals = tempTemplate.split("(\\{\\{=version\\}\\}|\\{\\{=release-date\\}\\})");
            StringBuilder regexBuilder = new StringBuilder();

            for (int i = 0; i < literals.length; i++) {
                if (!literals[i].isBlank()) {
                    regexBuilder.append(Pattern.quote(literals[i]));
                }
                if (i < placeholders.size()) {
                    String placeholder = placeholders.get(i);
                    if (placeholder.equals(versionPlaceholder)) {
                        regexBuilder.append("([\\w.-]+)"); // 버전 캡처 그룹
                    } else if (placeholder.equals(datePlaceholder)) {
                        regexBuilder.append("([\\d-]+)"); // 날짜 캡처 그룹
                    }
                }
            }
            String regex = regexBuilder.toString();

            java.nio.file.Path path = targetFile.toPath();
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            String newDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);

            if (!matcher.find()) {
                System.err.println("⚠️  [" + filePath + "] Could not find the version pattern from template: " + versionTemplate);
                return;
            }

            // 캡처 그룹 인덱스를 동적으로 할당한다.
            String existingVersion = "";
            String existingDate = "";
            int groupCount = 1;
            for (String placeholder : placeholders) {
                if (placeholder.equals(versionPlaceholder)) {
                    existingVersion = matcher.group(groupCount++);
                } else if (placeholder.equals(datePlaceholder)) {
                    existingDate = matcher.group(groupCount++);
                }
            }

            // 버전이 동일하면 업데이트를 건너뛴다.
            if (existingVersion.equals(newVersion)) {
                System.out.println("ℹ️  [" + filePath + "] Version is unchanged (" + newVersion + "). Skipping update.");
                return;
            }

            // 2. 템플릿에 실제 값을 채워 새 라인 생성
            String newLine = versionTemplate
                    .replace(versionPlaceholder, newVersion)
                    .replace(datePlaceholder, newDate);

            // 3. 내용 교체
            String updatedContent = matcher.replaceFirst(Matcher.quoteReplacement(newLine));

            if (!content.equals(updatedContent)) {
                java.nio.file.Files.write(path, updatedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("📝 [" + filePath + "] Updated version: " + existingVersion + " → " + newVersion + " (Date: " + existingDate + " → " + newDate + ")");
            }

        } catch (java.io.IOException e) {
            System.err.println("❌ [" + filePath + "] Failed to update: " + e.getMessage());
        }
    }

    // ========================================================================
    // JAR 관련 유틸리티 메서드
    // ========================================================================

    /**
     * JAR 파일명 생성
     * 예: s2-util-25.8-java8-pdf.jar
     *
     * @param archivesName 아카이브 기본 이름 (예: s2-util)
     * @param version      버전 (예: 25.8)
     * @param classifier   classifier (예: java8-pdf)
     * @return 전체 JAR 파일명
     */
    public static String getJarFileName(String archivesName, String version, String classifier) {
        StringBuilder sb = new StringBuilder(archivesName)
                .append("-")
                .append(version);

        if (classifier != null && !classifier.isBlank()) {
            sb.append("-").append(classifier);
        }

        return sb.append(".jar").toString();
    }

    // ========================================================================
    // Private 헬퍼 메서드
    // ========================================================================

    /**
     * 재귀적으로 디렉토리의 모든 파일을 순회하며 처리
     *
     * @param directory     처리할 디렉토리
     * @param fileProcessor 각 파일에 적용할 처리 로직
     */
    private static void processFilesRecursively(File directory, java.util.function.Consumer<File> fileProcessor) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                processFilesRecursively(file, fileProcessor);
            } else {
                fileProcessor.accept(file);
            }
        }
    }

    /**
     * 파일명에서 확장자를 추출
     *
     * @param fileName 파일명
     * @return 확장자 (소문자, 점 제외)
     */
    private static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    // ========================================================================
    // Gradle Task 설정 헬퍼 메서드
    // ========================================================================

    /**
     * 빌드 및 Javadoc 관련 소스 설정 적용 (Internal Helper)
     * <p>
     * compileJava, Jar, Javadoc 태스크에 대해 소스 제외 및 표준 옵션을 설정한다.
     * </p>
     *
     * @param project       Gradle 프로젝트 객체
     * @param excludedPaths 제외할 소스 경로 목록
     */
    private static void applySourceSettings(Project project, Set<String> excludedPaths) {
        // 1. 소스 제외 설정 (컴파일 및 JAR)
        if (excludedPaths != null && !excludedPaths.isEmpty()) {
            // 컴파일 태스크
            project.getTasks().named("compileJava", org.gradle.api.tasks.compile.JavaCompile.class).configure(task -> {
                task.exclude(fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString()));
            });

            // JAR 태스크 (sourcesJar 포함)
            project.getTasks().withType(Jar.class).configureEach(task -> {
                task.exclude(fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString()));
            });
        }

        // 2. Javadoc 설정 (표준 옵션 및 제외 경로)
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.setEncoding("UTF-8");
            options.setDocEncoding("UTF-8");
            options.setCharSet("UTF-8");
            options.setJFlags(java.util.Arrays.asList("-Dfile.encoding=UTF-8"));
            options.addStringOption("encoding", "UTF-8");
            options.addStringOption("docencoding", "UTF-8");
            options.addStringOption("charset", "UTF-8");

            // 모든 경고 및 오류 검사 비활성화
            options.addStringOption("Xdoclint:none", "-quiet");

            // 모든 접근 제어자 문서화
            options.addBooleanOption("private", true);
            options.setMemberLevel(JavadocMemberLevel.PROTECTED);

            // 링크 및 상속 설정
            options.setLinkSource(true);
            options.setUse(true);

            // 타이틀 설정
            options.setWindowTitle("S2Util API Documentation");
            options.setDocTitle("S2Util API Documentation");

            // 커스텀 태그
            options.setTags(java.util.Arrays.asList("details:a:Details:", "example:a:Example:"));

            // 표준 태그
            options.setAuthor(true);
            options.setVersion(true);

            // HTML5 및 추가 옵션
            options.addBooleanOption("html5", true);
            options.addBooleanOption("notimestamp", true);

            // 오류 처리
            javadoc.setFailOnError(true);

            // Javadoc에서도 제외 경로 적용
            if (excludedPaths != null && !excludedPaths.isEmpty()) {
                javadoc.exclude(
                        fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString())
                );
            }
        });
    }

    /**
     * JAR 및 배포 패키지 통합 설정 (자동 경로 계산 포함)
     * <p>
     * 이 메서드는 다음 프로젝트 속성을 자동으로 읽어 설정을 완료합니다:
     * - activeFeatures, dynamicSourceInfo: 라이선스 및 제외 경로 계산용
     * - defaultExcluded, excludedSourcePaths: 추가 제외 경로
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     */
    public static void configurePackaging(Project project) {
        // 1. 동적 기능 정보 수집
        Object activeFeaturesObj = project.findProperty("activeFeatures");
        if (activeFeaturesObj == null) {
            activeFeaturesObj = project.getRootProject().findProperty("activeFeatures");
        }
        Object dynamicSourceInfoObj = project.findProperty("dynamicSourceInfo");
        if (dynamicSourceInfoObj == null) {
            dynamicSourceInfoObj = project.getRootProject().findProperty("dynamicSourceInfo");
        }

        // 안정적인 타입 변환
        Collection<?> activeFeatures = (activeFeaturesObj instanceof Collection) ? (Collection<?>) activeFeaturesObj : new java.util.ArrayList<>();
        Map<String, Map<String, Object>> sourceInfoMap = new java.util.HashMap<>();
        if (dynamicSourceInfoObj instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) dynamicSourceInfoObj;
            rawMap.forEach((k, v) -> {
                if (k != null && v instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) v;
                    sourceInfoMap.put(String.valueOf(k), config);
                }
            });
        }

        // 2. 라이선스 경로 계산 (자동 감지 README*.md, licenses/* 포함)
        Set<String> extraFiles = new LinkedHashSet<>();

        // 2-1. 프로젝트 디렉토리의 README 파일 감지 (README.md, README-KO.md 등)
        // 단, dynamicSourceInfo에 등록된 라이선스 파일은 activeFeatures에 있을 때만 포함한다.
        Set<String> allDynamicLicenses = extractLicensesFromSourceInfo(sourceInfoMap, sourceInfoMap.keySet());
        Set<String> activeDynamicLicenses = extractLicensesFromSourceInfo(sourceInfoMap, activeFeatures);

        File projectDir = project.getProjectDir();
        File[] readmeFiles = projectDir.listFiles((dir, name) -> name.toUpperCase().startsWith("README") && name.toUpperCase().endsWith(".MD"));
        if (readmeFiles != null) {
            for (File f : readmeFiles) {
                String fileName = f.getName();

                // 동적 라이선스 정보에 등록된 파일이라면 활성화 여부를 확인한다.
                if (allDynamicLicenses.contains(fileName)) {
                    if (activeDynamicLicenses.contains(fileName)) {
                        extraFiles.add(fileName); // 활성화된 경우만 포함
                    } else {
                        project.getLogger().lifecycle("⏭️ [License] Skipping inactive dynamic license file: " + fileName);
                    }
                } else {
                    // 동적 라이선스로 등록되지 않은 일반 README는 항상 포함 (README.md 등)
                    extraFiles.add(fileName);
                }
            }
        }

        // 2-2. licenses/ 디렉토리 내 모든 파일 감지
        File licensesDir = new File(projectDir, "licenses");
        if (licensesDir.exists() && licensesDir.isDirectory()) {
            File[] licenseFiles = licensesDir.listFiles();
            if (licenseFiles != null) {
                for (File f : licenseFiles) {
                    if (f.isFile()) {
                        String relPath = "licenses/" + f.getName();

                        // NOTICE 파일은 동적 업데이트 대상으로 특별 처리
                        if (f.getName().equals("NOTICE")) {
                            File processedNotice = updateNoticeFileWithActiveFeatures(project, f);
                            if (processedNotice != null && processedNotice.exists()) {
                                extraFiles.add(processedNotice.getAbsolutePath());
                                continue;
                            }
                        }

                        // 동적 라이선스 목록에 있는데 활성화되지 않았으면 제외
                        if (allDynamicLicenses.contains(relPath) && !activeDynamicLicenses.contains(relPath)) {
                            project.getLogger().lifecycle("⏭️ [License] Skipping inactive dynamic license file: " + relPath);
                            continue;
                        }
                        extraFiles.add(relPath);
                    }
                }
            }
        }

        // 2-3. 동적 소스 정보에서 라이선스 추출 (Private Helper 사용)
        extraFiles.addAll(extractLicensesFromSourceInfo(sourceInfoMap, activeFeatures));

        // 3. 제외 경로 계산
        Set<String> activeFeaturesSet = new java.util.HashSet<>();
        activeFeatures.forEach(f -> {
            if (f != null)
                activeFeaturesSet.add(String.valueOf(f).trim());
        });
        Set<String> subDynamicExcluded = resolveExcludedPaths(sourceInfoMap, activeFeaturesSet);

        // 4. 추가 제외 경로 병합 (Root 기본 + 프로젝트 개별)
        Set<String> mergedExcluded = new java.util.LinkedHashSet<>();
        Object baseExcluded = project.getRootProject().findProperty("defaultExcluded");
        if (baseExcluded instanceof Collection) {
            ((Collection<?>) baseExcluded).forEach(e -> {
                if (e != null)
                    mergedExcluded.add(String.valueOf(e));
            });
        }
        Object subExcluded = project.findProperty("excludedSourcePaths");
        if (subExcluded instanceof Collection) {
            ((Collection<?>) subExcluded).forEach(e -> {
                if (e != null)
                    mergedExcluded.add(String.valueOf(e));
            });
        }
        mergedExcluded.addAll(subDynamicExcluded);

        // 5. 핵심 패키징 로직 실행
        configurePackaging(project, extraFiles, mergedExcluded);
    }

    /**
     * JAR 및 배포 패키지 통합 설정
     * ⭐ 소비자 프로젝트에서 'com.gradleup.shadow' 플러그인이 적용된 경우,
     * 자동으로 Shadow JAR 를 생성하도록 구성된다. → implementation, runtimeOnly 의존성은 relocate 처리
     *
     * @param project    Gradle 프로젝트 객체
     * @param extraFiles 포함할 추가 파일 경로 목록
     */
    public static void configurePackaging(Project project, Set<String> extraFiles) {
        configurePackaging(project, extraFiles, null);
    }

    /**
     * JAR 및 배포 패키지 통합 설정
     * ⭐ 소비자 프로젝트에서 'com.gradleup.shadow' 플러그인이 적용된 경우,
     * 자동으로 Shadow JAR 를 생성하도록 구성된다. → implementation, runtimeOnly 의존성은 relocate 처리
     *
     * @param project             Gradle 프로젝트 객체
     * @param extraFiles          포함할 추가 파일 경로 목록 (예: 라이선스 파일 등)
     * @param excludedSourcePaths 제외할 소스 경로 목록 (compileJava, javadoc 등에 적용)
     */
    public static void configurePackaging(Project project, Set<String> extraFiles, Set<String> excludedSourcePaths) {
        if (extraFiles != null && !extraFiles.isEmpty()) {
            project.getLogger().lifecycle("🔍 [Packaging Debug] " + project.getName() + " extraFiles: " + extraFiles);
        } else {
            project.getLogger().lifecycle("⚠️ [Packaging Debug] " + project.getName() + " extraFiles is empty or null");
        }

        // 0. 소스 및 Javadoc 설정 통합 처리
        applySourceSettings(project, excludedSourcePaths);

        // ========================================================================
        // 1. Shadow 플러그인 사용 여부 확인 및 적용
        // ========================================================================
        boolean useShadow = notifyShadowPluginStatus(project);

        final String archiveBaseName = getArchiveBaseName(project);
        final String version = project.getVersion().toString();

        /*
         * ========================================================================
         * 2. Standard JAR 태스크 등록 (배포 전용)
         * ========================================================================
         * [목적]
         * - Maven 배포 시 사용할 Standard JAR (의존성 분리) 태스크를 등록
         * - publishing 블록에서 이 태스크를 참조하므로 가장 먼저 등록해야 함
         * - registerStandardJarTask 헬퍼 메서드 재사용
         */
        registerStandardJarTask(project, archiveBaseName, version, extraFiles);

        // configurePublications is now called within afterEvaluate to ensure all plugins are loaded.

        /*
         * ========================================================================
         * 4. 빌드/배포 모드 및 태스크 분석
         * ========================================================================
         */
        List<String> taskNames = project.getGradle().getStartParameter().getTaskNames();
        // 'publish'가 포함된 태스크(publishing)인지 확인 (단순 메타데이터 생성 제외)
        boolean isAnyPublish = taskNames.stream().anyMatch(name -> {
            String lowerName = name.toLowerCase();
            return lowerName.contains("publish") && !lowerName.contains("metadata");
        });

        // 'build', 'assemble', 'shadow' 등 빌드 태스크 확인
        boolean isBuildTask = taskNames.stream().anyMatch(name -> {
            String lowerName = name.toLowerCase();
            return lowerName.contains("build") || lowerName.contains("assemble") || lowerName.contains("shadow");
        });

        /*
         * ========================================================================
         * 5. 패키징 모드별 설정 (Shadow vs Standard)
         * ========================================================================
         */

        // extraFiles 초기화 (불변 방지를 위해 복사)
        final Set<String> initialExtraFiles = (extraFiles != null) ? new LinkedHashSet<>(extraFiles) : new LinkedHashSet<>();

        // Shadow 기능 활성화 여부 판단 (Publishing 모드에서의 조건부 활성화)
        // Publishing: Shadow 플러그인 + shadedPackagePrefix 필수
        // Build: Shadow 플러그인만 있으면 활성화
        boolean enableShadowIntegration = false;
        if (useShadow) {
            if (isAnyPublish) {
                // 배포 모드: shadedPackagePrefix가 있어야만 Shadow 기능 사용 (값이 비어있으면 안됨)
                Object prefix = project.findProperty("shadedPackagePrefix");
                enableShadowIntegration = prefix != null && !prefix.toString().trim().isEmpty();
            } else {
                // 빌드 모드: Shadow 플러그인만 있으면 항상 사용 (Fat JAR)
                enableShadowIntegration = true;
            }
        }

        if (enableShadowIntegration) {
            // [Shadow 모드] Fat JAR 생성 및 Publish 연동
            // 타이밍 이슈 해결을 위해 내부에서 afterEvaluate를 사용하며, 이 리스너 안에서 라이선스를 재수집
            configureShadowIntegration(project, isBuildTask, isAnyPublish, archiveBaseName, version, initialExtraFiles);
        } else {
            // [Standard 모드] 기본 JAR 생성 (Fat JAR 선택적 생성)
            Set<String> combinedExtraFiles = new LinkedHashSet<>(initialExtraFiles);
            combinedExtraFiles.addAll(collectDynamicLicenses(project));

            configureStandardMode(project, isAnyPublish, combinedExtraFiles, version);
        }

        /*
         * ========================================================================
         * 6. 배포 패키지 생성 (Distributions)
         * ========================================================================
         * - 라이선스 파일 + JAR + 의존성을 포함한 ZIP 패키지 생성
         * - 'Gradle > Tasks > distribution > distZip' 실행 시 생성됨
         */
        configureDistributions(project, extraFiles);

        /*
         * ========================================================================
         * 7. 메타데이터 생성 및 스마트 배포 전략 설정
         * ========================================================================
         * [afterEvaluate 사용]
         * - Publishing 설정을 보완하고 태스크 의존성을 교정하기 위해 모든 평가가 끝난 후 실행
         */
        // 1. 메타데이터 생성 및 스마트 배포 전략 설정
        fixMetadataGeneration(project);
        MavenPublishStrategy.configureSmartPublishing(project);

        // 2. 배포 설정 (Maven Publication 등록)
        // Publishing에서 Shadow 사용 여부를 결정 (plugin 존재 && prefix 설정 존재)
        // configurePackaging 로직과 일치하도록 project 속성을 기준으로 재확인
        Object prefix = project.findProperty("shadedPackagePrefix");
        boolean hasValidPrefix = prefix != null && !prefix.toString().trim().isEmpty();
        boolean enableShadowPub = useShadow && hasValidPrefix;
        configurePublications(project, enableShadowPub, archiveBaseName);

        // 3. Shadow 사용 시 publishing 설정 (아티팩트 교체 등)
        if (enableShadowPub) {
            configurePublishingForShadow(project);
        }
    }

    /**
     * Shadow 플러그인 감지 및 상태 로깅
     *
     * @param project Gradle 프로젝트 객체
     * @return Shadow 플러그인 사용 여부
     */
    private static boolean notifyShadowPluginStatus(Project project) {
        boolean hasGradleupShadow = project.getPluginManager().hasPlugin("com.gradleup.shadow");
        boolean hasJohnrengelmanShadow = project.getPluginManager().hasPlugin("com.github.johnrengelman.shadow");
        boolean hasShadowPlugin = hasGradleupShadow || hasJohnrengelmanShadow;

        if (!hasShadowPlugin) {
            project.getLogger().lifecycle("ℹ️ [Shadow] Shadow 플러그인이 감지되지 않았습니다. 기본 JAR 패키징으로 진행합니다.");
            project.getLogger().debug("ℹ️ [Shadow] Fat JAR(Shaded)가 필요하다면 build.gradle에 'com.gradleup.shadow' 플러그인을 추가하세요.");
        } else {
            String detectedPlugin = hasGradleupShadow ? "com.gradleup.shadow" : "com.github.johnrengelman.shadow";
            project.getLogger().lifecycle("✅ [Shadow] Shadow 플러그인 감지됨 (" + detectedPlugin + ")");
        }
        return hasShadowPlugin;
    }

    /**
     * Shadow 통합 설정 (Fat JAR & Publishing)
     * <p>
     * ShadowJar 태스크를 구성하고, 빌드/배포 모드에 따라 동작을 분기합니다.
     * </p>
     */
    private static void configureShadowIntegration(Project project, boolean isBuildTask, boolean isAnyPublish,
            String archiveBaseName, String version, Set<String> extraFiles) {
        try {
            // 서브프로젝트 속성 로드 완료 후 라이선스 재수집 및 병합
            Set<String> combinedExtraFiles = new LinkedHashSet<>(extraFiles != null ? extraFiles : Collections.emptySet());
            combinedExtraFiles.addAll(collectDynamicLicenses(project));

            org.gradle.api.Task shadowTask = project.getTasks().findByName("shadowJar");
            if (shadowTask == null) {
                project.getLogger().warn("⚠️  [Shadow] shadowJar 태스크를 찾을 수 없습니다.");
                return;
            }

            Object shadowExtension = project.getExtensions().findByName("shadow");
            // shadowExtension은 null일 수도 있음 (Shadow 9.x 일부 버전 등)

            // Shadow JAR 상세 설정 분기
            if (isBuildTask && !isAnyPublish) {
                // [빌드 모드]: Fat JAR 생성 (Shaded + All Dependencies)
                configureShadowForBuild(project, shadowTask, shadowExtension, archiveBaseName, version, combinedExtraFiles);
            } else if (isAnyPublish) {
                // [배포 모드]: Standard JAR 생성, 구현체만 Shaded (pom 의존성을 위해)
                configureShadowForPublish(project, shadowTask, shadowExtension, archiveBaseName, version, combinedExtraFiles);
            }

            // 7. Shadow JAR 검증 태스크 등록 (사용자 설정 시) - 모든 모드 공통
            registerTestArtifactTask(project, shadowTask);

            // Shadow 플러그인의 startShadowScripts가 shadowJar를 사용하도록 자동 설정 보완
            if (shadowExtension != null && project.getPluginManager().hasPlugin("application")) {
                try {
                    java.lang.reflect.Method getApplicationMethod = shadowExtension.getClass().getMethod("getApplication");
                    Object applicationExtension = getApplicationMethod.invoke(shadowExtension);
                    if (applicationExtension != null) {
                        project.getLogger().debug("✅ [Shadow] application 확장 감지됨 - startShadowScripts -> shadowJar");
                    }
                } catch (NoSuchMethodException ignored) {
                    // ignore
                }
            }

            // 8. [Shadow] Outgoing Artifact 교체 (Project Dependency용)
            // 만약 이 프로젝트가 'shadedPackagePrefix'를 가지고 있다면,
            // 다른 프로젝트가 이 프로젝트를 의존성으로 참조할 때 Standard JAR 대신 Shadow JAR를 가져가도록 설정한다.
            if (project.hasProperty("shadedPackagePrefix")) {
                project.getLogger().lifecycle("🔧 [Shadow] Outgoing Artifact를 Shadow JAR로 교체합니다. (Project Dependencies용)");

                // Helper to replace artifacts
                org.gradle.api.Action<org.gradle.api.artifacts.Configuration> replaceArtifact = conf -> {
                    conf.getOutgoing().getArtifacts().clear();
                    conf.getOutgoing().artifact(shadowTask);
                };

                project.getConfigurations().named("apiElements").configure(replaceArtifact);
                project.getConfigurations().named("runtimeElements").configure(replaceArtifact);
            }

        } catch (Exception e) {
            project.getLogger().warn("⚠️  [Shadow] Shadow 플러그인 설정 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 프로젝트 속성을 기반으로 동적 라이선스 파일을 수집한다.
     * (Configuration 단계 이후에 호출되어야 함 - afterEvaluate 내부 등)
     */
    private static Set<String> collectDynamicLicenses(Project project) {
        Set<String> licenses = new LinkedHashSet<>();
        try {
            // activeFeatures와 dynamicSourceInfo는 Configuration 단계에서 설정되므로
            // afterEvaluate 시점에는 안전하게 접근 가능
            Object activeFeatures = project.findProperty("activeFeatures");
            Object dynamicSourceInfo = project.findProperty("dynamicSourceInfo");

            if (activeFeatures instanceof Collection && dynamicSourceInfo instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> sourceInfoMap = (Map<String, Map<String, Object>>) dynamicSourceInfo;
                // 2. 동적 소스 정보에서 라이선스 추출 (Private Helper 사용)
                licenses.addAll(extractLicensesFromSourceInfo(sourceInfoMap, (Collection<?>) activeFeatures));

                if (!licenses.isEmpty()) {
                    project.getLogger().lifecycle("🔍 [Dynamic License Collection] Found additional files: " + licenses);
                }
            }
        } catch (Exception e) {
            project.getLogger().warn("⚠️ [Dynamic License Collection] Failed to collect licenses: " + e.getMessage());
        }
        return licenses;
    }

    /**
     * Standard 모드 (Non-Shadow) 패키징 설정
     * <p>
     * 기본 jar 태스크를 설정하고, 필요 시 Fat JAR 기능을 활성화합니다.
     * </p>
     */
    private static void configureStandardMode(Project project, boolean isAnyPublish, Set<String> extraFiles, String version) {
        // Fat JAR 생성 여부 결정 (배포 시에는 항상 Standard JAR)
        boolean buildFatJar;
        if (project.hasProperty("buildFatJar")) {
            buildFatJar = Boolean.parseBoolean(project.findProperty("buildFatJar").toString());
        } else {
            buildFatJar = !isAnyPublish;
        }

        if (buildFatJar) {
            project.getLogger().lifecycle("🚀 [Packaging] Mode: Fat JAR (Includes all dependencies)");
        } else {
            project.getLogger().lifecycle("🚀 [Packaging] Mode: Standard JAR (Dependencies excluded for publishing)");
        }

        final boolean finalBuildFatJar = buildFatJar;

        project.getTasks().named("jar", Jar.class).configure(task -> {
            if (finalBuildFatJar) {
                project.getLogger().lifecycle("📦 Building Fat JAR (including dependencies)");
                task.from(
                        (Callable<Object>) () -> {
                            Set<String> excludes = getTransitiveDependenciesOfLocalProjects(project);
                            List<Object> sources = new ArrayList<>();
                            org.gradle.api.artifacts.Configuration config = project.getConfigurations().getByName("runtimeClasspath");
                            if (config.isCanBeResolved()) {
                                for (org.gradle.api.artifacts.ResolvedArtifact artifact : config.getResolvedConfiguration().getResolvedArtifacts()) {
                                    String id = artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName();
                                    if (!excludes.contains(id)) {
                                        File file = artifact.getFile();
                                        sources.add(file.isDirectory() ? file : project.zipTree(file));
                                    } else {
                                        project.getLogger().debug("   🚫 [FatJar] Skipping duplicated dependency: " + id);
                                    }
                                }
                            }
                            return sources;
                        }
                );
            } else {
                project.getLogger().lifecycle("📦 Building standard JAR (dependencies separate)");
            }

            // 추가 파일 포함
            includeExtraFiles(task, project, extraFiles);

            // 중복 파일 처리 전략
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

            // Manifest 설정
            applyManifest(task, project, version);
        });
    }

    /**
     * [⭐ 배포 패키지 생성: distributions 블록 ⭐]
     *
     * 목적:
     * 1. **종합적인 라이선스(LGPL 포함) 준수:** JAR 파일 외부에 README.md (고지)와 licenses 폴더 (전문)를 포함하여 배포
     * (모든 라이선스 정책 이행)
     * 2. **라이브러리 배포:** 최종 JAR 파일과 모든 런타임 의존성 JAR을 하나의 ZIP 파일로 묶어 제공
     * 3. **배포 방법:** 'Gradle > Tasks > distribution > distZip' 실행
     * 4. **배포 형태:** 'build/distributions/S2Util-version.zip' 파일이 생성
     *
     *
     * 1. 사용자가 ZIP 파일을 압축 해제합 (예: S2Util-version/ 폴더 생성)
     * 2. 압축 해제된 폴더 내의 'lib' 폴더에 있는 모든 JAR 파일 (s2-util-version.jar 포함)을
     * 사용자 프로젝트의 클래스패스(Classpath)에 추가하여 사용
     * 3. 사용자는 라이선스 준수를 위해 ZIP 파일 루트의 'README.md'와 'licenses' 폴더를 보관해야 함
     * (애플리케이션의 docs 또는 third-party-licenses 폴더)
     *
     * @param project    Gradle 프로젝트 객체
     * @param extraFiles 포함할 추가 파일 경로 목록 (예: 라이선스 파일 등)
     */
    private static void configureDistributions(Project project, Set<String> extraFiles) {
        org.gradle.api.distribution.DistributionContainer distributions = (org.gradle.api.distribution.DistributionContainer) project.getExtensions().findByName("distributions");
        if (distributions != null) {
            distributions.getByName("main").contents(contents -> {
                // 1. 추가 파일 (라이선스 등)
                if (extraFiles != null && !extraFiles.isEmpty()) {
                    contents.from(project.getRootDir(), copySpec -> {
                        copySpec.include(extraFiles);
                    });
                }

                // 2. 최종 JAR (lib 폴더)
                contents.from(project.getTasks().named("jar"), copySpec -> {
                    copySpec.into("lib");
                });

                // 3. 런타임 의존성 (lib 폴더)
                contents.from(project.getConfigurations().getByName("runtimeClasspath"), copySpec -> {
                    copySpec.into("lib");
                });

                contents.setDuplicatesStrategy(org.gradle.api.file.DuplicatesStrategy.EXCLUDE);
            });

            // distZip 중복 전략 설정
            project.getTasks().named("distZip", org.gradle.api.tasks.bundling.Zip.class).configure(task -> {
                task.setDuplicatesStrategy(org.gradle.api.file.DuplicatesStrategy.EXCLUDE);
            });
        }
    }

    /**
     * 🛡️ Gradle 메타데이터 생성 및 태스크 의존성 순서 교정
     * <p>
     * {@code maven-publish} 플러그인이 실행될 때, 배포용 아티팩트(주로 {@code standardJar})가
     * 선행되어야 함에도 불구하고 Gradle이 의존성을 자동으로 파악하지 못해 메타데이터 파일(.module)이
     * 먼저 생성되려고 시도하다가 오류가 발생하는 경우가 있습니다.
     * </p>
     * <p>
     * 이 메서드는 {@code generateMetadataFileForMavenJavaPublication} 태스크가 실행되기 전에
     * 반드시 {@code standardJar} 태스크가 완료되도록 강제하여 배포 오류를 방지합니다.
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     */
    private static void fixMetadataGeneration(Project project) {
        // Maven 배포를 위한 메타데이터 생성 태스크를 찾아 의존성을 명시적으로 설정
        try {
            project.getTasks().named("generateMetadataFileForMavenJavaPublication").configure(task -> {
                // standardJar 태스크가 존재한다면 그 결과를 보고 메타데이터를 만들도록 강제
                task.dependsOn(project.getTasks().named("standardJar"));
            });
        } catch (Exception ignored) {
            // 태스크가 없는 프로젝트(배포 설정이 없는 경우 등)에서는 조용히 무시하여 범용성 유지
        }
    }

    // ========================================================================
    // JAR 및 배포 설정 메서드
    // ========================================================================

    /**
     * Source JAR 생성 여부를 결정하는 전략 메서드
     *
     * @param project         Gradle 프로젝트 객체
     * @param isRemotePublish 원격 배포 실행 여부
     * @param safeTasks       안전한 태스크 목록 (로컬 빌드용)
     * @param repoBaseUrl     Maven 리포지토리 URL
     * @param githubToken     GitHub 토큰
     * @return Source JAR 생성 여부
     */
    public static boolean shouldEnableSourceJar(Project project, boolean isRemotePublish, Set<String> safeTasks, String repoBaseUrl, String githubToken) {
        // Source JAR 생성 여부 결정
        boolean enableSourceJar;
        if (project.hasProperty("enableSourceJar")) {
            enableSourceJar = Boolean.parseBoolean(project.findProperty("enableSourceJar").toString());
            String status = enableSourceJar ? "활성화(파라미터)" : "비활성화(파라미터)";
            project.getLogger().lifecycle("📦 [Config] 소스 JAR 생성이 " + status + "되었습니다.");
        } else if (isRemotePublish) {
            boolean isPrivate = GitHubPackagesClient.isRepoPrivate(repoBaseUrl, githubToken);
            enableSourceJar = isPrivate;
            if (isPrivate) {
                project.getLogger().lifecycle("🔒 [Config] 비공개 리포지토리 감지됨. 소스 JAR가 생성됩니다.");
            } else {
                project.getLogger().lifecycle("🌍 [Config] 공개 리포지토리 감지됨. 소스 JAR 생성을 건너뜁니다.");
            }
        } else {
            // 실행 중인 태스크 이름 가져오기
            List<String> taskNames = project.getGradle().getStartParameter().getTaskNames();
            enableSourceJar = safeTasks.stream().anyMatch(taskNames::contains);

            if (enableSourceJar) {
                project.getLogger().lifecycle("📦 [Config] 로컬 빌드 모드. 소스 JAR가 생성됩니다.");
            } else {
                project.getLogger().info("🚫 [Config] 소스 JAR 생성 조건 미충족 (Skip).");
            }
        }
        return enableSourceJar;
    }

    /**
     * Publish 전용 Standard JAR 태스크 등록
     *
     * @param project         Gradle 프로젝트 객체
     * @param archiveBaseName JAR 파일 기본 이름
     * @param version         프로젝트 버전
     * @param licensePaths    포함할 라이선스 파일 경로 목록
     */
    public static void registerStandardJarTask(Project project, String archiveBaseName, String version, Set<String> licensePaths) {
        project.getTasks().register("standardJar", Jar.class, task -> {
            task.getArchiveBaseName().set(archiveBaseName);
            task.getArchiveClassifier().set("standard"); // 기본 jar와 충돌 방지를 위해 standard 사용

            // main 소스셋의 출력을 포함
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            task.from(sourceSets.getByName("main").getOutput());

            // 라이선스 파일 포함
            if (licensePaths != null && !licensePaths.isEmpty()) {
                task.from(project.getRootDir(), spec -> {
                    spec.include(licensePaths);
                });
            }

            // Manifest 설정
            task.manifest(manifest -> {
                Map<String, String> attributes = new HashMap<>();
                attributes.put("Implementation-Title", project.getName());
                attributes.put("Implementation-Version", String.valueOf(version));
                attributes.put("Built-JDK", System.getProperty("java.version"));
                manifest.attributes(attributes);
            });
        });
    }

    // ========================================================================

    /**
     * Consumer 프로젝트의 Java 컴파일, 테스트, 실행 환경에 UTF-8 인코딩을 중앙에서 강제한다.
     *
     * <p>
     * 이 설정은 Windows 환경에서 발생하는 한글 깨짐 문제를 근본적으로 해결하며, 모든 JVM 기반 태스크에 -Dfile.encoding=UTF-8 옵션을 자동으로 주입한다.
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     */
    @SuppressWarnings("unchecked")
    public static void enforceUtf8Encoding(Project project) {
        /*
         * 컴파일 태스크: 소스 인코딩 및 컴파일러 JVM 인코딩 강제
         * - JavaCompile 태스크는 .java 파일을 컴파일하며, ForkOptions를 통해 javac 프로세스의 JVM 인코딩을 설정한다.
         */
        project.getTasks().withType(org.gradle.api.tasks.compile.JavaCompile.class).configureEach(task -> {
            // 소스 파일의 인코딩을 UTF-8로 설정한다.
            task.getOptions().setEncoding("UTF-8");

            // 컴파일러(javac)가 실행되는 JVM에 인코딩 인자를 전달하여 콘솔 깨짐을 방지한다.
            task.getOptions().getForkOptions().setJvmArgs(java.util.Arrays.asList("-Dfile.encoding=UTF-8"));
        });

        /*
         * 테스트 태스크: 테스트 런타임 인코딩 강제
         * - Test 태스크는 테스트 코드가 실행되는 JVM에 인코딩 인자를 설정한다.
         */
        project.getTasks().withType(org.gradle.api.tasks.testing.Test.class).configureEach(testTask -> {
            // 테스트 코드의 Console 출력 및 I/O 인코딩을 UTF-8로 설정한다.
            testTask.getJvmArgs().add("-Dfile.encoding=UTF-8");
        });

        /*
         * 애플리케이션 실행 태스크: 런타임 인코딩 강제 (Application Plugin 적용 시에만 동작)
         * - application 플러그인이 적용된 프로젝트의 애플리케이션 실행 태스크에 인코딩 인자를 설정한다.
         */
        if (project.getPluginManager().hasPlugin("application")) {
            try {
                // 리플렉션을 사용하여 'Run' 클래스를 동적으로 로드함으로써 application 플러그인이 없는 환경에서 컴파일 오류가 발생하는 것을 방지한다.
                Class<?> runTaskClass = Class.forName("org.gradle.api.tasks.application.Run");

                // withType의 제네릭 요구사항을 충족시키기 위해 명시적 캐스팅을 수행한다.
                Class<? extends org.gradle.api.Task> typedRunTaskClass = (Class<? extends org.gradle.api.Task>) runTaskClass;

                // Raw Type Action을 사용하여 복잡한 제네릭 타입 추론 오류를 회피한다.
                @SuppressWarnings("rawtypes")
                org.gradle.api.Action rawAction = new org.gradle.api.Action<org.gradle.api.Task>() {
                    @Override
                    public void execute(org.gradle.api.Task runTask) {
                        try {
                            // getJvmArgs() 메서드를 리플렉션으로 호출하여 JVM 인자를 설정한다.
                            java.lang.reflect.Method getJvmArgs = runTask.getClass().getMethod("getJvmArgs");

                            java.util.List<String> jvmArgs = (java.util.List<String>) getJvmArgs.invoke(runTask);

                            // 인코딩 인자가 중복되지 않도록 확인 후 추가한다.
                            if (!jvmArgs.contains("-Dfile.encoding=UTF-8")) {
                                jvmArgs.add("-Dfile.encoding=UTF-8");
                            }
                        } catch (Exception ignored) {
                            // 메소드가 없거나 접근할 수 없는 경우는 무시한다. (안전 장치)
                        }
                    }
                };

                project.getTasks().withType(typedRunTaskClass).configureEach(rawAction);

            } catch (ClassNotFoundException e) {
                // application 플러그인이 적용되었으나 Run 클래스가 비정상적으로 누락된 경우를 무시한다.
            }
        }

        /*
         * Javadoc 태스크: Javadoc 생성 인코딩 설정 (선택적)
         * - Javadoc 생성 시 문서 내 한글 깨짐을 방지한다.
         */
        project.getTasks().withType(org.gradle.api.tasks.javadoc.Javadoc.class).configureEach(javadocTask -> {
            javadocTask.getOptions().setEncoding("UTF-8");
        });
    }

    /**
     * 의존성 복사 태스크 등록 (copyDependencies)
     *
     * @param project Gradle 프로젝트 객체
     */
    public static void registerCopyDependenciesTask(Project project) {
        project.getTasks().register("copyDependencies", Copy.class, task -> {
            // Lazy configuration via provider
            task.from(project.getProviders().provider(() -> {
                List<File> filesToCopy = new ArrayList<>();
                org.gradle.api.artifacts.Configuration runtimeConfig = project.getConfigurations().findByName("runtimeClasspath");

                if (runtimeConfig != null && runtimeConfig.isCanBeResolved()) {
                    // Use resolved configuration to access artifacts and their metadata
                    for (ResolvedArtifact artifact : runtimeConfig.getResolvedConfiguration().getResolvedArtifacts()) {
                        ComponentIdentifier id = artifact.getId().getComponentIdentifier();

                        // 1. 로컬 프로젝트 제외 (s2-core 등)
                        if (id instanceof ProjectComponentIdentifier) {
                            continue;
                        }

                        // 2. Gradle 시스템 라이브러리 및 빌드 도구 제외
                        // (Gradle Plugin 프로젝트 특성상 runtimeClasspath에 Gradle API가 포함될 수 있음)
                        String group = artifact.getModuleVersion().getId().getGroup();
                        if (group != null) {
                            if (group.startsWith("org.gradle") ||
                                    group.startsWith("org.codehaus.groovy") || // Gradle bundled Groovy
                                    group.startsWith("org.jetbrains.kotlin") || // Gradle bundled Kotlin
                                    group.startsWith("ant")) { // Often bundled with Gradle
                                continue;
                            }
                        }

                        filesToCopy.add(artifact.getFile());
                    }
                }
                return filesToCopy;
            }));

            // 멀티 프로젝트에서도 루트의 dependencies 폴더로 모으기 위해 rootProject 기준 경로 사용
            task.into(project.getRootProject().getLayout().getProjectDirectory().dir("dependencies"));

            // 중복 파일 허용 (여러 모듈이 동일 라이브러리 의존 시)
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
        });
    }

    /**
     * 🛡️ Gradle 빌드 환경의 일관성(Consistency)을 검증한다.
     * - 현재 빌드를 실행하고 있는 Gradle 엔진의 버전(Runtime Version)과 프로젝트의 gradle-wrapper.properties 파일에 설정된 목표 버전(Wrapper Configured Version)을 비교한다.
     * - 두 버전이 일치하지 않을 경우, 사용자에게 경고 메시지를 출력하여 프로젝트 표준을 따르는 Wrapper 명령어('./gradlew') 사용 또는 Wrapper 버전 동기화를 유도한다.
     *
     * @param project 현재 빌드가 진행 중인 Gradle Project 객체
     */
    public static void checkGradleConsistency(Project project) {
        // 1. 실제 실행 버전 (Runtime Version) 가져오기
        String actualRuntimeVersion = project.getGradle().getGradleVersion();

        // 2. Wrapper 설정 파일에서 목표 버전 가져오기 (Configured Version)
        String configuredTargetVersion = getWrapperConfiguredVersion(project);

        // 3. 버전 비교 및 경고 출력
        if (configuredTargetVersion != null && !actualRuntimeVersion.equals(configuredTargetVersion)) {

            String separator = "==================================================================================";

            project.getLogger().warn(separator);
            project.getLogger().warn("⚠️ [S2BuildSupport] Gradle Wrapper 버전 편차 감지됨!");
            project.getLogger().warn("");
            project.getLogger().warn("  - 현재 실행 버전 (Runtime):   " + actualRuntimeVersion);
            project.getLogger().warn("  - 프로젝트 목표 버전 (Wrapper): " + configuredTargetVersion);
            project.getLogger().warn("");
            project.getLogger().warn("  ➡️ 현재 상황에 맞춰 다음 중 하나의 조치를 취해 주세요:");
            project.getLogger().warn("");

            project.getLogger().warn("  [A] 프로젝트 표준(목표 버전: " + configuredTargetVersion + ")으로 빌드하려면:");
            project.getLogger().warn("     시스템에 설치된 'gradle' 대신, './gradlew build'를 사용해 주세요.([프로젝트 루트]/gradlew)");
            project.getLogger().warn("");
            project.getLogger().warn("  [B] 현재 실행 버전(" + actualRuntimeVersion + ")으로 Wrapper 설정을 업데이트하려면 (⚠️ 신중히 결정, 시스템에 설치된 `Gradle`을 사용):");
            project.getLogger().warn("     gradle wrapper --gradle-version " + actualRuntimeVersion);
            project.getLogger().warn(separator);
        }
    }

    /**
     * 프로젝트의 'gradle-wrapper.properties' 파일을 읽어 'distributionUrl' 속성에서 설정된 Gradle 버전을 추출한다.
     *
     * @param project 현재 Gradle Project 객체
     * @return Wrapper에 설정된 Gradle 버전 문자열 (예: "9.2.1"). 파일이 없거나 파싱에 실패하면 null을 반환
     */
    private static String getWrapperConfiguredVersion(Project project) {
        // gradle/wrapper/gradle-wrapper.properties 파일 경로
        File wrapperPropertiesFile = project.getRootProject().file("gradle/wrapper/gradle-wrapper.properties");

        if (!wrapperPropertiesFile.exists()) {
            project.getLogger().warn("[S2BuildSupport] 경고: gradle-wrapper.properties 파일이 존재하지 않습니다. Wrapper 설정을 확인해 주세요.");
            return null;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(wrapperPropertiesFile)) {
            properties.load(input);
            String distributionUrl = properties.getProperty("distributionUrl");

            if (distributionUrl != null) {
                // distributionUrl에서 버전 문자열을 추출하는 정규식
                // 예: https\://.../gradle-9.2.1-bin.zip -> 9.2.1 추출
                Pattern pattern = Pattern.compile("gradle-(\\d+\\.\\d+\\.\\d+).*\\.zip");
                Matcher matcher = pattern.matcher(distributionUrl);

                if (matcher.find()) {
                    return matcher.group(1); // 첫 번째 캡처 그룹(버전) 반환
                }
            }
        } catch (IOException e) {
            project.getLogger().error("[S2BuildSupport] Wrapper 설정 파일을 읽는 중 오류 발생: " + e.getMessage());
        }
        return null;
    }

    // ========================================================================
    // Private 헬퍼 메서드 (Helper Methods)
    // ========================================================================

    /**
     * 프로젝트의 아카이브 기본 이름을 추출한다.
     * <p>
     * base.archivesName 속성이 있으면 사용하고, 없으면 프로젝트 이름을 반환한다.
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     * @return 아카이브 기본 이름
     */
    private static String getArchiveBaseName(Project project) {
        try {
            // base.archivesName 속성 사용 (동적 접미사 반영)
            return project.getExtensions().getByType(org.gradle.api.plugins.BasePluginExtension.class)
                    .getArchivesName().get();
        } catch (Exception e) {
            // 없으면 프로젝트 이름 사용
            return project.getName();
        }
    }

    /**
     * JAR 태스크에 표준 Manifest 정보를 설정한다.
     *
     * @param jarTask JAR 태스크
     * @param project Gradle 프로젝트 객체
     * @param version 버전 문자열
     */
    private static void applyManifest(Jar jarTask, Project project, String version) {
        jarTask.manifest(manifest -> {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("Implementation-Title", project.getName());
            attributes.put("Implementation-Version", version);
            attributes.put("Built-JDK", System.getProperty("java.version"));
            manifest.attributes(attributes);
        });
    }

    /**
     * JAR 태스크에 추가 파일(라이선스 등)을 포함한다.
     *
     * @param jarTask    JAR 태스크
     * @param project    Gradle 프로젝트 객체
     * @param extraFiles 포함할 파일 경로 목록
     */
    private static void includeExtraFiles(Jar jarTask, Project project, Set<String> extraFiles) {
        if (extraFiles != null && !extraFiles.isEmpty()) {
            project.getLogger().lifecycle("📋 [JAR Packaging] Including extra files into " + jarTask.getName());

            String buildDirPath = project.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath();

            for (String filePath : extraFiles) {
                // 1. 프로젝트 기준 탐색
                File file = project.file(filePath);

                if (file.exists()) {
                    String source = "projectDir";

                    // 만약 파일이 build 디렉토리 내에 생성된 것이라면 (예: 동적 NOTICE)
                    // 파일명과 폴더 구조를 유지하여 포함한다.
                    if (file.getAbsolutePath().startsWith(buildDirPath)) {
                        String fileName = file.getName();
                        if (fileName.equals("NOTICE")) {
                            jarTask.from(file, copySpec -> copySpec.into("licenses"));
                        } else {
                            jarTask.from(file);
                        }
                        continue;
                    }

                    if (file.isDirectory()) {
                        // 디렉토리인 경우 fileTree 사용
                        project.getLogger().lifecycle("   ✅ Adding directory [" + source + "]: " + filePath);
                        jarTask.from(project.fileTree(file));
                    } else if (filePath.contains("/")) {
                        // 경로가 포함된 파일 (예: licenses/LICENSE-MIT) -> 상위 디렉토리 유지
                        String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
                        project.getLogger().lifecycle("   ✅ Adding file [" + source + "]: " + filePath + " -> " + parentPath + "/");

                        File finalFile = file;
                        jarTask.from(finalFile, copySpec -> {
                            copySpec.into(parentPath);
                        });
                    } else {
                        // 루트 레벨 파일 (예: README.md) -> 루트에 저장
                        project.getLogger().lifecycle("   ✅ Adding root file [" + source + "]: " + filePath);
                        jarTask.from(file);
                    }
                } else {
                    project.getLogger().warn("   ⚠️  File not found: " + filePath + " (checked projectDir)");
                }
            }
        }
    }

    /**
     * 빌드 시 Shadow 플러그인 설정
     * - Fat JAR 생성
     * - implementation/runtimeOnly 의존성만 동적 쉐이딩
     *
     * <p>
     * <b>[Conditional Relocation]</b><br>
     * 프로젝트의 {@code ext.shadedPackagePrefix} 속성이 설정된 경우에만 패키지 재배치(Relocation)를 수행합니다.<br>
     * 설정 예시 (build.gradle): {@code ext { shadedPackagePrefix = "kr.devers2.s2util.shaded" }}
     * </p>
     *
     * @param project         Gradle 프로젝트 객체
     * @param shadowTask      Shadow JAR 태스크
     * @param shadowExtension Shadow 확장 객체
     * @param archiveBaseName 아카이브 기본 이름
     * @param version         버전
     * @param extraFiles      추가 파일 목록
     */
    private static void configureShadowForBuild(Project project, org.gradle.api.Task shadowTask,
            Object shadowExtension, String archiveBaseName, String version, Set<String> extraFiles) {
        try {
            project.getLogger().lifecycle("🔧 [Shadow] 빌드 모드: Fat JAR 생성, implementation/runtimeOnly 동적 쉐이딩");

            if (!(shadowTask instanceof com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar)) {
                project.getLogger().warn("⚠️ [Shadow] 태스크가 ShadowJar 타입이 아닙니다. 설정이 무시될 수 있습니다.");
                return;
            }
            com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar shadowJar = (com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) shadowTask;

            // 로컬 프로젝트의 전이 의존성 제외 처리 (중복 방지)
            excludeTransitiveDependenciesOfLocalProjects(project, shadowJar); // 0. 아티팩트 충돌 방지 및 실행 순서 제어
            // jar 태스크의 출력 경로를 분리하여 shadowJar와 파일명이 겹치지 않게 한다. (Classifier 대신 폴더 분리)
            // 단, Gradle Plugin 프로젝트의 경우 메타데이터 유효성 검사 오류 방지를 위해 분리를 피한다.
            if (!project.getPluginManager().hasPlugin("java-gradle-plugin")) {
                project.getTasks().named("jar", org.gradle.api.tasks.bundling.Jar.class, jar -> {
                    jar.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs/original"));
                });
            }

            // shadowJar 태스크 설정: 메인 아티팩트 이름 사용
            shadowJar.getArchiveBaseName().set(archiveBaseName);
            // Gradle Plugin 프로젝트의 경우 메타데이터 충돌 방지를 위해 -shaded classifier 사용
            if (project.getPluginManager().hasPlugin("java-gradle-plugin")) {
                shadowJar.getArchiveClassifier().set("shaded");
            } else {
                shadowJar.getArchiveClassifier().set("");
            }
            shadowJar.mustRunAfter(project.getTasks().named("jar"));

            // assemble이 shadowJar에 의존하도록 보장
            project.getTasks().named("assemble").configure(assemble -> assemble.dependsOn(shadowTask));

            try {
                // 0. startShadowScripts 태스크 의존성 해결 (Lazy Configuration)
                project.getTasks().configureEach(task -> {
                    if ("startShadowScripts".equals(task.getName())) {
                        task.dependsOn("jar");
                    }
                });

                // 1. 기본 설정 (Configurations) - FatJar 생성
                org.gradle.api.artifacts.Configuration runtimeClasspath = project.getConfigurations().findByName("runtimeClasspath");
                if (runtimeClasspath != null) {
                    // setConfigurations 대신 getConfigurations().add() 사용
                    // ShadowJar의 configurations는 List<FileCollection> 타입임
                    shadowJar.getConfigurations().add(runtimeClasspath);
                    project.getLogger().lifecycle("✅ [Shadow] Configurations 설정을 통한 FatJar 모드 활성화");
                }

                // 2. 추가 파일 (licenses, META-INF, readme.md) 포함
                if (extraFiles != null && !extraFiles.isEmpty()) {
                    project.getLogger().lifecycle("📋 [Shadow JAR] Including extra files into " + shadowJar.getName());

                    for (String filePath : extraFiles) {
                        // 1. 프로젝트 기준 탐색
                        File file = project.file(filePath);

                        if (file.exists()) {
                            String source = "projectDir";

                            if (file.isDirectory()) {
                                // 디렉토리인 경우 fileTree 사용
                                project.getLogger().lifecycle("   ✅ Adding directory [" + source + "]: " + filePath);
                                shadowJar.from(project.fileTree(file));
                            } else if (filePath.contains("/")) {
                                // 경로가 포함된 파일 (예: licenses/LICENSE-MIT) -> 상위 디렉토리 유지
                                String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
                                project.getLogger().lifecycle("   ✅ Adding file [" + source + "]: " + filePath + " -> " + parentPath + "/");

                                File finalFile = file;
                                shadowJar.from(finalFile, copySpec -> {
                                    copySpec.into(parentPath);
                                });
                            } else {
                                // 루트 레벨 파일 (예: README.md) -> 루트에 저장
                                project.getLogger().lifecycle("   ✅ Adding root file [" + source + "]: " + filePath);
                                shadowJar.from(file);
                            }
                        } else {
                            project.getLogger().warn("   ⚠️  File not found: " + filePath + " (checked projectDir)");
                        }
                    }
                    project.getLogger().lifecycle("✅ [Shadow] 추가 파일 포함 완료 (" + extraFiles.size() + " items)");
                }

                // 4. Relocation 대상 패키지 식별 (runtimeClasspath 스캔 - api 제외)
                // 빌드 모드에서는 relocation을 하지 않음 (명시적으로 전달된 prefix만 사용)
                // 커맨드라인에서 -PshadedPackagePrefix=... 로 명시적으로 prefix를 전달한 경우에만 relocation 수행
                boolean hasExplicitPrefix = false;
                String prefix = "";

                // 커맨드라인 인자 확인
                java.util.List<String> args = project.getGradle().getStartParameter().getProjectProperties().keySet().stream()
                        .filter(key -> "shadedPackagePrefix".equals(key))
                        .map(key -> (String) project.getGradle().getStartParameter().getProjectProperties().get(key))
                        .collect(java.util.stream.Collectors.toList());

                if (!args.isEmpty()) {
                    hasExplicitPrefix = true;
                    prefix = args.get(0);
                }

                // 빌드 모드: relocation 하지 않음 (prefix가 명시적으로 전달되지 않은 경우)
                if (hasExplicitPrefix && prefix != null && !prefix.isEmpty()) {
                    Set<String> packagesToRelocate = extractPackagesToRelocate(project);

                    // Relocate 설정
                    for (String pkg : packagesToRelocate) {
                        String fromPackage = pkg;
                        String toPackage = prefix + "." + pkg;
                        shadowJar.relocate(fromPackage, toPackage);
                        project.getLogger().lifecycle("✅ [Shadow] Relocate Package: " + fromPackage + " -> " + toPackage);
                    }
                } else {
                    project.getLogger().lifecycle("ℹ️ [Shadow] 빌드 모드: Relocation을 건너뜁니다.");
                }

                // Manifest 설정
                shadowJar.manifest(manifest -> {
                    Map<String, String> attributes = new HashMap<>();
                    attributes.put("Implementation-Title", project.getName());
                    attributes.put("Implementation-Version", version);
                    attributes.put("Built-JDK", System.getProperty("java.version"));
                    manifest.attributes(attributes);
                });

                // 중복 파일 처리 전략
                shadowJar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

            } catch (Exception e) {
                project.getLogger().warn("⚠️ [Shadow] 빌드 모드 설정 중 오류: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            project.getLogger().warn("⚠️ [Shadow] 초기 설정 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shadow JAR 안정성 검증을 위한 테스크 등록
     * <p>
     * build.gradle의 ext.artifactTestClassNames 설정이 있는 경우,
     * 해당 클래스를 최종 생성된 JAR를 클래스패스로 하여 실행하는 testArtifact 태스크를 생성합니다.
     * </p>
     */
    private static void registerTestArtifactTask(Project project, org.gradle.api.Task shadowJar) {
        List<String> verifyClasses = new ArrayList<>();
        if (project.hasProperty("artifactTestClassNames")) {
            Object prop = project.findProperty("artifactTestClassNames");
            if (prop instanceof Collection) {
                for (Object o : (Collection<?>) prop) {
                    if (o != null)
                        verifyClasses.add(o.toString());
                }
            } else if (prop != null) {
                String s = prop.toString();
                if (!s.isEmpty())
                    verifyClasses.add(s);
            }
        }

        if (!verifyClasses.isEmpty()) {
            // 1. 단일 또는 다중 실행 태스크 등록
            if (verifyClasses.size() == 1) {
                registerTestArtifactJavaExecTask(project, shadowJar, "testArtifact", verifyClasses.get(0));
            } else {
                // Lifecycle task
                org.gradle.api.Task rootTask = project.getTasks().maybeCreate("testArtifact");
                rootTask.setGroup("verification");
                rootTask.setDescription("Minimize가 적용된 Shadow JAR를 기반으로 등록된 모든 검증 클래스를 실행합니다.");

                for (String cls : verifyClasses) {
                    String subTaskName = "testArtifact_" + cls.substring(cls.lastIndexOf('.') + 1);
                    registerTestArtifactJavaExecTask(project, shadowJar, subTaskName, cls);
                    rootTask.dependsOn(subTaskName);
                }
            }

            // 2. 빌드 사이클에 통합: 'check' 태스크가 'testArtifact'에 의존하게 하여 빌드 시 자동 실행
            project.getTasks().named("check").configure(check -> check.dependsOn("testArtifact"));

            // 3. 일반 test 태스크 비활성화 (검증 클래스가 지정된 경우 'testArtifact'로 검증을 일원화)
            project.getTasks().withType(org.gradle.api.tasks.testing.Test.class).configureEach(testTask -> {
                project.getLogger().info("ℹ️ [Shadow] 'artifactTestClassNames' 설정이 감지되어 일반 test 태스크를 비활성화하고 'testArtifact'로 검증을 이관합니다.");
                testTask.setEnabled(false);
            });

            project.getLogger().lifecycle("✅ [Shadow] 'testArtifact' 태스크가 빌드 사이클에 등록되었습니다. (대상: " + verifyClasses + ")");
        } else {
            // 가이드 로그 출력 (Cyan)
            project.getLogger().lifecycle(ANSI_CYAN + "📘 [Guide] 빌드 완료 후 결과물을 테스트하려면 build.gradle에 'ext.artifactTestClassNames = [\"패키지.클래스1\", \"패키지.클래스2\"]'를 설정하세요." + ANSI_RESET);
            project.getLogger().lifecycle(ANSI_CYAN + "   -> 설정 시 './gradlew testArtifact'를 통해 최종 JAR를 클래스패스로 하여 테스트를 실행할 수 있습니다." + ANSI_RESET);
        }
    }

    /**
     * JavaExec 기반의 검증 태스크를 내부적으로 등록한다.
     */
    private static void registerTestArtifactJavaExecTask(Project project, org.gradle.api.Task shadowJar, String taskName, String targetClass) {
        project.getTasks().register(taskName, JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Minimize가 적용된 Shadow JAR를 기반으로 [" + targetClass + "]를 실행하여 안정성을 검증합니다.");
            task.dependsOn(shadowJar);

            // 1. Shadow JAR를 클래스패스 최우선순위로 설정
            task.setClasspath(project.files(shadowJar.getOutputs().getFiles()));

            // 2. 테스트 환경 구동을 위해 필요한 경우 Test Runtime Classpath 추가
            try {
                SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                SourceSet testSourceSet = sourceSets.getByName("test");
                task.setClasspath(task.getClasspath().plus(testSourceSet.getRuntimeClasspath()));
            } catch (Exception ignored) {
                // Test SourceSet이 없는 경우 무시
            }

            // 3. S2TestLauncher를 실행하기 위해 build-support 클래스패스 추가
            try {
                java.io.File supportJar = new java.io.File(S2BuildUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                task.setClasspath(task.getClasspath().plus(project.files(supportJar)));
            } catch (Exception e) {
                project.getLogger().warn("⚠️ [Shadow] S2TestLauncher 클래스패스 추가 실패: " + e.getMessage());
            }

            // 4. 실행 MainClass를 S2TestLauncher로 설정하고 타겟 클래스를 첫 번째 인자로 전달
            task.getMainClass().set("kr.devers2.buildsupport.S2TestLauncher");
            task.setArgs(java.util.List.of(targetClass));

            // 5. 작업 시작 전 안내 로그 (Cyan 색상 적용)
            task.doFirst(t -> {
                project.getLogger().lifecycle(ANSI_CYAN + "🚀 [Verification] 'built-artifact'를 클래스패스 최우선으로 하여 런타임 검증을 수행합니다. (Target: " + targetClass + ")" + ANSI_RESET);
            });
        });
    }

    // ========================================================================
    // Publishing 설정 메서드
    // ========================================================================

    /**
     * 메이븐 배포 설정 (Maven Publication)
     * - maven-publish 플러그인이 적용된 경우 mavenJava Publication을 생성 및 설정
     * - Shadow 플러그인 유무에 따라 아티팩트 구성 분기
     *
     * @param project    Gradle 프로젝트 객체
     * @param useShadow  Shadow 플러그인 사용 여부
     * @param artifactId 아티팩트 ID (archivesName)
     */
    private static void configurePublications(Project project, boolean useShadow, String artifactId) {
        if (!project.getPluginManager().hasPlugin("maven-publish")) {
            return;
        }

        // Gradle Plugin 프로젝트의 경우 java-gradle-plugin이 이미 Publication을 생성하므로 중복 생성을 피한다.
        if (project.getPluginManager().hasPlugin("java-gradle-plugin")) {
            project.getLogger().lifecycle("ℹ️ [Publishing] Gradle Plugin 프로젝트 감지됨. 별도의 mavenJava Publication 생성을 건너뜁니다.");
            return;
        }

        project.getExtensions().configure("publishing", (org.gradle.api.publish.PublishingExtension publishing) -> {
            publishing.getPublications().create("mavenJava", org.gradle.api.publish.maven.MavenPublication.class, publication -> {
                publication.setArtifactId(artifactId);

                if (!useShadow) {
                    // 1. Shadow 미사용: Standard 모드
                    // - from components.java (의존성 정보 자동 포함)
                    // - java 컴포넌트가 이미 jar, javadoc, sources(조건부)를 포함하므로 추가 조작 불필요
                    try {
                        publication.from(project.getComponents().getByName("java"));
                    } catch (Exception e) {
                        project.getLogger().warn("⚠️ [Publishing] components.java를 찾을 수 없습니다.");
                    }
                } else {
                    // 2. Shadow 사용: Shadow 모드
                    // - 여기서는 빈 Publication만 생성하고 artifactId만 설정
                    // - 실제 아티팩트 및 POM 설정은 configureShadowForPublish (afterEvaluate)에서 처리
                    // - components.java를 사용하지 않음 (Shadow와 충돌)
                }
            });
        });
    }

    /**
     * 배포 시 Shadow 플러그인 설정
     * - 표준 JAR 생성
     * - implementation/runtimeOnly 의존성만 동적 쉐이딩 후 jar에 소스 포함
     * - api 의존성은 pom에만 추가
     *
     * <p>
     * <b>[Conditional Relocation]</b><br>
     * 프로젝트의 {@code ext.shadedPackagePrefix} 속성이 설정된 경우에만 패키지 재배치(Relocation)를 수행합니다.<br>
     * 설정 예시 (build.gradle): {@code ext { shadedPackagePrefix = "kr.devers2.s2util.shaded" }}
     * </p>
     *
     * @param project         Gradle 프로젝트 객체
     * @param shadowTask      Shadow JAR 태스크
     * @param shadowExtension Shadow 확장 객체
     * @param archiveBaseName 아카이브 기본 이름
     * @param version         버전
     * @param extraFiles      추가 파일 목록
     */
    private static void configureShadowForPublish(Project project, org.gradle.api.Task shadowTask,
            Object shadowExtension, String archiveBaseName, String version, Set<String> extraFiles) {
        try {
            project.getLogger().lifecycle("🔧 [Shadow] 배포 모드: 표준 JAR 생성, implementation/runtimeOnly 동적 쉐이딩");

            if (!(shadowTask instanceof com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar)) {
                return;
            }
            com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar shadowJar = (com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) shadowTask;

            // 로컬 프로젝트의 전이 의존성 제외 처리 (중복 방지)
            excludeTransitiveDependenciesOfLocalProjects(project, shadowJar); // 0. 아티팩트 충돌 방지 및 실행 순서 제어

            // 배포 모드: runtimeClasspath를 포함하지 않음 (Standard JAR는 의존성을 포함하지 않음)
            // Shadow JAR의 configurations() 메서드를 호출하여 runtimeClasspath 제거
            try {
                // ShadowJar의 configurations() 메서드로 configurations 리스트 제거
                // Shadow 9에서는 getConfigurations()가 List<FileCollection>을 반환
                java.lang.reflect.Method getConfigsMethod = shadowJar.getClass().getMethod("getConfigurations");
                if (getConfigsMethod != null) {
                    Object configsObj = getConfigsMethod.invoke(shadowJar);
                    if (configsObj instanceof java.util.List) {
                        java.util.List<?> configs = (java.util.List<?>) configsObj;
                        // beforeEvaluate 이후 configurations이 설정되므로, doFirst로 연기
                        shadowJar.doFirst(new org.gradle.api.Action<org.gradle.api.Task>() {
                            @Override
                            public void execute(org.gradle.api.Task t) {
                                try {
                                    java.lang.reflect.Method getConfigsMethod2 = shadowJar.getClass().getMethod("getConfigurations");
                                    Object configsObj2 = getConfigsMethod2.invoke(shadowJar);
                                    if (configsObj2 instanceof java.util.List) {
                                        ((java.util.List<?>) configsObj2).clear();
                                        project.getLogger().lifecycle("✅ [Shadow] 배포 모드: configurations 제거 (의존성 미포함)");
                                    }
                                } catch (Exception e2) {
                                    // ignore
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // ignore - configurations 없을 수 있음
            }

            // jar 태스크의 출력 경로를 분리하여 shadowJar와 파일명이 겹치지 않게 한다. (Classifier 대신 폴더 분리)
            // 단, Gradle Plugin 프로젝트의 경우 메타데이터 유효성 검사 오류 방지를 위해 분리를 피한다.
            if (!project.getPluginManager().hasPlugin("java-gradle-plugin")) {
                project.getTasks().named("jar", org.gradle.api.tasks.bundling.Jar.class, jar -> {
                    jar.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs/original"));
                });
            }

            // Shadow JAR 기본 설정
            java.lang.reflect.Method getArchiveBaseNameMethod = shadowTask.getClass().getMethod("getArchiveBaseName");
            if (getArchiveBaseNameMethod != null) {
                Object archiveBaseNameProp = getArchiveBaseNameMethod.invoke(shadowTask);
                if (archiveBaseNameProp instanceof org.gradle.api.provider.Property) {
                    @SuppressWarnings("unchecked")
                    org.gradle.api.provider.Property<String> prop = (org.gradle.api.provider.Property<String>) archiveBaseNameProp;
                    prop.set(archiveBaseName);
                }
            }

            java.lang.reflect.Method getArchiveClassifierMethod = shadowTask.getClass().getMethod("getArchiveClassifier");
            if (getArchiveClassifierMethod != null) {
                Object archiveClassifierProp = getArchiveClassifierMethod.invoke(shadowTask);
                if (archiveClassifierProp instanceof org.gradle.api.provider.Property) {
                    @SuppressWarnings("unchecked")
                    org.gradle.api.provider.Property<String> prop = (org.gradle.api.provider.Property<String>) archiveClassifierProp;
                    // Gradle Plugin 프로젝트의 경우 메타데이터 충돌 방지를 위해 -shaded classifier 사용
                    if (project.getPluginManager().hasPlugin("java-gradle-plugin")) {
                        prop.set("shaded");
                    } else {
                        prop.set("");
                    }
                }
            }

            // Shadow 9에서는 기본적으로 main 소스셋과 runtimeClasspath가 이미 포함됨
            // 추가 파일 (licenses, META-INF, readme.md) 포함
            if (extraFiles != null && !extraFiles.isEmpty()) {
                try {
                    // Shadow 9에서는 CopySpec으로 캐스팅하여 from 메서드 호출
                    if (shadowTask instanceof org.gradle.api.file.CopySpec) {
                        org.gradle.api.file.CopySpec copySpec = (org.gradle.api.file.CopySpec) shadowTask;
                        project.getLogger().lifecycle("📋 [Shadow Publish] Including extra files into " + shadowTask.getName());

                        for (String filePath : extraFiles) {
                            // 1. 프로젝트 기준 탐색
                            File file = project.file(filePath);

                            if (file.exists()) {
                                String source = "projectDir";

                                if (file.isDirectory()) {
                                    // 디렉토리인 경우 fileTree 사용
                                    project.getLogger().lifecycle("   ✅ Adding directory [" + source + "]: " + filePath);
                                    copySpec.from(project.fileTree(file));
                                } else if (filePath.contains("/")) {
                                    // 경로가 포함된 파일 (예: licenses/LICENSE-MIT) -> 상위 디렉토리 유지
                                    String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
                                    project.getLogger().lifecycle("   ✅ Adding file [" + source + "]: " + filePath + " -> " + parentPath + "/");

                                    File finalFile = file;
                                    copySpec.from(finalFile, spec -> {
                                        if (spec instanceof org.gradle.api.file.CopySpec) {
                                            ((org.gradle.api.file.CopySpec) spec).into(parentPath);
                                        }
                                    });
                                } else {
                                    // 루트 레벨 파일 (예: README.md) -> 루트에 저장
                                    project.getLogger().lifecycle("   ✅ Adding root file [" + source + "]: " + filePath);
                                    copySpec.from(file);
                                }
                            } else {
                                project.getLogger().warn("   ⚠️  File not found: " + filePath + " (checked projectDir)");
                            }
                        }
                    } else {
                        // 리플렉션으로 from 메서드 호출
                        java.lang.reflect.Method fromMethod = shadowTask.getClass().getMethod("from", Object.class, org.gradle.api.Action.class);
                        fromMethod.invoke(shadowTask, project.getProjectDir(), (org.gradle.api.Action<org.gradle.api.file.CopySpec>) spec -> {
                            spec.include(extraFiles);
                        });
                    }
                } catch (Exception e) {
                    project.getLogger().warn("⚠️  [Shadow] 추가 파일 포함 중 오류: " + e.getMessage());
                }
            }

            // Shadow 9에서는 기본적으로 runtimeClasspath가 포함되므로
            // api 의존성은 exclude하여 shaded되지 않도록 하고, relocate로 implementation/runtimeOnly만 정밀하게 제어

            // 2. Api 의존성 Artifact 식별 및 ShadowExclude
            // Shadow Plugin의 dependencies 블록을 사용하여 API 의존성을 명확히 제외
            java.lang.reflect.Method dependenciesMethod = shadowTask.getClass().getMethod("dependencies", org.gradle.api.Action.class);
            if (dependenciesMethod != null && project.hasProperty("shadedPackagePrefix")) {
                dependenciesMethod.invoke(shadowTask, (org.gradle.api.Action<Object>) dependenciesSpec -> {
                    try {
                        java.lang.reflect.Method excludeMethodSpec = dependenciesSpec.getClass().getMethod("exclude", org.gradle.api.specs.Spec.class);

                        // api 설정 Resolve (transitive=true, JAVA_RUNTIME)
                        Set<String> apiArtifactIdsForExclude = new java.util.HashSet<>();
                        try {
                            org.gradle.api.artifacts.Configuration apiConfigForExclude = project.getConfigurations().findByName("api");
                            if (apiConfigForExclude != null) {
                                org.gradle.api.artifacts.Configuration resolvableApi = project.getConfigurations().detachedConfiguration();
                                resolvableApi.getDependencies().addAll(apiConfigForExclude.getAllDependencies());

                                resolvableApi.attributes(attrs -> {
                                    attrs.attribute(
                                            org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
                                            project.getObjects().named(org.gradle.api.attributes.Usage.class, org.gradle.api.attributes.Usage.JAVA_RUNTIME)
                                    );
                                });
                                resolvableApi.setTransitive(true);

                                for (ResolvedArtifact artifact : resolvableApi.getResolvedConfiguration().getResolvedArtifacts()) {
                                    apiArtifactIdsForExclude.add(artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName());
                                }
                            }
                        } catch (Exception e) {
                            // Fallback
                            org.gradle.api.artifacts.Configuration apiConfigFallback = project.getConfigurations().findByName("api");
                            if (apiConfigFallback != null) {
                                for (org.gradle.api.artifacts.Dependency dep : apiConfigFallback.getAllDependencies()) {
                                    if (dep.getGroup() != null && dep.getName() != null)
                                        apiArtifactIdsForExclude.add(dep.getGroup() + ":" + dep.getName());
                                }
                            }
                        }

                        // Spec을 통한 제외: dependency(Dependency) -> boolean
                        // Shadow는 내부적으로 ResolvedDependency를 사용하므로 Spec<ResolvedDependency>로 매칭
                        excludeMethodSpec.invoke(dependenciesSpec, (org.gradle.api.specs.Spec<org.gradle.api.artifacts.ResolvedDependency>) dependency -> {
                            String id = dependency.getModuleGroup() + ":" + dependency.getModuleName();
                            // API 의존성 집합에 포함되면 제외 (true 반환 시 exclude됨)
                            return apiArtifactIdsForExclude.contains(id);
                        });

                        project.getLogger().lifecycle("✅ [Shadow] API 의존성(전이 포함) " + apiArtifactIdsForExclude.size() + "개를 Shadow JAR에서 제외 설정했습니다.");

                    } catch (Exception e) {
                        project.getLogger().warn("⚠️ [Shadow] API 제외 설정(dependencies) 중 오류: " + e.getMessage());
                    }
                });
            }

            // 1. Relocation 대상 패키지 식별 (runtimeClasspath 스캔 - api 제외)
            // ext.shadedPackagePrefix 가 있고 비어있지 않을 때만 Relocate 진행
            if (project.hasProperty("shadedPackagePrefix")) {
                String prefix = project.findProperty("shadedPackagePrefix").toString();

                // 빈 문자열이면 relocation 수행 안 함
                if (prefix != null && !prefix.isEmpty()) {
                    Set<String> packagesToRelocate = extractPackagesToRelocate(project);

                    try {
                        java.lang.reflect.Method relocateMethod = shadowTask.getClass().getMethod("relocate", String.class, String.class);
                        if (relocateMethod != null) {
                            for (String pkg : packagesToRelocate) {
                                String fromPackage = pkg;
                                String toPackage = prefix + "." + pkg;
                                relocateMethod.invoke(shadowTask, fromPackage, toPackage);
                                project.getLogger().lifecycle("✅ [Shadow] Relocate Package: " + fromPackage + " -> " + toPackage);
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        project.getLogger().warn("⚠️  [Shadow] relocate 메서드를 찾을 수 없습니다: " + e.getMessage());
                    }
                } else {
                    project.getLogger().lifecycle("ℹ️ [Shadow] 'shadedPackagePrefix' 속성이 비어있어 Relocation을 건너뜁니다.");
                }
            } else {
                project.getLogger().lifecycle("ℹ️ [Shadow] 'shadedPackagePrefix' 속성이 없어 Relocation을 건너뜁니다.");
            }

            // Manifest 설정
            java.lang.reflect.Method manifestMethod = shadowTask.getClass().getMethod("manifest", org.gradle.api.Action.class);
            if (manifestMethod != null) {
                manifestMethod.invoke(shadowTask, (org.gradle.api.Action<org.gradle.api.java.archives.Manifest>) manifest -> {
                    Map<String, String> attributes = new HashMap<>();
                    attributes.put("Implementation-Title", project.getName());
                    attributes.put("Implementation-Version", version);
                    attributes.put("Built-JDK", System.getProperty("java.version"));
                    manifest.attributes(attributes);
                });
            }

            // 중복 파일 처리 전략
            java.lang.reflect.Method setDuplicatesStrategyMethod = shadowTask.getClass().getMethod("setDuplicatesStrategy", DuplicatesStrategy.class);
            if (setDuplicatesStrategyMethod != null) {
                setDuplicatesStrategyMethod.invoke(shadowTask, DuplicatesStrategy.EXCLUDE);
            }

        } catch (Exception e) {
            project.getLogger().warn("⚠️  [Shadow] 배포 모드 설정 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shadow 사용 시 publishing 설정
     * - Shadow JAR를 메인 아티팩트로 사용 (-all 없이)
     * - implementation/runtimeOnly 의존성은 pom에서 제외
     * - api 의존성은 pom에만 추가
     * - 공개 리포지토리면 소스 Jar 생성 안함
     *
     * @param project Gradle 프로젝트 객체
     */
    /**
     * Shadow 사용 시 publishing 설정
     * - Shadow JAR를 메인 아티팩트로 사용 (-all 없이)
     * - implementation/runtimeOnly 의존성은 pom에서 제외
     * - api 의존성은 pom에만 추가
     * - 공개 리포지토리면 소스 Jar 생성 안함
     *
     * @param project Gradle 프로젝트 객체
     */
    private static void configurePublishingForShadow(Project project) {
        project.getExtensions().configure(org.gradle.api.publish.PublishingExtension.class, publishing -> {
            // "mavenJava" 명칭을 가진 Publication에 대해서만 Shadow 설정을 적용한다.
            // (java-gradle-plugin 등에서 자동 생성하는 "pluginMaven" 등과의 충돌 방지)
            publishing.getPublications().withType(org.gradle.api.publish.maven.MavenPublication.class).configureEach(publication -> {
                if (!"mavenJava".equals(publication.getName())) {
                    return;
                }

                // Shadow 플러그인 사용 시 from components.java를 사용하면 Gradle Module Metadata 수정 불가 오류 발생
                // from components.java가 설정되어 있는지 확인 (기존 리플렉션 로직 유지하되 안전하게 처리)
                boolean hasFromComponents = false;
                try {
                    java.lang.reflect.Method getFromMethod = publication.getClass().getMethod("getFrom");
                    Object fromComponent = getFromMethod.invoke(publication);
                    hasFromComponents = fromComponent != null;
                } catch (Exception e) {
                    // getFrom 메서드가 없거나 오류 발생 시 무시
                }

                if (hasFromComponents) {
                    // from components.java가 설정되어 있으면 오류 메시지 출력
                    project.getLogger().error("❌ [Shadow] 오류: Shadow 플러그인 사용 시 'from components.java'를 사용할 수 없습니다.");
                    project.getLogger().error("❌ [Shadow] build.gradle의 publishing 블록에서 'from components.java'를 제거하고");
                    project.getLogger().error("❌ [Shadow] 아티팩트를 직접 추가하세요. 예:");
                    project.getLogger().error("❌ [Shadow]   mavenJava(MavenPublication) {");
                    project.getLogger().error("❌ [Shadow]       // from components.java  <- 이 줄 제거");
                    project.getLogger().error("❌ [Shadow]       artifactId = base.archivesName.get()");
                    project.getLogger().error("❌ [Shadow]       artifact(tasks.named('shadowJar'))");
                    project.getLogger().error("❌ [Shadow]       // ... 기타 아티팩트");
                    project.getLogger().error("❌ [Shadow]   }");
                    throw new IllegalStateException("Shadow 플러그인 사용 시 'from components.java'를 사용할 수 없습니다. build.gradle을 수정하세요.");
                }

                // 기존 아티팩트 제거
                publication.getArtifacts().clear();

                // Shadow JAR를 메인 아티팩트로 사용 (Lazy Configuration 사용)
                try {
                    publication.artifact(project.getTasks().named("shadowJar"));
                } catch (Exception e) {
                    project.getLogger().warn("⚠️ [Shadow] shadowJar 태스크를 찾을 수 없습니다.");
                }

                // Sources JAR 처리 (Lazy Configuration 사용)
                try {
                    org.gradle.api.tasks.TaskProvider<?> sourcesJarProvider = project.getTasks().named("sourcesJar");

                    // 원격 리포지토리 공개 여부 확인
                    boolean isRemotePublish = project.getGradle().getStartParameter().getTaskNames().stream()
                            .anyMatch(name -> name.toLowerCase().contains("publish") && !name.toLowerCase().contains("local"));

                    final boolean[] shouldAddSourceJar = { true };

                    if (isRemotePublish) {
                        try {
                            // 원격 배포 시 리포지토리 공개 여부 확인
                            publishing.getRepositories().withType(org.gradle.api.artifacts.repositories.MavenArtifactRepository.class).forEach(repo -> {
                                if (repo.getUrl() != null) {
                                    String url = repo.getUrl().toString();
                                    if (url.contains("github.com") || url.contains("maven.pkg.github.com")) {
                                        // GitHub Packages인 경우
                                        try {
                                            org.gradle.api.credentials.PasswordCredentials credentials = repo.getCredentials(org.gradle.api.credentials.PasswordCredentials.class);
                                            if (credentials != null && credentials.getPassword() != null) {
                                                boolean isPrivate = GitHubPackagesClient.isRepoPrivate(url, credentials.getPassword());
                                                if (!isPrivate) {
                                                    project.getLogger().lifecycle("🌍 [Shadow] 공개 리포지토리 감지됨. 소스 JAR 생성을 건너뜁니다.");
                                                    shouldAddSourceJar[0] = false;
                                                }
                                            }
                                        } catch (Exception e) {
                                            // PasswordCredentials가 아니거나 접근 권한이 없는 경우 무시
                                            project.getLogger().debug("⚠️ [Shadow] 리포지토리 자격 증명 확인 건너뜀 (표준 API 사용): " + e.getMessage());
                                        }
                                    }
                                }
                            });
                        } catch (Exception e) {
                            project.getLogger().warn("⚠️ [Shadow] 리포지토리 공개 여부 확인 중 오류 발생: " + e.getMessage());
                        }
                    }

                    // 공개 리포지토리(shouldAddSourceJar=false)가 아니라면 소스 JAR 추가
                    if (shouldAddSourceJar[0]) {
                        publication.artifact(sourcesJarProvider);
                    }

                } catch (org.gradle.api.UnknownTaskException e) {
                    // sourcesJar 태스크가 없으면 무시
                }

                // Javadoc JAR 추가 (Lazy Configuration 사용)
                try {
                    publication.artifact(project.getTasks().named("javadocJar"));
                } catch (org.gradle.api.UnknownTaskException e) {
                    // javadocJar 태스크가 없으면 무시
                }

                // POM 설정: api 의존성을 수동으로 추가 (Shaded 모드에서는 components.java를 못 쓰므로)
                org.gradle.api.artifacts.Configuration apiConfig = project.getConfigurations().findByName("api");
                if (apiConfig != null) {
                    publication.pom(pom -> {
                        pom.withXml(xml -> {
                            // Groovy Node 사용
                            Object rootObj = xml.asNode();
                            // groovy.util.Node 캐스팅 (Gradle 내장 Groovy 사용)
                            if (rootObj instanceof groovy.util.Node) {
                                groovy.util.Node root = (groovy.util.Node) rootObj;

                                groovy.util.Node dependenciesNode;
                                java.util.List<?> depNodes = (java.util.List<?>) root.get("dependencies");
                                if (depNodes != null && !depNodes.isEmpty()) {
                                    dependenciesNode = (groovy.util.Node) depNodes.get(0);
                                } else {
                                    dependenciesNode = root.appendNode("dependencies");
                                }

                                // 기존 하위 노드 제거 (중복 방지)
                                dependenciesNode.children().clear();

                                for (org.gradle.api.artifacts.Dependency dep : apiConfig.getAllDependencies()) {
                                    String g = dep.getGroup();
                                    String a = dep.getName();
                                    String v = dep.getVersion();

                                    // Project Dependency 처리
                                    if (dep instanceof org.gradle.api.artifacts.ProjectDependency) {
                                        try {
                                            java.lang.reflect.Method getProjectMethod = dep.getClass().getMethod("getDependencyProject");
                                            org.gradle.api.Project depProject = (org.gradle.api.Project) getProjectMethod.invoke(dep);
                                            g = depProject.getGroup().toString();
                                            a = depProject.getName();
                                            v = depProject.getVersion().toString();
                                        } catch (Exception e) {
                                            // Reflection failed or method not found
                                            project.getLogger().warn("⚠️ [Shadow Publish] Failed to resolve ProjectDependency via reflection: " + e.getMessage());
                                        }
                                    }

                                    if (g != null && a != null && !"unspecified".equals(a)) {
                                        groovy.util.Node depNode = dependenciesNode.appendNode("dependency");
                                        depNode.appendNode("groupId", g);
                                        depNode.appendNode("artifactId", a);
                                        if (v != null && !v.isEmpty() && !"unspecified".equals(v)) {
                                            depNode.appendNode("version", v);
                                        }
                                        depNode.appendNode("scope", "compile");
                                    }
                                }
                            }
                        });
                    });
                    project.getLogger().lifecycle("✅ [Shadow Publish] Generated POM includes 'api' dependencies manually.");
                }
            });
        });
    }

    /**
     * README 파일의 버전 정보를 업데이트하고, 필요한 경우 런타임 의존성 가이드를 추가한다.
     * <p>
     * 1. 버전 업데이트: "Implementation-Version" 패턴 등을 찾아 현재 프로젝트 버전으로 교체
     * 2. 의존성 가이드: 'compileOnly'로 선언된 특정 라이브러리(예: PDF 관련)가 있다면,
     * 소비자가 이를 런타임에 추가해야 함을 알리는 문구를 README에 삽입하거나 업데이트 한다.
     * </p>
     *
     * @param project Gradle 프로젝트 객체
     * @param file    대상 파일 (주로 README.md)
     */
    public static void updateReadmeWithVersionAndDependencies(Project project, File file) {
        if (!file.exists())
            return;

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String currentVersion = project.getVersion().toString();
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 1. 버전 및 날짜 업데이트 로직 (버전이 바뀔 때만 날짜 변경함)
            // 스냅샷(-SNAPSHOT) 등을 포함한 다양한 버전 형식을 지원하도록 정규식 개선
            Pattern vPattern = Pattern.compile("s2 Product Version: ([\\w\\.\\-]+) \\((\\d{4}-\\d{2}-\\d{2})\\)");
            Matcher vMatcher = vPattern.matcher(content);
            if (vMatcher.find()) {
                String existingVersion = vMatcher.group(1);
                // 버전이 기존과 다를 경우에만 전체 문구 교체함
                if (!existingVersion.equals(currentVersion)) {
                    content = content.replace(vMatcher.group(0), "s2 Product Version: " + currentVersion + " (" + today + ")");
                }
            }

            // 2. 의존성 정보 수집함
            List<String> depLines = collectDependencies(project);

            // 3. 마커 및 블록 처리함 (따옴표와 괄호 혼용 문제를 해결하기 위해 범용 패턴 사용함)
            // 아래 패턴은 [//]: # '...' 또는 [//]: # (...) 또는 [//]: # "..." 형식을 모두 찾아냄
            String startMarkerPattern = "\\[//\\]: # [\\'\\\"\\(]S2_DEPS_INFO_START[\\'\\\"\\)]";
            String endMarkerPattern = "\\[//\\]: # [\\'\\\"\\(]S2_DEPS_INFO_END[\\'\\\"\\)]";

            // 표준 마커 (업데이트 시 이 형식으로 통일함)
            String stdStartMarker = "[//]: # 'S2_DEPS_INFO_START'";
            String stdEndMarker = "[//]: # 'S2_DEPS_INFO_END'";

            StringBuilder depsBlock = new StringBuilder();
            if (!depLines.isEmpty()) {
                depsBlock.append("\n").append(stdStartMarker).append("\n\n---\n\n");
                depsBlock.append("**To use certain functionalities (e.g., S2PdfUtil), the end-user project must explicitly add the following dependencies to be available at runtime.** ");
                depsBlock.append("Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.\n\n");
                depsBlock.append("**[For Gradle Users]**\n\n```groovy\ndependencies {\n");
                depsBlock.append("    // Essential runtime dependencies for optional functionalities\n");
                for (String dl : depLines)
                    depsBlock.append(dl).append("\n");
                depsBlock.append("}\n```\n\n").append(stdEndMarker);
            }

            // 4. 기존 블록 교체 또는 추가 로직임
            Pattern fullBlockPattern = Pattern.compile("\n?" + startMarkerPattern + ".*?" + endMarkerPattern, Pattern.DOTALL);
            if (fullBlockPattern.matcher(content).find()) {
                // 기존에 어떤 형태의 마커가 있든 새 블록으로 교체함 (중복 방지 핵심)
                content = fullBlockPattern.matcher(content).replaceAll(depsBlock.toString());
            } else if (!depLines.isEmpty()) {
                // 마커가 없으면 파일 끝에 추가함
                content = content.trim() + "\n\n" + depsBlock.toString();
            }

            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            project.getLogger().warn("⚠️ [README Update] Failed to update " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * NOTICE 파일 내의 특정 섹션을 활성화된 기능(activeFeatures)에 따라 토글하여 임시 파일로 반환한다.
     * 원본 파일은 변경하지 않고, build/tmp 디렉토리에 새로운 파일을 생성한다.
     *
     * @param project    Gradle 프로젝트 객체
     * @param sourceFile 원본 NOTICE 파일
     * @return 수정된 내용을 담은 임시 NOTICE 파일 (또는 오류 시 원본 파일)
     */
    public static File updateNoticeFileWithActiveFeatures(Project project, File sourceFile) {
        if (!sourceFile.exists())
            return sourceFile;

        try {
            String content = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
            Object activeFeaturesObj = project.findProperty("activeFeatures");
            if (activeFeaturesObj == null) {
                activeFeaturesObj = project.getRootProject().findProperty("activeFeatures");
            }

            Set<String> activeFeatures = new HashSet<>();
            if (activeFeaturesObj instanceof Collection) {
                for (Object f : (Collection<?>) activeFeaturesObj) {
                    if (f != null)
                        activeFeatures.add(String.valueOf(f).trim());
                }
            }

            // 마커 패턴: [//]: # 'SECTION_START:Key' ... [//]: # 'SECTION_END:Key'
            Pattern sectionPattern = Pattern.compile(
                    "\\[//\\]: # [\\'\\\"\\(]SECTION_START:(.*?)[\\'\\\"\\)](.*?)\\[//\\]: # [\\'\\\"\\(]SECTION_END:\\1[\\'\\\"\\)]",
                    Pattern.DOTALL
            );
            Matcher matcher = sectionPattern.matcher(content);
            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;

            while (matcher.find()) {
                sb.append(content, lastEnd, matcher.start());
                String featureKey = matcher.group(1);
                String sectionContent = matcher.group(2);

                if (activeFeatures.contains(featureKey)) {
                    // 기능이 활성화된 경우 내용 유지 (마커는 제거하여 깨끗한 NOTICE 파일 생성)
                    sb.append(sectionContent.trim()).append("\n");
                } else {
                    // 기능이 비활성화된 경우 내용 제거
                }
                lastEnd = matcher.end();
            }
            sb.append(content.substring(lastEnd));

            String newContent = sb.toString();
            // 연속된 줄바꿈 정리 (최대 2개까지만 허용)
            newContent = newContent.replaceAll("(\\r?\\n){3,}", "\n\n");

            // 임시 출력 파일 설정
            File tempOutputDir = project.getLayout().getBuildDirectory().dir("tmp/licenses").get().getAsFile();
            if (!tempOutputDir.exists()) {
                tempOutputDir.mkdirs();
            }
            File tempNoticeFile = new File(tempOutputDir, "NOTICE");

            Files.write(tempNoticeFile.toPath(), newContent.getBytes(StandardCharsets.UTF_8));
            return tempNoticeFile;

        } catch (IOException e) {
            project.getLogger().warn("⚠️ [License] Failed to generate dynamic NOTICE file: " + e.getMessage());
            return sourceFile;
        }
    }

    private static List<String> collectDependencies(Project project) {
        List<String> depLines = new ArrayList<>();
        String[] targets = { "compileOnly", "compileOnlyApi", "provided" };

        for (String target : targets) {
            org.gradle.api.artifacts.Configuration config = project.getConfigurations().findByName(target);
            if (config == null)
                continue;

            for (Dependency dep : config.getDependencies()) {
                String g = dep.getGroup();
                String n = dep.getName();
                String v = dep.getVersion();
                if (n == null || "unspecified".equals(n))
                    continue;

                String notation = (g != null && v != null) ? g + ":" + n + ":" + v : (g != null ? g + ":" + n : n);
                String line = "    implementation '" + notation + "'";
                if (!depLines.contains(line))
                    depLines.add(line);
            }
        }
        return depLines;
    }

    /**
     * 로컬 프로젝트 의존성의 전이 의존성(Children) ID 집합을 반환한다.
     *
     * @param project Gradle 프로젝트 객체
     * @return 제외할 의존성 ID 집합 ("group:name")
     */
    private static Set<String> getTransitiveDependenciesOfLocalProjects(Project project) {
        Set<String> dependenciesToExclude = new java.util.HashSet<>();

        org.gradle.api.artifacts.Configuration runtimeConfig = project.getConfigurations().findByName("runtimeClasspath");
        if (runtimeConfig == null)
            return dependenciesToExclude;

        try {
            org.gradle.api.artifacts.ResolvedConfiguration resolvedConfig = runtimeConfig.getResolvedConfiguration();
            for (org.gradle.api.artifacts.ResolvedDependency dep : resolvedConfig.getFirstLevelModuleDependencies()) {
                boolean isProject = false;
                for (org.gradle.api.artifacts.ResolvedArtifact artifact : dep.getModuleArtifacts()) {
                    if (artifact.getId().getComponentIdentifier() instanceof org.gradle.api.artifacts.component.ProjectComponentIdentifier) {
                        isProject = true;
                        break;
                    }
                }

                if (isProject) {
                    for (org.gradle.api.artifacts.ResolvedDependency transitive : dep.getChildren()) {
                        dependenciesToExclude.add(transitive.getModuleGroup() + ":" + transitive.getModuleName());
                    }
                }
            }
        } catch (Exception e) {
            project.getLogger().warn("⚠️ [S2BuildUtils] Failed to resolve dependencies for exclusion calculation: " + e.getMessage());
        }
        return dependenciesToExclude;
    }

    /**
     * 로컬 프로젝트 의존성(ProjectDependency)의 전이 의존성을 Shadow JAR에서 제외한다.
     */
    private static void excludeTransitiveDependenciesOfLocalProjects(Project project, com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar shadowJar) {
        Set<String> dependenciesToExclude = getTransitiveDependenciesOfLocalProjects(project);
        if (!dependenciesToExclude.isEmpty()) {
            shadowJar.dependencies(dependenciesSpec -> {
                dependenciesToExclude.forEach(depId -> {
                    dependenciesSpec.exclude(dependenciesSpec.dependency(depId));
                });
            });
            project.getLogger().lifecycle("      🚫 [Shadow] Excluded " + dependenciesToExclude.size() + " duplicated transitive dependencies.");
            dependenciesToExclude.forEach(depId -> project.getLogger().debug("         - Exclude: " + depId));
        }
    }

    /**
     * Relocation 대상이 되는 패키지 목록을 추출한다.
     * (getRelocatableArtifactIDs를 통해 대상 아티팩트를 식별 후 JAR 스캔)
     */
    private static Set<String> extractPackagesToRelocate(Project project) {
        Set<String> packagesToRelocate = new java.util.HashSet<>();
        Set<String> targetArtifactIds = getRelocatableArtifactIDs(project);

        project.getLogger().lifecycle("🔍 [Shadow] Relocation 대상 패키지 스캔 시작...");

        org.gradle.api.artifacts.Configuration runtimeConfig = project.getConfigurations().findByName("runtimeClasspath");
        if (runtimeConfig != null && runtimeConfig.isCanBeResolved()) {
            try {
                Set<ResolvedArtifact> artifacts = runtimeConfig.getResolvedConfiguration().getResolvedArtifacts();
                for (ResolvedArtifact artifact : artifacts) {
                    String id = artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName();

                    // 대상 아티팩트만 스캔
                    if (!targetArtifactIds.contains(id))
                        continue;

                    File file = artifact.getFile();
                    if (file == null || !file.exists() || !file.getName().toLowerCase().endsWith(".jar"))
                        continue;

                    project.getLogger().lifecycle("📦 [Shadow] Scanning JAR: " + file.getName() + " (" + id + ")");
                    scanJarForPackages(project, file, packagesToRelocate);
                }
            } catch (Exception ignored) {
            }
        }
        return packagesToRelocate;
    }

    /**
     * Relocation 대상이 되는 아티팩트 ID(Group:Name) 목록을 추출한다.
     */
    private static Set<String> getRelocatableArtifactIDs(Project project) {
        Set<String> relocatableIds = new java.util.HashSet<>();
        Set<String> apiDependencyIds = new java.util.HashSet<>();
        try {
            org.gradle.api.artifacts.Configuration apiConfig = project.getConfigurations().findByName("api");
            if (apiConfig != null) {
                org.gradle.api.artifacts.Configuration resolvableApi = project.getConfigurations().detachedConfiguration();
                resolvableApi.getDependencies().addAll(apiConfig.getAllDependencies());
                resolvableApi.attributes(attrs -> {
                    attrs.attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, project.getObjects().named(org.gradle.api.attributes.Usage.class, org.gradle.api.attributes.Usage.JAVA_RUNTIME));
                });
                resolvableApi.setTransitive(true);
                Set<ResolvedArtifact> apiArtifacts = resolvableApi.getResolvedConfiguration().getResolvedArtifacts();
                for (ResolvedArtifact artifact : apiArtifacts) {
                    apiDependencyIds.add(artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName());
                }
            }
        } catch (Exception e) {
            org.gradle.api.artifacts.Configuration apiConfig = project.getConfigurations().findByName("api");
            if (apiConfig != null) {
                for (org.gradle.api.artifacts.Dependency dep : apiConfig.getAllDependencies()) {
                    if (dep.getGroup() != null && dep.getName() != null) {
                        apiDependencyIds.add(dep.getGroup() + ":" + dep.getName());
                    }
                }
            }
        }

        org.gradle.api.artifacts.Configuration runtimeConfig = project.getConfigurations().findByName("runtimeClasspath");
        if (runtimeConfig != null && runtimeConfig.isCanBeResolved()) {
            try {
                Set<ResolvedArtifact> artifacts = runtimeConfig.getResolvedConfiguration().getResolvedArtifacts();
                for (ResolvedArtifact artifact : artifacts) {
                    String id = artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName();
                    if (apiDependencyIds.contains(id))
                        continue;
                    if (artifact.getId().getComponentIdentifier() instanceof org.gradle.api.artifacts.component.ProjectComponentIdentifier)
                        continue;
                    relocatableIds.add(id);
                }
            } catch (Exception e) {
                project.getLogger().warn("⚠️ [Shadow] runtimeClasspath 분석 중 오류: " + e.getMessage());
            }
        }
        return relocatableIds;
    }

    private static void scanJarForPackages(Project project, File jarFile, Set<String> packages) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF") || !name.endsWith(".class"))
                    continue;
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash > 0) {
                    String path = name.substring(0, lastSlash);
                    String packageName = path.replace('/', '.');
                    String topLevel = getTopLevelPackage(packageName);
                    if (isValidPackage(topLevel))
                        packages.add(topLevel);
                }
            }
        } catch (IOException e) {
            project.getLogger().warn("⚠️ [Shadow] JAR 스캔 실패 (" + jarFile.getName() + "): " + e.getMessage());
        }
    }

    private static String getTopLevelPackage(String packageName) {
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2)
            return parts[0] + "." + parts[1];
        return packageName;
    }

    private static boolean isValidPackage(String pkg) {
        return !pkg.startsWith("java.") && !pkg.startsWith("javax.") && !pkg.startsWith("sun.") && !pkg.startsWith("jdk.") &&
                !pkg.startsWith("kr.devers2.") && !pkg.startsWith("org.w3c.") && !pkg.startsWith("org.xml.");
    }
}
