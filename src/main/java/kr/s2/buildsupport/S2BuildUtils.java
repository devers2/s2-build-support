package kr.s2.buildsupport;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

/**
 * S2 프로젝트 빌드 관련 공통 유틸리티 클래스
 *
 * <p>이 클래스는 build.gradle에서 사용하는 복잡한 빌드 로직을 Java 코드로 캡슐화하여
 * 재사용성과 유지보수성을 높이는 핵심 유틸리티입니다.</p>
 *
 * <p>주요 기능 영역:</p>
 * <ul>
 *   <li><b>경로 계산</b>: 라이선스 경로, 제외 경로 해석</li>
 *   <li><b>소스 토글</b>: 동적 기능별 소스 파일 활성화/비활성화</li>
 *   <li><b>Import 변환</b>: Java 버전별 Servlet import 자동 전환</li>
 *   <li><b>파일 업데이트</b>: 저작권 연도, README.md 버전 자동 갱신</li>
 *   <li><b>JAR 생성</b>: Classifier 생성, 파일명 생성 유틸리티</li>
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
     * 초기화 단계에서 실행되며 활성화된 기능에 따라 파일명을 변경
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
     * Java 버전에 따라 Servlet Import 구문을 업데이트
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
     * 소스 파일의 저작권 연도를 업데이트
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
     * README.md 파일의 버전 정보를 현재 버전으로 업데이트
     * - project.version이 README에 기록된 기존 버전과 다를 때만 갱신
     *
     * 기능:
     * 1. README.md 파일에서 버전 패턴(제품 버전]: X.Y [YYYY-MM-DD]:)을 찾아 현재 프로젝트 버전으로 업데이트
     * 2. 버전이 변경된 경우에만 날짜도 현재 날짜로 업데이트
     * 3. 버전이 변경되지 않은 경우 날짜 갱신을 건너뜀
     *
     * @param project    Gradle 프로젝트 객체
     * @param newVersion 새 버전 문자열
     */
    public static void updateReadmeVersion(Project project, String newVersion) {
        File readmeFile = project.file("README.md");

        if (!readmeFile.exists()) {
            System.err.println("❌ [README] README.md file not found in project root.");
            return;
        }

        try {
            java.nio.file.Path path = readmeFile.toPath();
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);

            // 정규식: "[제품 버전]: (버전) [(날짜)]:" 패턴에서 버전 부분을 캡처
            // 예시: [제품 버전]: 25.8 [2025-11-26]:
            java.util.regex.Pattern currentVersionPattern = java.util.regex.Pattern.compile("\\[제품 버전\\]:\\s+(\\S+)\\s+\\[\\d{4}-\\d{2}-\\d{2}\\]:");
            java.util.regex.Matcher matcher = currentVersionPattern.matcher(content);

            if (!matcher.find()) {
                System.err.println("⚠️  [README] Could not find the version pattern. Please ensure the format '[제품 버전]: X.Y [YYYY-MM-DD]:' exists.");
                return;
            }

            // README.md 파일에 기록된 기존 버전 추출
            String existingVersion = matcher.group(1).trim();

            if (existingVersion.equals(newVersion)) {
                // 버전이 변경되지 않음: 날짜 갱신을 건너뜀
                System.out.println("ℹ️  [README] Version is unchanged (" + newVersion + "). Skipping date update.");
                return;
            }

            // 버전이 변경됨: 버전과 날짜 모두 갱신
            String newDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String newProductVersionLine = "[제품 버전]: " + newVersion + " [" + newDate + "]:";

            String updatedContent = matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(newProductVersionLine));

            if (!content.equals(updatedContent)) {
                java.nio.file.Files.write(path, updatedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("📝 [README] Updated version: " + existingVersion + " → " + newVersion + " (Date: " + newDate + ")");
            }
        } catch (java.io.IOException e) {
            System.err.println("❌ [README] Failed to update: " + e.getMessage());
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
}
