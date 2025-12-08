package kr.s2.buildsupport;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

/**
 * S2 프로젝트 빌드 관련 공통 유틸리티 클래스
 *
 * <p>
 * 이 클래스는 build.gradle에서 사용하는 복잡한 빌드 로직을 Java 코드로 캡슐화하여
 * 재사용성과 유지보수성을 높이는 핵심 유틸리티
 * </p>
 *
 * <p>
 * 주요 기능 영역:
 * </p>
 * <ul>
 * <li><b>경로 계산</b>: 라이선스 경로, 제외 경로 해석</li>
 * <li><b>소스 토글</b>: 동적 기능별 소스 파일 활성화/비활성화</li>
 * <li><b>Import 변환</b>: Java 버전별 Servlet import 자동 전환</li>
 * <li><b>파일 업데이트</b>: 저작권 연도, README.md 버전 자동 갱신</li>
 * <li><b>JAR 생성</b>: Classifier 생성, 파일명 생성 유틸리티</li>
 * </ul>
 *
 * @see LibrariesPublisher
 * @see MavenPublishStrategy
 */
public class S2BuildUtils {

    private static final Set<String> DEFAULT_LICENSES = new LinkedHashSet<>();

    static {
        DEFAULT_LICENSES.add("README.md");
        DEFAULT_LICENSES.add("licenses/NOTICE");
        DEFAULT_LICENSES.add("licenses/LICENSE-APACHE-2.0");
        DEFAULT_LICENSES.add("licenses/LICENSE-EPL-2.0");
        DEFAULT_LICENSES.add("licenses/LICENSE-MIT");
        DEFAULT_LICENSES.add("licenses/LICENSE-MPL-2.0");
    }

    // ========================================================================
    // 경로 계산 관련 메서드
    // ========================================================================

    /**
     * 활성화된 기능에 따른 라이선스 파일 목록 반환
     *
     * @param dynamicSourceInfo 동적 소스 설정 정보 (Map<기능명, Map<설정, 값>>)
     * @param activeSources     활성화된 추가 소스 목록
     * @return 병합된 라이선스 파일 목록
     */
    public static Set<String> resolveLicensePaths(Map<String, Map<String, Object>> dynamicSourceInfo, Set<String> activeSources) {
        Set<String> licensePaths = new LinkedHashSet<>(DEFAULT_LICENSES);

        if (dynamicSourceInfo == null || activeSources == null) {
            return licensePaths;
        }

        for (String featureName : dynamicSourceInfo.keySet()) {
            if (activeSources.contains(featureName)) {
                Map<String, Object> config = dynamicSourceInfo.get(featureName);
                if (config != null) {
                    Object licensesObj = config.get("licenses");
                    if (licensesObj instanceof Collection) {
                        for (Object license : (Collection<?>) licensesObj) {
                            licensePaths.add(String.valueOf(license));
                        }
                    }
                }
            }
        }
        return licensePaths;
    }

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
    // 초기화 단계 실행 메서드 (Configuration Phase)
    // ========================================================================

    /**
     * 소스 파일 토글 수행 (.java <-> .java.txt)
     * 초기화 단계에서 실행되며 활성화된 기능에 따라 파일명 변경
     *
     * @param project           Gradle 프로젝트 객체
     * @param javaSourceRoot    Java 소스 루트 경로
     * @param dynamicSourceInfo 동적 소스 설정 정보
     * @param activeSources     활성화된 추가 소스 목록
     */
    public static void performSourceToggle(Project project, String javaSourceRoot, Map<String, Map<String, Object>> dynamicSourceInfo, Set<String> activeSources) {
        if (dynamicSourceInfo == null) {
            return;
        }

        if (!javaSourceRoot.endsWith("/")) {
            javaSourceRoot += "/";
        }

        for (String featureName : dynamicSourceInfo.keySet()) {
            boolean shouldBeIncluded = activeSources != null && activeSources.contains(featureName);
            Map<String, Object> config = dynamicSourceInfo.get(featureName);
            if (config == null)
                continue;

            Object sourcesObj = config.get("sources");
            if (sourcesObj instanceof Collection) {
                for (Object relPathObj : (Collection<?>) sourcesObj) {
                    String relativePath = String.valueOf(relPathObj);
                    String fullPathBase = javaSourceRoot + relativePath;

                    File fileJava = project.file(fullPathBase);
                    File fileTxt = project.file(fullPathBase + ".txt");

                    if (shouldBeIncluded) {
                        // 🟩 기능 활성화: .java.txt -> .java 로 복원
                        if (fileTxt.exists()) {
                            if (fileTxt.renameTo(fileJava)) {
                                System.out.println("✅ [Source Toggle] " + fileTxt.getName() + " → " + fileJava.getName() + " (Feature: " + featureName + " ENABLED)");
                            } else {
                                System.err.println("❌ [Source Toggle] Failed to rename " + fileTxt.getName() + " → " + fileJava.getName());
                            }
                        }
                    } else {
                        // 🟥 기능 비활성화: .java -> .java.txt 로 제외
                        if (fileJava.exists()) {
                            if (fileJava.renameTo(fileTxt)) {
                                System.out.println("⚠️  [Source Toggle] " + fileJava.getName() + " → " + fileTxt.getName() + " (Feature: " + featureName + " DISABLED)");
                            } else {
                                System.err.println("❌ [Source Toggle] Failed to rename " + fileJava.getName() + " → " + fileTxt.getName());
                            }
                        }
                    }
                }
            }
        }
    }

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
     * 지정된 파일의 버전 정보를 템플릿 기반으로 업데이트합니다.
     * - 예: "Version: {{=version}} ({{=release-date}})"
     * - project.version 또는 날짜가 파일에 기록된 기존 정보와 다를 때만 갱신합니다.
     *
     * <p>
     * <b>Example Usage (in build.gradle):</b>
     * </p>
     *
     * <pre>{@code
     * // 1. build.gradle에서 다음과 같이 호출
     * kr.s2.buildsupport.S2BuildUtils.updateVersionInFile(project, "README.md", "### Version: {{=version}} ({{=release-date}})", project.version.toString());
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
     * @param versionTemplate 버전 정보 템플릿. `{{=version}}`과 `{{=release-date}}` 플레이스홀더를 포함해야 합니다.
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
                System.err.println("❌ [" + filePath + "] versionTemplate must contain {{=version}} and {{=release-date}}.");
                return;
            }

            // 1. 템플릿을 기반으로 검색할 정규식을 생성합니다.
            // 플레이스홀더 순서를 기억하고, 각 부분을 정규식으로 변환합니다.
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
                if (!literals[i].isEmpty()) {
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

            // 캡처 그룹 인덱스를 동적으로 할당합니다.
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

            if (existingVersion.equals(newVersion) && existingDate.equals(newDate)) {
                System.out.println("ℹ️  [" + filePath + "] Version and date are unchanged (" + newVersion + " " + newDate + "). Skipping update.");
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
     * Java 버전과 추가 소스 목록을 기반으로 Classifier 문자열 생성
     * 예: Java 8, [S2PdfUtil] -> "java8-pdf"
     *
     * @param javaVersion       Java 버전
     * @param additionalSources 추가 소스 목록
     * @return 생성된 classifier 문자열 (없으면 빈 문자열)
     */
    public static String generateClassifier(JavaVersion javaVersion, Set<String> additionalSources) {
        List<String> parts = new ArrayList<>();

        if (javaVersion == JavaVersion.VERSION_1_8) {
            parts.add("java8");
        }

        if (additionalSources != null && additionalSources.contains("S2PdfUtil")) {
            parts.add("pdf");
        }

        // 중복 제거 및 하이픈으로 연결
        return parts.stream()
                .distinct()
                .collect(Collectors.joining("-"));
    }

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

        if (classifier != null && !classifier.isEmpty()) {
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
     * 표준 Javadoc 옵션 및 제외 경로 설정
     *
     * @param project       Gradle 프로젝트 객체
     * @param excludedPaths 제외할 소스 경로 목록
     */
    public static void configureJavadoc(Project project, Set<String> excludedPaths) {
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.setEncoding("UTF-8");

            // 모든 경고 및 오류 검사 비활성화
            options.addStringOption("Xdoclint:none", "-quiet");

            // 모든 접근 제어자 문서화
            options.addBooleanOption("private", true);
            options.setMemberLevel(JavadocMemberLevel.PROTECTED);

            // 링크 및 상속 설정
            options.setLinkSource(true);
            options.setUse(true);

            // 타이틀 설정
            // project.name 등을 활용할 수도 있지만 일관성을 위해 고정값 또는 파라미터화 고려
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

            // 오류 처리 및 제외 설정
            javadoc.setFailOnError(true);

            if (excludedPaths != null && !excludedPaths.isEmpty()) {
                javadoc.exclude(
                        fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString())
                );
            }
        });
    }

    /**
     * 컴파일 및 JAR 생성 시 소스 제외 설정 적용
     *
     * @param project       Gradle 프로젝트 객체
     * @param excludedPaths 제외할 소스 경로 목록
     */
    public static void configureSourceExclusions(Project project, Set<String> excludedPaths) {
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return;
        }

        // 컴파일 태스크
        project.getTasks().named("compileJava", org.gradle.api.tasks.compile.JavaCompile.class).configure(task -> {
            task.exclude(fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString()));
        });

        // JAR 태스크 (sourcesJar 포함)
        project.getTasks().withType(org.gradle.api.tasks.bundling.Jar.class).configureEach(task -> {
            task.exclude(fileDetails -> excludedPaths.contains(fileDetails.getRelativePath().toString()));
        });
    }

    /**
     * Distributions 플러그인 설정 (라이선스 및 라이브러리 포함)
     *
     * @param project      Gradle 프로젝트 객체
     * @param licensePaths 라이선스 파일 경로 목록
     */
    public static void configureDistributions(Project project, Set<String> licensePaths) {
        // distributions 플러그인이 적용되었는지 확인은 호출 측에서 보장하거나 try-catch
        org.gradle.api.distribution.DistributionContainer distributions = (org.gradle.api.distribution.DistributionContainer) project.getExtensions().findByName("distributions");

        if (distributions == null)
            return;

        distributions.getByName("main").contents(contents -> {
            // 1. 라이선스 파일
            if (licensePaths != null && !licensePaths.isEmpty()) {
                contents.from(project.getRootDir(), copySpec -> {
                    copySpec.include(licensePaths);
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
