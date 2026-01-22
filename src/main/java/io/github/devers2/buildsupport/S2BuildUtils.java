/**
 * S2BuildSupport Plugin
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.buildsupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
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
 * Common build utility class for S2 projects.
 * <p>
 * This utility encapsulates complex build logic used in {@code build.gradle} files
 * into reusable Java code. It manages versioning, dependency injection, packaging
 * strategies (Standard vs Shaded), and README documentation updates.
 * </p>
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * S2 프로젝트 빌드 관련 공통 유틸리티 클래스입니다.
 * <p>
 * {@code build.gradle}에서 사용하는 복잡한 빌드 로직을 Java 코드로 캡슐화하여 재사용성과 유지보수성을
 * 높이는 핵심 유틸리티입니다. 버전 관리, 동적 의존성 주입, 패키징 전략(Standard vs Shaded),
 * 그리고 README 자동 업데이트 기능을 포함합니다.
 * </p>
 *
 * <b>Packaging Strategies (패키징 전략)</b>
 *
 * <b>1. Publishing (Standard) - 'shadedPackagePrefix' 미설정 시</b>
 * <ul>
 * <li>결과물: Standard JAR (Shadow OFF)</li>
 * <li>특징: 의존성을 포함하지 않음. POM을 통해 api(compile), implementation(runtime) 전이.</li>
 * </ul>
 *
 * <b>2. Publishing (Shaded) - 'shadedPackagePrefix' 설정 시</b>
 * <ul>
 * <li>결과물: Shaded JAR (Shadow ON)</li>
 * <li>특징: implementation/runtimeOnly 의존성을 Relocate하여 JAR에 포함.</li>
 * <li>전이: api는 JAR에서 제외하고 POM에 compile 스코프로 주입. implementation은 POM에서 제거.</li>
 * </ul>
 *
 * <b>3. Build (Fat JAR) - 'shadedPackagePrefix' 미설정 시</b>
 * <ul>
 * <li>결과물: Fat JAR (Shadow ON)</li>
 * <li>특징: Relocation 없이 모든 의존성을 JAR에 포함.</li>
 * </ul>
 *
 * <b>4. Build (Relocated Fat JAR) - 'shadedPackagePrefix' 설정 시</b>
 * <ul>
 * <li>결과물: Fat JAR with Relocation (Shadow ON)</li>
 * <li>특징: 모든 의존성을 지정된 패키지로 Relocate하여 JAR에 포함.</li>
 * </ul>
 *
 * <b>🔗 의존성 전이 및 패키징 규칙 (Dependency Rules)</b>
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
 * <li>Standard and Shaded: JAR와 POM 모두에서 제외됨.</li>
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
 * <ul>
 * <li><b>Standard:</b> Normal JAR without dependencies. Dependencies are listed in the POM.</li>
 * <li><b>Shaded (Shadow):</b> Fat JAR containing internal dependencies (relocated to avoid conflicts).</li>
 * </ul>
 *
 * <b>Dependency Rules (의존성 규칙)</b>
 * <ul>
 * <li><b>api:</b> Public dependencies. Kept in POM, excluded from Shaded JAR.</li>
 * <li><b>implementation:</b> Internal dependencies. Removed from POM, relocated into Shaded JAR.</li>
 * </ul>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 */
public class S2BuildUtils {

    // ANSI Color Constants for Terminal Output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";

    /**
     * Checks if the current locale is Korean.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 현재 로케일이 한국어인지 여부를 확인합니다.
     *
     * @return true if Korean, false otherwise | 한국어인 경우 true
     */
    public static boolean isKorean() {
        return java.util.Locale.getDefault().getLanguage().equals("ko");
    }

    /**
     * Prints an info message in the appropriate language based on the locale.
     *
     * @param project   Gradle project | Gradle 프로젝트
     * @param koMessage Korean message | 한글 메시지
     * @param enMessage English message | 영문 메시지
     */
    public static void info(Project project, String koMessage, String enMessage) {
        project.getLogger().lifecycle(isKorean() ? koMessage : enMessage);
    }

    /**
     * Prints a warning message in the appropriate language based on the locale.
     *
     * @param project   Gradle project | Gradle 프로젝트
     * @param koMessage Korean message | 한글 메시지
     * @param enMessage English message | 영문 메시지
     */
    public static void warn(Project project, String koMessage, String enMessage) {
        project.getLogger().warn(ANSI_YELLOW + (isKorean() ? koMessage : enMessage) + ANSI_RESET);
    }

    /**
     * Prints an error message in the appropriate language based on the locale.
     *
     * @param project   Gradle project | Gradle 프로젝트
     * @param koMessage Korean message | 한글 메시지
     * @param enMessage English message | 영문 메시지
     */
    public static void error(Project project, String koMessage, String enMessage) {
        project.getLogger().error(ANSI_RED + (isKorean() ? koMessage : enMessage) + ANSI_RESET);
    }

    // ========================================================================
    // 프로젝트 통합 설정 메서드 (Unified Configuration)
    // ========================================================================

    /**
     * Unified configuration method that performs all project settings in the correct order.
     * <p>
     * Execution Order:
     * <ol>
     * <li><b>Dynamic Dependency Injection:</b> Adds compileOnly dependencies based on active features.</li>
     * <li><b>Source File Toggling:</b> Switches between {@code .java} and {@code .java.txt} based on feature flags.</li>
     * <li><b>Packaging Setup:</b> Configures JAR and Shadow JAR tasks.</li>
     * <li><b>Dependency Copying:</b> Registers the {@code copyDependencies} task.</li>
     * <li><b>README Update:</b> Synchronizes version and dependency documentation.</li>
     * </ol>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 프로젝트의 모든 설정을 올바른 순서로 수행하는 통합 메서드입니다.
     * <p>
     * 실행 순서:
     * <ol>
     * <li><b>동적 의존성 주입:</b> 활성화된 기능에 따라 compileOnly 의존성을 추가합니다.</li>
     * <li><b>소스 파일 토글:</b> 기능 플래그에 따라 {@code .java}와 {@code .java.txt} 파일을 전환합니다.</li>
     * <li><b>패키징 설정:</b> JAR 및 Shadow JAR 태스크를 구성합니다.</li>
     * <li><b>의존성 복사:</b> {@code copyDependencies} 태스크를 등록합니다.</li>
     * <li><b>README 업데이트:</b> 버전 및 의존성 가이드를 동기화합니다.</li>
     * </ol>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     */
    public static void configureProject(Project project) {
        // 모든 의존성 정의가 완료된 후 실행하기 위해 afterEvaluate 사용
        project.afterEvaluate(p -> {
            // 추가할 Variant ID 목록
            Set<String> variantIds = new HashSet<>();
            // 추가할 소스 목록
            Set<String> extraSources = new HashSet<>();
            // 제외할 소스 목록
            Set<String> excludedSources = new HashSet<>();
            // 추가할 의존성 목록
            Map<String, String> extraDependencyMap = new HashMap<>();
            // 추가할 라이선스 목록
            Set<String> extraLicenses = new HashSet<>();

            analyzeDynamicSourceInfo(project, variantIds, extraSources, excludedSources, extraDependencyMap, extraLicenses);

            // 1. 동적 의존성 주입 (Dynamic Dependency Injection) 및 추가 파일/variantId 정보 수집
            injectDynamicDependencies(p, extraDependencyMap);

            // 2. 추가/제외 소스 파일 토글
            performSourceToggle(p, extraSources, excludedSources);

            // 3. 패키징 및 빌드 설정 (경로 자동 계산 포함)
            // ext.skipPackaging = true인 프로젝트는 패키징 스킵
            if (!Boolean.TRUE.equals(p.findProperty("skipPackaging"))) {
                configurePackaging(p, extraSources, excludedSources, extraLicenses);
            }

            // 4. README 파일 버전 & 의존성 가이드 업데이트
            updateReadmeWithVersionAndDependencies(p, p.file("README.md"));

            // 5. Central Portal 배포 설정 (Hijack Task)
            configureCentralPortalPublishing(p);
        });
    }

    /**
     * Analyzes feature flags and resolves dynamic source, dependency, and license info.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Variant ID, 추가 소스, 제외 소스 정보 등을 분석하여 동적 설정을 수립합니다.
     *
     * @param project            The Gradle project instance | Gradle 프로젝트 객체
     * @param variantIds         List of variant IDs to add | 추가할 Variant ID 목록
     * @param extraSources       List of extra sources to add | 추가할 소스 목록
     * @param excludedSources    List of sources to exclude | 제외할 소스 목록
     * @param extraDependencyMap Map of dependencies to add | 추가할 의존성 목록
     * @param extraLicenses      List of licenses to add | 추가할 라이선스 목록
     */
    @SuppressWarnings("unchecked")
    private static void analyzeDynamicSourceInfo(Project project, Set<String> variantIds, Set<String> extraSources, Set<String> excludedSources, Map<String, String> extraDependencyMap, Set<String> extraLicenses) {
        Object activeFeaturesObj = project.hasProperty("activeFeatures") ? project.property("activeFeatures") : null;
        Object dynamicSourceInfoMapObj = project.hasProperty("dynamicSourceInfoMap") ? project.property("dynamicSourceInfoMap") : null;
        Object excludedSourcesObj = project.hasProperty("excludedSources") ? project.property("excludedSources") : null;

        Set<String> activeFeatures = new HashSet<>();
        Optional.ofNullable(activeFeaturesObj)
                .filter(Collection.class::isInstance)
                .map(obj -> (Collection<?>) obj)
                .ifPresent(
                        col -> col.stream()
                                .filter(Objects::nonNull)
                                .map(String::valueOf)
                                .filter(s -> !s.isBlank())
                                .forEach(activeFeatures::add)
                );

        if (dynamicSourceInfoMapObj instanceof Map) {
            Map<String, ?> dynamicSourceInfoMap = (Map<String, ?>) dynamicSourceInfoMapObj;
            for (String key : dynamicSourceInfoMap.keySet()) {
                Object dynamicSourceInfoObj = dynamicSourceInfoMap.get(key);
                if (dynamicSourceInfoObj instanceof Map) {
                    Map<String, ?> dynamicSourceInfo = (Map<String, ?>) dynamicSourceInfoObj;
                    if (activeFeatures.contains(key)) {
                        Optional.ofNullable(dynamicSourceInfo.get("variantId"))
                                .map(String.class::cast)
                                .ifPresent(variantIds::add);

                        Optional.ofNullable(dynamicSourceInfo.get("sources"))
                                .map(val -> (Collection<String>) val)
                                .ifPresent(extraSources::addAll);

                        Optional.ofNullable(dynamicSourceInfo.get("licenses"))
                                .map(val -> (Collection<String>) val)
                                .ifPresent(extraLicenses::addAll);

                        Optional.ofNullable(dynamicSourceInfo.get("dependencies"))
                                .map(val -> (List<Map<String, String>>) val)
                                .ifPresent(
                                        dependencies -> dependencies.stream()
                                                .filter(Objects::nonNull)
                                                .forEach(dependency -> {
                                                    String group = dependency.get("group");
                                                    String name = dependency.get("name");

                                                    if (group != null && !group.isBlank() && name != null && !name.isBlank()) {
                                                        String version = dependency.get("version");
                                                        String config = dependency.getOrDefault("configuration", "implementation");

                                                        // 2. 버전 유무에 따른 notation 생성
                                                        String notation = (version != null && !version.isBlank())
                                                                ? group + ":" + name + ":" + version
                                                                : group + ":" + name;

                                                        extraDependencyMap.put(notation, config);
                                                    }
                                                })
                                );
                    } else {
                        Optional.ofNullable(dynamicSourceInfo.get("sources"))
                                .map(val -> (Collection<String>) val)
                                .ifPresent(
                                        sources -> sources.stream()
                                                .filter(Objects::nonNull)
                                                .map(String::valueOf)
                                                .filter(source -> !source.isBlank())
                                                .map(source -> source + ".txt")
                                                .forEach(excludedSources::add)
                                );
                    }
                }
            }
        }

        Optional.ofNullable(excludedSourcesObj)
                .map(val -> (Collection<String>) val)
                .ifPresent(
                        sources -> sources.stream()
                                .filter(Objects::nonNull)
                                .map(String::valueOf)
                                .filter(source -> !source.isBlank())
                                .forEach(excludedSources::add)
                );
    }

    /**
     * Implementation of dynamic dependency injection based on active features.
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * activeFeatures 및 dynamicSourceInfo 기반 의존성 주입 구현부입니다.
     *
     * @param project            The Gradle project instance | Gradle 프로젝트 객체
     * @param extraDependencyMap Map of dependencies to be added | 추가할 의존성 맵 데이터
     */
    private static void injectDynamicDependencies(Project project, Map<String, String> extraDependencyMap) {
        for (String notation : extraDependencyMap.keySet()) {
            String config = extraDependencyMap.get(notation);
            try {
                project.getDependencies().add(config, notation);
                info(project, "   ➕ 의존성 추가 [" + config + "]: " + notation, "   ➕ Adding dependency [" + config + "]: " + notation);
            } catch (Exception e) {
                warn(project, "   ⚠️ 의존성 추가 실패: " + notation + " -> " + e.getMessage(), "   ⚠️ Failed to add dependency: " + notation + " -> " + e.getMessage());
            }
        }
    }

    /**
     * Toggles source file availability between {@code .java} and {@code .java.txt}.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 소스 파일 토글을 수행합니다 (.java <-> .java.txt).
     * <p>
     * 초기화 단계에서 실행되며, 활성화된 기능 여부에 따라 실제 소스 파일로 사용할지 텍스트로 보관할지 파일명을 변경합니다.
     * </p>
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param extraSources    List of extra sources to enable | 활성화된 추가 소스 목록
     * @param excludedSources List of sources to exclude | 제외할 소스 목록
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

    /**
     * Unified configuration for JAR and distribution packaging.
     * <p>
     * If the {@code com.gradleup.shadow} plugin is applied, it automatically configures
     * Shadow JAR generation where {@code implementation} and {@code runtimeOnly}
     * dependencies are relocated.
     * </p>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * JAR 및 배포 패키지 통합 설정입니다.
     * <p>
     * 소비자 프로젝트에서 {@code com.gradleup.shadow} 플러그인이 적용된 경우, 자동으로
     * Shadow JAR를 생성하도록 구성하며 {@code implementation} 및 {@code runtimeOnly}
     * 의존성은 relocate 처리됩니다.
     * </p>
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param extraSources    Paths to be included in the artifact | 포함할 추가 파일 경로 목록
     * @param excludedSources Paths to be excluded from compilation/javadoc | 제외할 소스 경로 목록
     * @param extraLicenses   Paths to additional license files | 포함할 추가 라이선스 파일 경로 목록
     */
    public static void configurePackaging(Project project, Set<String> extraSources, Set<String> excludedSources, Set<String> extraLicenses) {
        if (extraSources != null && !extraSources.isEmpty()) {
            info(project, "🔍 [패키징 디버그] " + project.getName() + " 추가 소스: " + extraSources, "🔍 [Packaging Debug] " + project.getName() + " extraSources: " + extraSources);
        } else {
            info(project, "⚠️ [패키징 디버그] " + project.getName() + " 추가 소스가 없거나 null입니다.", "⚠️ [Packaging Debug] " + project.getName() + " extraSources is empty or null");
        }

        // 0. 소스 및 Javadoc 설정 통합 처리
        applySourceSettings(project, excludedSources);

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
        registerStandardJarTask(project, archiveBaseName, version, extraSources);

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

        final Set<String> combinedExtraFiles = Optional.ofNullable(extraSources)
                .map(HashSet::new)
                .orElseGet(HashSet::new);

        Optional.ofNullable(extraLicenses)
                .ifPresent(
                        licenses -> licenses.stream()
                                .filter(Objects::nonNull)
                                .map(String::valueOf)
                                .filter(l -> !l.isBlank())
                                .forEach(combinedExtraFiles::add)
                );

        // Shadow 기능 활성화 여부 판단 (Publishing 모드에서의 조건부 활성화)
        // Publishing: Shadow 플러그인 + shadedPackagePrefix 필수
        // Build: Shadow 플러그인만 있으면 활성화
        boolean enableShadowIntegration = false;
        if (useShadow) {
            // Shadow 기능(Fat JAR/Relocation)은 shadedPackagePrefix가 설정된 경우에만 활성화
            Object prefix = project.findProperty("shadedPackagePrefix");
            enableShadowIntegration = prefix != null && !prefix.toString().trim().isEmpty();
        }

        if (enableShadowIntegration) {
            // [Shadow 모드] Fat JAR 생성 및 Publish 연동
            // 타이밍 이슈 해결을 위해 내부에서 afterEvaluate를 사용하며, 이 리스너 안에서 라이선스를 재수집
            configureShadowIntegration(project, isBuildTask, isAnyPublish, archiveBaseName, version, combinedExtraFiles);
        } else {
            // [Standard 모드] 기본 JAR 생성 (Fat JAR 선택적 생성)
            configureStandardMode(project, isAnyPublish, version, combinedExtraFiles);
        }

        /*
         * ========================================================================
         * 6. 배포 패키지 생성 (Distributions)
         * ========================================================================
         * - 라이선스 파일 + JAR + 의존성을 포함한 ZIP 패키지 생성
         * - 'Gradle > Tasks > distribution > distZip' 실행 시 생성됨
         */
        configureDistributions(project, extraSources);

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
     * Returns a list of source file paths (.txt) for disabled features.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 비활성화된 기능의 소스 파일 경로(.txt) 목록을 반환합니다.
     *
     * @param dynamicSourceInfo Dynamic source configuration info | 동적 소스 설정 정보 (Map&lt;기능명, Map&lt;설정, 값&gt;&gt;)
     * @param activeSources     List of active extra sources | 활성화된 추가 소스 목록
     * @return List of source file paths to exclude | 제외할 소스 파일 경로 목록
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

    /**
     * Updates Servlet import statements based on Java version.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Java 버전에 따라 Servlet 관련 import 구문을 자동으로 업데이트합니다.
     * <p>
     * 1. Java 11 이상: {@code javax.servlet} -> {@code jakarta.servlet}
     * 2. Java 11 미만: {@code jakarta.servlet} -> {@code javax.servlet}
     * </p>
     *
     * @param project        The Gradle project instance | Gradle 프로젝트 객체
     * @param javaSourceRoot Java source root path | Java 소스 루트 경로
     * @param javaVersion    Target Java version | 대상 Java 버전
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

        processFiles(sourceDir, file -> {
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
                            info(project, "🔄 [서블릿 임포트] " + file.getName() + " 업데이트 완료 (" + fromPackage + " → " + toPackage + ")", "🔄 [Servlet Import] Updated " + file.getName() + " (" + fromPackage + " → " + toPackage + ")");
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
                        info(project, "🔄 [서블릿 임포트] " + file.getName() + " 업데이트 완료 (" + fromPackage + " → " + toPackage + ")", "🔄 [Servlet Import] Updated " + file.getName() + " (" + fromPackage + " → " + toPackage + ")");
                    }
                }
            } catch (java.io.IOException e) {
                error(project, "❌ [서블릿 임포트] " + file.getName() + " 업데이트 실패: " + e.getMessage(), "❌ [Servlet Import] Failed to update " + file.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Updates the copyright year in source files.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 소스 파일의 저작권 연도를 현재 연도로 업데이트합니다.
     * <p>
     * 지정된 소스 디렉토리들을 순회하며 저작권 패턴을 찾아 최신 연도로 갱신합니다.
     * </p>
     *
     * @param project     The Gradle project instance | Gradle 프로젝트 객체
     * @param sourcePaths Array of source paths to inspect | 검사할 소스 경로 배열
     */
    public static void updateCopyright(Project project, String[] sourcePaths) {
        String currentYear = String.valueOf(java.time.Year.now().getValue());
        // 패턴: (Copyright (c) | Copyright | 저작권) 2020 - [연도] devers2
        java.util.regex.Pattern copyrightPattern = java.util.regex.Pattern.compile("(Copyright(?: \\(c\\))?|저작권) 2020 ?[-–] ?(\\d{4}) devers2");

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

            processFiles(sourceDir, file -> {
                String fileName = file.getName();
                String extension = getFileExtension(fileName);

                if (!targetExtensions.contains(extension)) {
                    return;
                }

                try {
                    java.nio.file.Path path = file.toPath();
                    String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher matcher = copyrightPattern.matcher(content);

                    boolean found = false;
                    String updatedContent = content;

                    while (matcher.find()) {
                        String copyrightPrefix = matcher.group(1);
                        String oldYear = matcher.group(2);
                        if (!oldYear.equals(currentYear)) {
                            String newCopyrightString = copyrightPrefix + " 2020 - " + currentYear + " devers2";
                            updatedContent = updatedContent.replace(matcher.group(0), newCopyrightString);
                            found = true;
                        }
                    }

                    if (found && !content.equals(updatedContent)) {
                        java.nio.file.Files.write(path, updatedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        info(project, "©️  [저작권] " + fileName + " 업데이트 완료 (" + currentYear + "년으로 갱신)", "©️  [Copyright] Updated " + fileName + " (Multiple occurrences or single updated to " + currentYear + ")");
                    }
                } catch (java.io.IOException e) {
                    error(project, "❌ [저작권] " + fileName + " 처리 실패: " + e.getMessage(), "❌ [Copyright] Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }
    }

    /**
     * Updates version information in a specified file based on a template.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 지정된 파일의 버전 정보를 템플릿 기반으로 업데이트합니다.
     *
     * - 예: "Version: {{=version}} ({{=release-date}})"
     * - project.version이 파일에 기록된 기존 정보와 다를 때만 갱신한다.
     *
     * <p>
     * <b>Example Usage (in build.gradle):</b>
     * </p>
     *
     * <pre>{@code
     * // 1. build.gradle에서 다음과 같이 호출
     * io.github.devers2.buildsupport.S2BuildUtils.updateVersionInFile(project, "README.md", "### Version: {{=version}} ({{=release-date}})", project.version.toString());
     *
     * // 2. README.md 파일에 아래 내용이 있다고 가정:
     * // ### Version: 1.0.0 (2023-01-01)
     *
     * // 3. project.version = '1.1.0'으로 태스크 실행 후, README.md 내용은 아래와 같이 변경됨:
     * // ### Version: 1.1.0 (YYYY-MM-DD) // (여기서 YYYY-MM-DD는 현재 날짜)
     * }</pre>
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param filePath        Path to the file to update | 업데이트할 파일 경로
     * @param versionTemplate Version info template with placeholders | 버전 정보 템플릿 ({{=version}}, {{=release-date}} 포함 필수)
     * @param newVersion      The new version string | 적용할 새로운 버전 문자열
     */
    public static void updateVersionInFile(Project project, String filePath, String versionTemplate, String newVersion) {
        File targetFile = project.file(filePath);
        if (!targetFile.exists()) {
            error(project, "❌ [" + filePath + "] 프로젝트 루트에서 파일을 찾을 수 없습니다.", "❌ [" + filePath + "] File not found in project root.");
            return;
        }

        try {
            String versionPlaceholder = "{{=version}}";
            String datePlaceholder = "{{=release-date}}";

            if (!versionTemplate.contains(versionPlaceholder) || !versionTemplate.contains(datePlaceholder)) {
                error(project, "❌ [" + filePath + "] versionTemplate은 {{=version}} 및 {{=release-date}}를 포함해야 합니다.", "❌ [" + filePath + "] versionTemplate must contain {{=version}} and {{=release-date}}.");
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
                warn(project, "⚠️  [" + filePath + "] 템플릿에서 버전 패턴을 찾을 수 없습니다: " + versionTemplate, "⚠️  [" + filePath + "] Could not find the version pattern from template: " + versionTemplate);
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
                info(project, "ℹ️  [" + filePath + "] 버전이 변경되지 않았습니다 (" + newVersion + "). 업데이트를 건너뜁니다.", "ℹ️  [" + filePath + "] Version is unchanged (" + newVersion + "). Skipping update.");
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
                info(project, "📝 [" + filePath + "] 버전 업데이트 완료: " + existingVersion + " → " + newVersion + " (날짜: " + existingDate + " → " + newDate + ")", "📝 [" + filePath + "] Updated version: " + existingVersion + " → " + newVersion + " (Date: " + existingDate + " → " + newDate + ")");
            }

        } catch (java.io.IOException e) {
            error(project, "❌ [" + filePath + "] 업데이트 실패: " + e.getMessage(), "❌ [" + filePath + "] Failed to update: " + e.getMessage());
        }
    }

    // ========================================================================
    // JAR 관련 유틸리티 메서드
    // ========================================================================

    /**
     * Generates a JAR filename.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * JAR 파일 이름을 생성합니다.
     * <p>
     * 예: {@code s2-util-25.8-java8-pdf.jar}
     * </p>
     *
     * @param archivesName Archive base name | 아카이브 기본 이름 (예: s2-util)
     * @param version      Version | 버전 (예: 25.8)
     * @param classifier   Classifier | classifier (예: java8-pdf)
     * @return Full JAR filename | 전체 JAR 파일명
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
    private static void processFiles(File file, java.util.function.Consumer<File> fileProcessor) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    processFiles(child, fileProcessor);
                }
            }
        } else {
            fileProcessor.accept(file);
        }
    }

    /**
     * Extracts the file extension from a filename.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 파일명에서 확장자를 추출합니다.
     *
     * @param fileName Filename | 파일명
     * @return File extension (lowercase, without dot) | 확장자 (소문자, 점 제외)
     */
    private static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    // ========================================================================
    // Gradle Task 설정 헬퍼 메서드
    // ========================================================================

    /**
     * Applies build and Javadoc source settings (Internal Helper).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 빌드 및 Javadoc 관련 소스 설정을 적용합니다.
     * <p>
     * {@code compileJava}, {@code Jar}, {@code Javadoc} 태스크에 대해 소스 제외 및 표준 옵션을 설정합니다.
     * </p>
     *
     * @param project       The Gradle project instance | Gradle 프로젝트 객체
     * @param excludedPaths List of source paths to exclude | 제외할 소스 경로 목록
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
     * Detects Shadow plugin and logs its status.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Shadow 플러그인 적용 여부를 감지하고 상태를 로깅합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @return {@code true} if Shadow plugin is applied | Shadow 플러그인 사용 여부
     */
    private static boolean notifyShadowPluginStatus(Project project) {
        boolean hasGradleupShadow = project.getPluginManager().hasPlugin("com.gradleup.shadow");
        boolean hasJohnrengelmanShadow = project.getPluginManager().hasPlugin("com.github.johnrengelman.shadow");
        boolean hasShadowPlugin = hasGradleupShadow || hasJohnrengelmanShadow;

        if (!hasShadowPlugin) {
            info(project, "ℹ️ [Shadow] Shadow 플러그인이 감지되지 않았습니다. 기본 JAR 패키징으로 진행합니다.", "ℹ️ [Shadow] Shadow plugin not detected. Proceeding with standard JAR packaging.");
            info(project, "   Fat JAR(Shaded)가 필요하다면 'com.gradleup.shadow' 플러그인을 추가하세요.", "   If you need Fat JAR (Shaded), please add 'com.gradleup.shadow' plugin.");
        } else {
            String detectedPlugin = hasGradleupShadow ? "com.gradleup.shadow" : "com.github.johnrengelman.shadow";
            info(project, "✅ [Shadow] Shadow 플러그인이 감지되었습니다 (" + detectedPlugin + ")", "✅ [Shadow] Detected Shadow plugin (" + detectedPlugin + ")");
        }
        return hasShadowPlugin;
    }

    /**
     * Configures Shadow integration for Fat JAR and Publishing.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Shadow 통합 설정을 수행합니다 (Fat JAR 및 Publishing).
     *
     * @param project            The Gradle project instance | Gradle 프로젝트 객체
     * @param isBuildTask        Whether it's a build/assemble task | 빌드 태스크 여부
     * @param isAnyPublish       Whether it's a publishing task | 배포 태스크 여부
     * @param archiveBaseName    Archive base name | 아카이브 베이스 이름
     * @param version            Version string | 버전 문자열
     * @param combinedExtraFiles Files to include in the JAR | 포함할 추가 파일 세트
     */
    private static void configureShadowIntegration(Project project, boolean isBuildTask, boolean isAnyPublish, String archiveBaseName, String version, Set<String> combinedExtraFiles) {
        try {
            // 서브프로젝트 속성 로드 완료 후 라이선스 재수집 및 병합
            org.gradle.api.Task shadowTask = project.getTasks().findByName("shadowJar");
            if (shadowTask == null) {
                warn(project, "⚠️  [Shadow] shadowJar 태스크를 찾을 수 없습니다.", "⚠️  [Shadow] shadowJar task not found.");
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
     * Configures packaging for Standard mode (Non-Shadow).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Standard 모드 (Non-Shadow) 패키징 설정을 수행합니다.
     * <p>
     * 기본 {@code jar} 태스크를 설정하고, 설정에 따라 Fat JAR 기능을 선택적으로 활성화합니다.
     * </p>
     *
     * @param project            The Gradle project instance | Gradle 프로젝트 객체
     * @param isAnyPublish       Whether it's a publishing task | 배포 태스크 여부
     * @param version            Version string | 버전 문자열
     * @param combinedExtraFiles Files to include in the JAR | 포함할 추가 파일 세트
     */
    private static void configureStandardMode(Project project, boolean isAnyPublish, String version, Set<String> combinedExtraFiles) {
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
            // [Fix] Gradle 9.2.1+ Implicit Dependency Error 해결
            // runtimeClasspath의 의존성(프로젝트 포함)을 명시적으로 dependsOn에 추가하여 실행 순서를 보장합니다.
            // Fat JAR 여부와 관계없이 순서를 보장하는 것이 안전합니다.
            org.gradle.api.artifacts.Configuration runtimeConfig = project.getConfigurations().getByName("runtimeClasspath");
            task.dependsOn(runtimeConfig);

            if (finalBuildFatJar) {
                project.getLogger().lifecycle("📦 Building Fat JAR (including dependencies)");

                // Gradle의 증분 빌드를 위해 입력 파일(Inputs)로도 명시합니다.
                task.getInputs().files(runtimeConfig);

                task.from(
                        project.getConfigurations().getByName("runtimeClasspath")
                                .getIncoming()
                                .getArtifacts()
                                .getResolvedArtifacts()
                                .map(artifactResults -> {
                                    Set<String> excludes = getTransitiveDependenciesOfLocalProjects(project);
                                    List<Object> sources = new ArrayList<>();

                                    for (ResolvedArtifactResult artifact : artifactResults) {
                                        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
                                        String idStr = null;

                                        if (id instanceof ModuleComponentIdentifier) {
                                            ModuleComponentIdentifier mid = (ModuleComponentIdentifier) id;
                                            idStr = mid.getGroup() + ":" + mid.getModule();
                                        } else if (id instanceof ProjectComponentIdentifier) {
                                            // 프로젝트 의존성의 경우, 로컬 프로젝트의 전이 의존성에서 제외되지 않으므로 포함
                                            // 필요한 경우 여기서 추가 로직 수행
                                        }

                                        if (idStr == null || !excludes.contains(idStr)) {
                                            File file = artifact.getFile();
                                            sources.add(file.isDirectory() ? file : project.zipTree(file));
                                        } else {
                                            project.getLogger().debug("   🚫 [FatJar] Skipping duplicated dependency: " + idStr);
                                        }
                                    }
                                    return sources;
                                })
                );
            } else {
                project.getLogger().lifecycle("📦 Building standard JAR (dependencies separate)");
            }

            // 추가 파일 포함
            includeExtraFiles(task, project, combinedExtraFiles);

            // 중복 파일 처리 전략
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

            // Manifest 설정
            applyManifest(task, project, version);
        });
    }

    /**
     * Configures distribution packages (ZIP).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 패키지(ZIP) 생성을 위한 {@code distributions} 블록을 설정합니다.
     *
     * 목적:
     * 1. **종합적인 라이선스 준수:** JAR 파일 외부에 README.md (고지)와 licenses 폴더 (전문)를 포함하여 배포
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
     * @param project    The Gradle project instance | Gradle 프로젝트 객체
     * @param extraFiles List of extra files to include | 포함할 추가 파일 경로 목록 (예: 라이선스 파일 등)
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
     * Corrects Gradle metadata generation and task dependencies.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Gradle 메타데이터 생성 및 태스크 의존성 순서를 교정합니다.
     * <p>
     * {@code maven-publish} 플러그인 실행 시, 배포용 아티팩트가 메타데이터보다 먼저 생성되도록 강제하여 배포 오류를 방지합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
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
     * Determines whether to generate a Source JAR.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Source JAR 생성 여부를 결정합니다.
     *
     * 소스 JAR 생성 조건:
     * 1. 원격 배포 시 GitHub API로 비공개 여부 확인
     * 2. 비공개 리포지토리: 항상 생성
     * 3. 공개 리포지토리: 안전한 태스크(local build/test)에서만 생성
     * - 안전한 태스크: 로컬 빌드/테스트용 (assemble, build, jar, sourcesJar, publishToMavenLocal)
     * - 공개 리포지토리에 publish 실행 시 소스 코드 노출 방지
     * <p>
     * <b>결정 로직:</b>
     * <ol>
     * <li><b>Maven Central (OSSRH):</b> 무조건 생성 (true)</li>
     * <li><b>User Override:</b> {@code project.ext.enableSourceJar}가 이미 true이면 (true)</li>
     * <li><b>Remote Publish:</b> GitHub Packages인 경우 Private 리포지토리면 (true), Public이면 (false)</li>
     * <li><b>Local Build:</b> 안전한 태스크(assemble, build, 등) 실행 시 (true)</li>
     * </ol>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @return {@code true} if Source JAR should be generated | Source JAR 생성 여부
     */
    public static boolean determineSourceJarStatus(Project project) {
        Project rootProject = project.getRootProject();

        // 1. Maven Central (OSSRH) 배포 감지 - 최우선 순위
        // (Task 이름에 'OSSRH' 또는 'Central'이 포함되는지 확인)
        List<String> taskNames = project.getGradle().getStartParameter().getTaskNames();
        boolean isCentralPublish = taskNames.stream()
                .anyMatch(name -> name.toUpperCase().contains("OSSRH") || name.toUpperCase().contains("CENTRAL"));

        if (isCentralPublish) {
            info(project, "🌍 [설정] Maven Central 배포가 감지되었습니다. 소스 JAR 생성을 강제 활성화합니다.", "🌍 [Config] Maven Central publish detected. Forcing Source JAR generation.");
            rootProject.getExtensions().getExtraProperties().set("enableSourceJar", true);
            return true;
        }

        // 2. 기존 설정 확인 (이미 true로 설정되어 있으면 유지)
        if (rootProject.getExtensions().getExtraProperties().has("enableSourceJar")) {
            Object existingVal = rootProject.getExtensions().getExtraProperties().get("enableSourceJar");
            if (Boolean.TRUE.equals(existingVal) || "true".equalsIgnoreCase(String.valueOf(existingVal))) {
                // 이미 활성화된 상태라면 유지
                return true;
            }
        }

        // 3. Remote Publish 여부 확인
        boolean isAnyPublish = taskNames.stream().anyMatch(name -> name.toLowerCase().contains("publish"));
        boolean isLocalPublish = taskNames.stream().anyMatch(name -> name.toLowerCase().contains("mavenlocal"));
        boolean isRemotePublish = isAnyPublish && !isLocalPublish;

        boolean enableSourceJar = false;
        String reason = "";

        if (isRemotePublish) {
            // GitHub Packages 등의 원격 배포
            String repoBaseUrl = (String) rootProject.findProperty("REPO_BASE_URL");
            String githubToken = (String) rootProject.findProperty("GITHUB_TOKEN");

            if (repoBaseUrl != null && githubToken != null) {
                boolean isPrivate = GitHubPackagesClient.isRepoPrivate(repoBaseUrl, githubToken);
                if (isPrivate) {
                    enableSourceJar = true;
                    reason = "비공개 리포지토리 (Private Repository)";
                } else {
                    enableSourceJar = false;
                    reason = "공개 리포지토리 (Public Repository) - 소스 비공개";
                }
            } else {
                // 정보가 없으면 기본적으로 생성 안 함 (안전하게)
                enableSourceJar = false;
                reason = "리포지토리 정보 부족";
            }
        } else {
            // 4. 로컬 빌드/테스트
            // 사용자의 요청으로 로컬 빌드 시에는 무조건 소스 JAR 생성 활성화
            enableSourceJar = true;
            reason = "로컬 빌드 시 항상 생성 (User Request)";
        }

        // 결과 저장 및 로깅
        rootProject.getExtensions().getExtraProperties().set("enableSourceJar", enableSourceJar);

        if (enableSourceJar) {
            info(project, "📦 [설정] 소스 JAR 생성 활성화: " + reason, "📦 [Config] Source JAR generation enabled: " + reason);
        } else {
            info(project, "🚫 [설정] 소스 JAR 생성 비활성화: " + reason, "🚫 [Config] Source JAR generation disabled: " + reason);
        }

        return enableSourceJar;
    }

    /**
     * Registers a Standard JAR task for publishing.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 전용 Standard JAR 태스크를 등록합니다.
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param archiveBaseName Archive base name | JAR 파일 기본 이름
     * @param version         Project version | 프로젝트 버전
     * @param licensePaths    List of license file paths | 포함할 라이선스 파일 경로 목록
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
     * Centrally enforces UTF-8 encoding for Java compilation, testing, and execution environments.
     * <p>
     * This setting fundamentally resolves Korean character corruption issues common on
     * Windows platforms by automatically injecting {@code -Dfile.encoding=UTF-8} into all
     * JVM-based tasks.
     * </p>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 프로젝트의 Java 컴파일, 테스트, 실행 환경에 UTF-8 인코딩을 중앙에서 강제합니다.
     * <p>
     * 이 설정은 Windows 환경에서 발생하는 한글 깨짐 문제를 근본적으로 해결하며, 모든 JVM 기반 태스크에
     * {@code -Dfile.encoding=UTF-8} 옵션을 자동으로 주입합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
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
     * Registers the {@code copyDependencies} task.
     * <p>
     * This task copies all runtime dependencies into a specific directory,
     * which is useful for manual distribution or artifact analysis.
     * </p>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 의존성 복사 태스크({@code copyDependencies})를 등록합니다.
     * <p>
     * 모든 런타임 의존성을 특정 디렉토리로 복사하여 수동 배포나 아티팩트 분석에 활용할 수 있게 합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
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
     * Verifies the consistency of the Gradle build environment.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Gradle 빌드 환경의 일관성(Consistency)을 검증합니다.
     * <p>
     * 현재 실행 중인 Gradle 버전과 {@code gradle-wrapper.properties}에 설정된 목표 버전을 비교하여,
     * 일치하지 않을 경우 경고 메시지와 해결 방법을 안내합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     */
    public static void checkGradleConsistency(Project project) {
        // 1. 실제 실행 버전 (Runtime Version) 가져오기
        String actualRuntimeVersion = project.getGradle().getGradleVersion();

        // 2. Wrapper 설정 파일에서 목표 버전 가져오기 (Configured Version)
        String configuredTargetVersion = getWrapperConfiguredVersion(project);

        // 3. 버전 비교 및 경고 출력
        if (configuredTargetVersion != null && !actualRuntimeVersion.equals(configuredTargetVersion)) {

            String separator = "==================================================================================";

            if (isKorean()) {
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
            } else {
                project.getLogger().warn(separator);
                project.getLogger().warn("⚠️ [S2BuildSupport] Gradle Wrapper version divergence detected!");
                project.getLogger().warn("");
                project.getLogger().warn("  - Current Runtime Version:   " + actualRuntimeVersion);
                project.getLogger().warn("  - Project Target Version (Wrapper): " + configuredTargetVersion);
                project.getLogger().warn("");
                project.getLogger().warn("  ➡️ Please take one of the following actions based on your situation:");
                project.getLogger().warn("");
                project.getLogger().warn("  [A] To build with project standard (Target Version: " + configuredTargetVersion + "):");
                project.getLogger().warn("     Please use './gradlew build' instead of system-installed 'gradle'. ([Project Root]/gradlew)");
                project.getLogger().warn("");
                project.getLogger().warn("  [B] To update Wrapper settings to current runtime version (" + actualRuntimeVersion + ") (⚠️ Be cautious, uses your installed `Gradle`):");
                project.getLogger().warn("     gradle wrapper --gradle-version " + actualRuntimeVersion);
                project.getLogger().warn(separator);
            }
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
    public static void includeExtraFiles(Jar jarTask, Project project, Set<String> extraFiles) {
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
     * Configures Shadow plugin for Build mode.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 빌드 시 Shadow 플러그인을 설정합니다 (Fat JAR 생성 및 의존성 쉐이딩).
     *
     * - Fat JAR 생성
     * - implementation/runtimeOnly 의존성만 동적 쉐이딩
     *
     * <p>
     * <b>[Conditional Relocation]</b><br>
     * 프로젝트의 {@code ext.shadedPackagePrefix} 속성이 설정된 경우에만 패키지 재배치(Relocation)를 수행합니다.<br>
     * 설정 예시 (build.gradle): {@code ext { shadedPackagePrefix = "io.github.devers2.s2util.shaded" }}
     * </p>
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param shadowTask      Shadow JAR task | Shadow JAR 태스크
     * @param shadowExtension Shadow extension object | Shadow 확장 객체
     * @param archiveBaseName Archive base name | 아카이브 기본 이름
     * @param version         Project version | 버전
     * @param extraFiles      Set of extra files to include | 추가 파일 목록
     */
    private static void configureShadowForBuild(Project project, org.gradle.api.Task shadowTask,
            Object shadowExtension, String archiveBaseName, String version, Set<String> extraFiles) {
        try {
            info(project, "🔧 [Shadow] 빌드 모드: Fat JAR 생성 및 의존성 동적 쉐이딩을 구성합니다.", "🔧 [Shadow] Build Mode: Configuring Fat JAR generation and dynamic dependency shading.");

            if (!(shadowTask instanceof com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar)) {
                warn(project, "⚠️ [Shadow] 태스크가 ShadowJar 타입이 아닙니다. 설정이 무시될 수 있습니다.", "⚠️ [Shadow] Task is not of type ShadowJar. Settings may be ignored.");
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
                    info(project, "✅ [Shadow] Configurations 설정을 통해 Fat JAR 모드를 활성화했습니다.", "✅ [Shadow] Fat JAR mode enabled via Configurations setting.");
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
                                info(project, "   ✅ 디렉토리 추가 [" + source + "]: " + filePath, "   ✅ Adding directory [" + source + "]: " + filePath);
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
                                info(project, "   ✅ 루트 파일 추가 [" + source + "]: " + filePath, "   ✅ Adding root file [" + source + "]: " + filePath);
                                shadowJar.from(file);
                            }
                        } else {
                            warn(project, "   ⚠️  파일을 찾을 수 없습니다: " + filePath, "   ⚠️  File not found: " + filePath);
                        }
                    }
                    info(project, "✅ [Shadow] 추가 파일 포함 완료 (" + extraFiles.size() + "개 항목)", "✅ [Shadow] Extra files inclusion complete (" + extraFiles.size() + " items)");
                }

                // 4. Relocation 대상 패키지 식별 (runtimeClasspath 스캔 - api 제외)
                // 빌드 모드에서는 relocation을 하지 않음 (명시적으로 전달된 prefix만 사용)
                // 커맨드라인에서 -PshadedPackagePrefix=... 로 명시적으로 prefix를 전달한 경우에만 relocation 수행
                boolean hasExplicitPrefix = false;
                String prefix = "";

                // [Fix] 커맨드라인 인자 뿐만 아니라 build.gradle의 ext 속성도 확인하도록 변경
                if (project.hasProperty("shadedPackagePrefix")) {
                    String propPrefix = (String) project.property("shadedPackagePrefix");
                    if (propPrefix != null && !propPrefix.trim().isEmpty()) {
                        hasExplicitPrefix = true;
                        prefix = propPrefix;
                    }
                }

                if (!hasExplicitPrefix) {
                    // 커맨드라인 인자 재확인 (우선순위를 위해 남겨둘 수도 있지만, 위에서 이미 체크됨.
                    // 단, 사용자가 -P옵션으로 덮어쓰는 경우를 위해 유지하거나 병합 가능)
                    java.util.List<String> args = project.getGradle().getStartParameter().getProjectProperties().keySet().stream()
                            .filter(key -> "shadedPackagePrefix".equals(key))
                            .map(key -> (String) project.getGradle().getStartParameter().getProjectProperties().get(key))
                            .collect(java.util.stream.Collectors.toList());

                    if (!args.isEmpty()) {
                        hasExplicitPrefix = true;
                        prefix = args.get(0);
                    }
                }

                // 빌드 모드: relocation 하지 않음 (prefix가 명시적으로 전달되지 않은 경우)
                if (hasExplicitPrefix && prefix != null && !prefix.isEmpty()) {
                    Set<String> packagesToRelocate = extractPackagesToRelocate(project);

                    // Relocate 설정
                    for (String pkg : packagesToRelocate) {
                        String fromPackage = pkg;
                        String toPackage = prefix + "." + pkg;
                        shadowJar.relocate(fromPackage, toPackage);
                        info(project, "✅ [Shadow] 패키지 재배치: " + fromPackage + " -> " + toPackage, "✅ [Shadow] Relocate Package: " + fromPackage + " -> " + toPackage);
                    }
                } else {
                    info(project, "ℹ️ [Shadow] 빌드 모드: 패키지 재배치(Relocation)를 건너뜁니다.", "ℹ️ [Shadow] Build Mode: Skipping package relocation.");
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
                warn(project, "⚠️ [Shadow] 빌드 모드 설정 중 오류: " + e.getMessage(), "⚠️ [Shadow] Error during Build mode configuration: " + e.getMessage());
            }
        } catch (Exception e) {
            warn(project, "⚠️ [Shadow] 초기 설정 중 오류: " + e.getMessage(), "⚠️ [Shadow] Error during initial configuration: " + e.getMessage());
        }
    }

    /**
     * Registers a task for verifying Shadow JAR stability.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Shadow JAR의 안정성 검증을 위한 {@code testArtifact} 태스크를 등록합니다.
     *
     * @param project   The Gradle project instance | Gradle 프로젝트 객체
     * @param shadowJar Shadow JAR task | Shadow JAR 태스크
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
                info(project, "ℹ️ [Shadow] 'artifactTestClassNames' 설정으로 인해 일반 테스트를 비활성화하고 'testArtifact'로 대체합니다.", "ℹ️ [Shadow] 'artifactTestClassNames' detected. Disabling standard 'test' task and delegating to 'testArtifact'.");
                testTask.setEnabled(false);
            });

            info(project, "✅ [Shadow] 'testArtifact' 태스크가 빌드 사이클에 등록되었습니다. (대상: " + verifyClasses + ")", "✅ [Shadow] 'testArtifact' task registered to build cycle. (Targets: " + verifyClasses + ")");
        } else {
            // 가이드 로그 출력 (Cyan)
            if (isKorean()) {
                project.getLogger().lifecycle(ANSI_CYAN + "📘 [가이드] 빌드 완료 후 결과물을 테스트하려면 build.gradle에 'ext.artifactTestClassNames = [\"패키지.클래스1\", \"패키지.클래스2\"]'를 설정하세요." + ANSI_RESET);
                project.getLogger().lifecycle(ANSI_CYAN + "   -> 설정 시 './gradlew testArtifact'를 통해 최종 JAR를 클래스패스로 하여 테스트를 실행할 수 있습니다." + ANSI_RESET);
            } else {
                project.getLogger().lifecycle(ANSI_CYAN + "📘 [Guide] To test output after build, set 'ext.artifactTestClassNames = [\"package.Class1\", \"package.Class2\"]' in build.gradle." + ANSI_RESET);
                project.getLogger().lifecycle(ANSI_CYAN + "   -> Once set, you can run tests with built JAR as classpath via './gradlew testArtifact'." + ANSI_RESET);
            }
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
            task.getMainClass().set("io.github.devers2.buildsupport.S2TestLauncher");
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
     * Configures Maven Publication.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Maven 배포(Publication) 설정을 수행합니다.
     *
     * - maven-publish 플러그인이 적용된 경우 mavenJava Publication을 생성 및 설정
     * - Shadow 플러그인 유무에 따라 아티팩트 구성 분기
     *
     * @param project    The Gradle project instance | Gradle 프로젝트 객체
     * @param useShadow  Whether Shadow plugin is used | Shadow 플러그인 사용 여부
     * @param artifactId Artifact ID | 아티팩트 ID
     */
    private static void configurePublications(Project project, boolean useShadow, String artifactId) {
        if (!project.getPluginManager().hasPlugin("maven-publish")) {
            return;
        }

        // Gradle Plugin 프로젝트의 경우 java-gradle-plugin이 이미 Publication을 생성하므로 중복 생성을 피한다.
        if (project.getPluginManager().hasPlugin("java-gradle-plugin")) {
            info(project, "ℹ️ [배포] Gradle Plugin 프로젝트 감지됨. 별도의 'mavenJava' Publication 생성을 건너뜁니다.", "ℹ️ [Publishing] Gradle Plugin project detected. Skipping 'mavenJava' Publication creation.");
            return;
        }

        project.getExtensions().configure("publishing", (org.gradle.api.publish.PublishingExtension publishing) -> {
            // "mavenJava" Publication이 이미 존재하는지 확인
            org.gradle.api.publish.maven.MavenPublication publication;
            if (publishing.getPublications().findByName("mavenJava") != null) {
                publication = (org.gradle.api.publish.maven.MavenPublication) publishing.getPublications().getByName("mavenJava");
                info(project, "ℹ️ [배포] 기존 'mavenJava' Publication을 재사용하여 설정을 업데이트합니다.", "ℹ️ [Publishing] Reusing existing 'mavenJava' Publication.");
            } else {
                publication = publishing.getPublications().create("mavenJava", org.gradle.api.publish.maven.MavenPublication.class);
            }

            // 공통 설정 적용
            publication.setArtifactId(artifactId);

            if (!useShadow) {
                // 1. Shadow 미사용: Standard 모드
                // - from components.java (의존성 정보 자동 포함)
                // - 이미 추가되어 있을 수 있으므로(사용자 정의 등) try-catch로 안전하게 처리
                try {
                    // 이미 SoftwareComponent가 셋팅되어 있는지 확인이 어렵으므로 추가 시도를 하고 중복 오류는 무시하거나 경고 처리
                    publication.from(project.getComponents().getByName("java"));
                } catch (Exception e) {
                    // 이미 존재하거나(중복 추가), components.java가 없는 경우
                    // 로그 레벨을 낮춰서 빌드에 지장을 주지 않도록 함 (INFO or DEBUG)
                    project.getLogger().info("ℹ️ [Publishing] components.java 추가 건너뜀 (이미 존재하거나 사용할 수 없음): " + e.getMessage());
                }
            } else {
                // 2. Shadow 사용: Shadow 모드
                // - 여기서는 빈 Publication만 생성하고 artifactId만 설정
                // - 실제 아티팩트 및 POM 설정은 configureShadowForPublish (afterEvaluate)에서 처리
                // - components.java를 사용하지 않음 (Shadow와 충돌)
            }
        });
    }

    /**
     * Configures Shadow plugin for Publishing mode.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 배포 시 Shadow 플러그인을 설정합니다.
     *
     * - 표준 JAR 생성
     * - implementation/runtimeOnly 의존성만 동적 쉐이딩 후 jar에 소스 포함
     * - api 의존성은 pom에만 추가
     *
     * <p>
     * <b>[Conditional Relocation]</b><br>
     * 프로젝트의 {@code ext.shadedPackagePrefix} 속성이 설정된 경우에만 패키지 재배치(Relocation)를 수행합니다.<br>
     * 설정 예시 (build.gradle): {@code ext { shadedPackagePrefix = "io.github.devers2.s2util.shaded" }}
     * </p>
     *
     * @param project         The Gradle project instance | Gradle 프로젝트 객체
     * @param shadowTask      Shadow JAR task | Shadow JAR 태스크
     * @param shadowExtension Shadow extension object | Shadow 확장 객체
     * @param archiveBaseName Archive base name | 아카이브 기본 이름
     * @param version         Project version | 버전
     * @param extraFiles      Set of extra files to include | 추가 파일 목록
     */
    private static void configureShadowForPublish(Project project, org.gradle.api.Task shadowTask,
            Object shadowExtension, String archiveBaseName, String version, Set<String> extraFiles) {
        try {
            info(project, "🔧 [Shadow] 배포 모드: 표준 JAR 생성 및 의존성 동적 쉐이딩을 구성합니다.", "🔧 [Shadow] Publishing Mode: Configuring standard JAR generation and dynamic dependency shading.");

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
                    shadowJar.doFirst(new org.gradle.api.Action<org.gradle.api.Task>() {
                        @Override
                        public void execute(org.gradle.api.Task t) {
                            try {
                                // 외부의 getConfigsMethod를 그대로 사용하여 invoke
                                Optional.ofNullable(getConfigsMethod.invoke(shadowJar))
                                        .filter(java.util.List.class::isInstance)
                                        .map(obj -> (java.util.List<?>) obj)
                                        .ifPresent(list -> {
                                            list.clear();
                                            info(project, "✅ [Shadow] 배포 모드: configurations 설정을 초기화했습니다 (의존성 미포함).", "✅ [Shadow] Publishing Mode: Cleared configurations (dependencies excluded).");
                                        });
                            } catch (Exception e2) {
                                // ignore
                            }
                        }
                    });
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
                        info(project, "📋 [Shadow 배포] 추가 파일을 포함합니다: " + shadowTask.getName(), "📋 [Shadow Publish] Including extra files into " + shadowTask.getName());

                        for (String filePath : extraFiles) {
                            // 1. 프로젝트 기준 탐색
                            File file = project.file(filePath);

                            if (file.exists()) {
                                String source = "projectDir";

                                if (file.isDirectory()) {
                                    // 디렉토리인 경우 fileTree 사용
                                    info(project, "   ✅ 디렉토리 추가 [" + source + "]: " + filePath, "   ✅ Adding directory [" + source + "]: " + filePath);
                                    copySpec.from(project.fileTree(file));
                                } else if (filePath.contains("/")) {
                                    // 경로가 포함된 파일 (예: licenses/LICENSE-MIT) -> 상위 디렉토리 유지
                                    String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
                                    info(project, "   ✅ 파일 추가 [" + source + "]: " + filePath + " -> " + parentPath + "/", "   ✅ Adding file [" + source + "]: " + filePath + " -> " + parentPath + "/");

                                    File finalFile = file;
                                    copySpec.from(finalFile, spec -> {
                                        if (spec instanceof org.gradle.api.file.CopySpec) {
                                            ((org.gradle.api.file.CopySpec) spec).into(parentPath);
                                        }
                                    });
                                } else {
                                    // 루트 레벨 파일 (예: README.md) -> 루트에 저장
                                    info(project, "   ✅ 루트 파일 추가 [" + source + "]: " + filePath, "   ✅ Adding root file [" + source + "]: " + filePath);
                                    copySpec.from(file);
                                }
                            } else {
                                warn(project, "   ⚠️  파일을 찾을 수 없습니다: " + filePath, "   ⚠️  File not found: " + filePath);
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
                    warn(project, "⚠️  [Shadow] 추가 파일 포함 중 오류: " + e.getMessage(), "⚠️  [Shadow] Error including extra files: " + e.getMessage());
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

                        info(project, "✅ [Shadow] API 의존성(전이 포함) " + apiArtifactIdsForExclude.size() + "개를 Shadow JAR에서 제외했습니다.", "✅ [Shadow] Excluded " + apiArtifactIdsForExclude.size() + " API dependencies (including transitive) from Shadow JAR.");

                    } catch (Exception e) {
                        warn(project, "⚠️ [Shadow] API 제외 설정(dependencies) 중 오류: " + e.getMessage(), "⚠️ [Shadow] Error during API exclusion (dependencies): " + e.getMessage());
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
                                info(project, "✅ [Shadow] 패키지 재배치: " + fromPackage + " -> " + toPackage, "✅ [Shadow] Relocate Package: " + fromPackage + " -> " + toPackage);
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        warn(project, "⚠️  [Shadow] relocate 메서드를 찾을 수 없습니다: " + e.getMessage(), "⚠️  [Shadow] 'relocate' method not found: " + e.getMessage());
                    }
                } else {
                    info(project, "ℹ️ [Shadow] 'shadedPackagePrefix' 속성이 비어있어 재배치를 건너뜁니다.", "ℹ️ [Shadow] 'shadedPackagePrefix' property is empty, skipping relocation.");
                }
            } else {
                info(project, "ℹ️ [Shadow] 'shadedPackagePrefix' 속성이 없어 재배치를 건너뜁니다.", "ℹ️ [Shadow] 'shadedPackagePrefix' property not found, skipping relocation.");
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
     * Configures publishing for Shadow.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Shadow 사용 시의 배포(Publishing) 설정을 수행합니다.
     * <p>
     * 1. Shadow JAR를 메인 아티팩트로 사용합니다 (-all 접미사 제거).
     * 2. {@code implementation}/{@code runtimeOnly} 의존성은 POM에서 제외합니다.
     * 3. {@code api} 의존성은 POM에 수동으로 추가합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
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
                    String msgKo = "❌ [Shadow] 오류: Shadow 플러그인 사용 시 'from components.java'를 사용할 수 없습니다.\n" +
                            "❌ [Shadow] build.gradle의 publishing 블록에서 'from components.java'를 제거하고\n" +
                            "❌ [Shadow] 아티팩트를 직접 추가하세요. 예:\n" +
                            "❌ [Shadow]   mavenJava(MavenPublication) {\n" +
                            "❌ [Shadow]       // from components.java  <- 이 줄 제거\n" +
                            "❌ [Shadow]       artifactId = base.archivesName.get()\n" +
                            "❌ [Shadow]       artifact(tasks.named('shadowJar'))\n" +
                            "❌ [Shadow]       // ... 기타 아티팩트\n" +
                            "❌ [Shadow]   }";
                    String msgEn = "❌ [Shadow] Error: 'from components.java' cannot be used with Shadow plugin.\n" +
                            "❌ [Shadow] Please remove 'from components.java' from publishing block and add artifacts manually. Example:\n" +
                            "❌ [Shadow]   mavenJava(MavenPublication) {\n" +
                            "❌ [Shadow]       // from components.java  <- remove this line\n" +
                            "❌ [Shadow]       artifactId = base.archivesName.get()\n" +
                            "❌ [Shadow]       artifact(tasks.named('shadowJar'))\n" +
                            "❌ [Shadow]       // ... other artifacts\n" +
                            "❌ [Shadow]   }";

                    if (isKorean()) {
                        project.getLogger().error(msgKo);
                    } else {
                        project.getLogger().error(msgEn);
                    }
                    throw new IllegalStateException(isKorean() ? "Shadow 플러그인 사용 시 'from components.java'를 사용할 수 없습니다." : "'from components.java' cannot be used with Shadow plugin.");
                }

                // 기존 아티팩트 제거
                publication.getArtifacts().clear();

                // Shadow JAR를 메인 아티팩트로 사용 (Lazy Configuration 사용)
                try {
                    publication.artifact(project.getTasks().named("shadowJar"));
                } catch (Exception e) {
                    warn(project, "⚠️ [Shadow] shadowJar 태스크를 찾을 수 없습니다.", "⚠️ [Shadow] shadowJar task not found.");
                }

                // Sources JAR 처리 (Lazy Configuration 사용)
                try {
                    org.gradle.api.tasks.TaskProvider<?> sourcesJarProvider = project.getTasks().named("sourcesJar");

                    // 원격 리포지토리 공개 여부 확인
                    // 소스 JAR 생성 여부 결정 (Unified Logic)
                    // 이미 determineSourceJarStatus에 의해 rootProject.ext.enableSourceJar가 설정되어 있을 것이나,
                    // Shadow 플러그인 컨텍스트에서 다시 한 번 확인하거나 값을 가져옴.
                    boolean enableSourceJar = false;
                    if (project.getRootProject().getExtensions().getExtraProperties().has("enableSourceJar")) {
                        enableSourceJar = (boolean) project.getRootProject().getExtensions().getExtraProperties().get("enableSourceJar");
                    } else {
                        // 만약 설정이 안되어 있다면(순서 문제 등), 다시 계산
                        enableSourceJar = determineSourceJarStatus(project);
                    }

                    if (enableSourceJar) {
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
                    info(project, "✅ [Shadow 배포] POM 파일에 'api' 의존성을 수동으로 추가했습니다.", "✅ [Shadow Publish] Manually added 'api' dependencies to generated POM.");
                }
            });
        });
    }

    /**
     * Updates version information and adds runtime dependency guides to the README file.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * README 파일의 버전 정보를 업데이트하고, 필요한 경우 런타임 의존성 가이드를 추가합니다.
     * <p>
     * 1. 버전 업데이트: {@code Implementation-Version} 패턴 등을 찾아 현재 프로젝트 버전으로 교체합니다.
     * 2. 의존성 가이드: {@code compileOnly}로 선언된 라이브러리가 있다면, 소비자가 런타임에 추가해야 함을 README에 삽입합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @param file    Target file (usually README.md) | 대상 파일 (주로 README.md)
     */
    public static void updateReadmeWithVersionAndDependencies(Project project, File file) {
        if (!file.exists())
            return;

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String currentVersion = project.getVersion().toString();
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 1. 버전 및 날짜 업데이트 로직 (버전이 바뀔 때만 날짜 변경함)
            // s2-core Version, s2-validator-plugin Version 등 다양한 접두사를 지원하도록 수정
            Pattern vPattern = Pattern.compile("(s2-[\\w-]+ Version): ([\\w\\.\\-]+) \\((\\d{4}-\\d{2}-\\d{2})\\)");
            Matcher vMatcher = vPattern.matcher(content);
            if (vMatcher.find()) {
                String prefix = vMatcher.group(1); // 예: "s2-core Version"
                String existingVersion = vMatcher.group(2);
                // 버전이 기존과 다를 경우에만 전체 문구 교체함
                if (!existingVersion.equals(currentVersion)) {
                    content = content.replace(vMatcher.group(0), prefix + ": " + currentVersion + " (" + today + ")");
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
                depsBlock.append("**To use certain functionalities (e.g., S2BindValidator), the end-user project must explicitly add the following dependencies to be available at runtime.** ");
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
            warn(project, "⚠️ [README 업데이트] " + file.getName() + " 업데이트 실패: " + e.getMessage(), "⚠️ [README Update] Failed to update " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Toggles specific sections in the NOTICE file based on active features.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@code activeFeatures}에 따라 NOTICE 파일 내의 특정 섹션을 활성화/비활성화하여 임시 파일로 반환합니다.
     * 원본 파일은 유지되며, {@code build/tmp} 디렉토리에 새로운 파일이 생성됩니다.
     *
     * @param project    The Gradle project instance | Gradle 프로젝트 객체
     * @param sourceFile Original NOTICE file | 원본 NOTICE 파일
     * @return Temporary NOTICE file with modified content | 수정된 내용을 담은 임시 NOTICE 파일
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
            warn(project, "⚠️ [라이선스] 동적 NOTICE 파일 생성 실패: " + e.getMessage(), "⚠️ [License] Failed to generate dynamic NOTICE file: " + e.getMessage());
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
     * Returns a set of transitive dependency IDs for local project dependencies.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 로컬 프로젝트 의존성의 전이 의존성(Children) ID 집합을 반환합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @return Set of dependency IDs to exclude ("group:name") | 제외할 의존성 ID 집합 ("group:name")
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
            warn(project, "⚠️ [의존성] 제외 대상 계산 중 의존성 해제 실패: " + e.getMessage(), "⚠️ [S2BuildUtils] Failed to resolve dependencies for exclusion calculation: " + e.getMessage());
        }
        return dependenciesToExclude;
    }

    /**
     * Excludes transitive dependencies of local project dependencies from the Shadow JAR.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 로컬 프로젝트 의존성({@code ProjectDependency})의 전이 의존성을 Shadow JAR에서 제외합니다.
     *
     * @param project   The Gradle project instance | Gradle 프로젝트 객체
     * @param shadowJar Shadow JAR task | Shadow JAR 태스크
     */
    private static void excludeTransitiveDependenciesOfLocalProjects(Project project, com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar shadowJar) {
        Set<String> dependenciesToExclude = getTransitiveDependenciesOfLocalProjects(project);
        if (!dependenciesToExclude.isEmpty()) {
            shadowJar.dependencies(dependenciesSpec -> {
                dependenciesToExclude.forEach(depId -> {
                    dependenciesSpec.exclude(dependenciesSpec.dependency(depId));
                });
            });
            info(project, "      🚫 [Shadow] 중복된 전이 의존성 " + dependenciesToExclude.size() + "개를 제외했습니다.", "      🚫 [Shadow] Excluded " + dependenciesToExclude.size() + " duplicated transitive dependencies.");
            dependenciesToExclude.forEach(depId -> project.getLogger().debug("         - Exclude: " + depId));
        }
    }

    /**
     * Extracts the list of packages to relocate.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 패키지 재배치(Relocation) 대상 목록을 추출합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @return Set of package names to relocate | 재배치 대상 패키지 집합
     */
    private static Set<String> extractPackagesToRelocate(Project project) {
        Set<String> packagesToRelocate = new java.util.HashSet<>();
        Set<String> targetArtifactIds = getRelocatableArtifactIDs(project);

        info(project, "🔍 [Shadow] 패키지 재배치 대상 스캔을 시작합니다...", "🔍 [Shadow] Starting scan for relocation target packages...");

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

                    info(project, "📦 [Shadow] JAR 스캔 중: " + file.getName() + " (" + id + ")", "📦 [Shadow] Scanning JAR: " + file.getName() + " (" + id + ")");
                    scanJarForPackages(project, file, packagesToRelocate);
                }
            } catch (Exception ignored) {
            }
        }
        return packagesToRelocate;
    }

    /**
     * Returns a set of artifact IDs (Group:Name) that are targets for relocation.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 패키지 재배치 대상이 되는 아티팩트 ID(Group:Name) 목록을 추출합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     * @return Set of relocatable artifact IDs | 재배치 대상 아티팩트 ID 집합
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
                warn(project, "⚠️ [Shadow] runtimeClasspath 분석 중 오류: " + e.getMessage(), "⚠️ [Shadow] Error during runtimeClasspath analysis: " + e.getMessage());
            }
        }
        return relocatableIds;
    }

    /**
     * Scans a JAR file and extracts all top-level package names.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * JAR 파일을 스캔하여 포함된 모든 최상위 패키지명을 추출합니다.
     *
     * @param project  The Gradle project instance | Gradle 프로젝트 객체
     * @param jarFile  The JAR file to scan | 스캔할 JAR 파일
     * @param packages Set to store extracted packages | 추출된 패키지를 저장할 집합
     */
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
            warn(project, "⚠️ [Shadow] JAR 스캔 실패 (" + jarFile.getName() + "): " + e.getMessage(), "⚠️ [Shadow] JAR scan failed (" + jarFile.getName() + "): " + e.getMessage());
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
                !pkg.startsWith("io.github.devers2.") && !pkg.startsWith("org.w3c.") && !pkg.startsWith("org.xml.");
    }

    // ========================================================================
    // Central Portal Publishing (Hijack Task)
    // ========================================================================

    /**
     * Detects Central Portal publishing task and replaces it with Zip bundle upload.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * Central Portal 배포 태스크를 감지하여 Zip 번들 업로드 방식으로 교체합니다.
     * <p>
     * Maven Central Portal의 새로운 API는 Zip 번들 업로드를 필수로 요구합니다.
     * 모든 아티팩트와 서명을 포함한 Zip 번들을 생성하여 업로드합니다.
     * </p>
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     */
    public static void configureCentralPortalPublishing(Project project) {
        project.afterEvaluate(p -> {
            p.getTasks().withType(org.gradle.api.publish.maven.tasks.PublishToMavenRepository.class).configureEach(task -> {
                // CentralPortal 리포지토리로 배포하는 태스크인지 확인
                if (task.getRepository() != null && "CentralPortal".equals(task.getRepository().getName())) {
                    // 1. Repository URL (build.gradle 설정 값 사용)
                    final String centralUploadUrl = task.getRepository().getUrl().toString();

                    // 2. 기존 동작 제거 및 새 동작 주입
                    task.getActions().clear();

                    // 서명 태스크 의존성 강제 추가
                    // - Gradle 플러그인 프로젝트에서는 여러 Publication(sign tasks)이 존재할 수 있으므로
                    // 모든 Sign 타입 태스크에 대해 의존성을 추가하여 .asc 파일이 항상 생성되도록 보장한다.
                    task.dependsOn(p.getTasks().withType(org.gradle.plugins.signing.Sign.class));
                    info(project, "🔗 [중앙 포털] 서명 태스크 의존성 설정 완료", "🔗 [Central Portal] Set dependency on signing tasks.");

                    task.doLast(t -> {
                        // Publication 정보 직접 가져오기 (각 Publication마다 다른 artifactId 사용)
                        org.gradle.api.publish.maven.MavenPublication publication = (org.gradle.api.publish.maven.MavenPublication) task.getPublication();
                        String publicationName = publication.getName();
                        String groupId = publication.getGroupId();
                        String artifactId = publication.getArtifactId();
                        String version = publication.getVersion();

                        info(
                                project,
                                "🚀 [중앙 포털] Zip 번들 업로드를 시작합니다 [" + publicationName + "]...",
                                "🚀 [Central Portal] Starting Zip bundle upload [" + publicationName + "]..."
                        );

                        // 필요한 파일 수집
                        File buildDir = project.getLayout().getBuildDirectory().getAsFile().get();

                        // Publication별 독립적인 번들 디렉토리
                        File bundleDir = new File(buildDir, "distributions/central-bundle/" + publicationName);
                        if (bundleDir.exists())
                            project.delete(bundleDir);
                        bundleDir.mkdirs();

                        List<File> filesToBundle = new ArrayList<>();

                        // 1) JARs & Signatures (libs 폴더)
                        File libsDir = new File(buildDir, "libs");
                        final boolean isSnapshotVersion = version.contains("SNAPSHOT");
                        if (libsDir.exists()) {
                            // 넓은 의미의 매칭: 파일 이름에 '-<version>' 패턴이 포함된 아티팩트는 모두 포함
                            File[] files = libsDir.listFiles((dir, name) -> {
                                if (!(name.endsWith(".jar") || name.endsWith(".asc") || name.endsWith(".pom")))
                                    return false;
                                if (!isSnapshotVersion && name.contains("SNAPSHOT")) {
                                    project.getLogger().debug("         - Skipping SNAPSHOT file for non-SNAPSHOT release: " + name);
                                    return false;
                                }
                                // 일반적으로 아티팩트명은 '<artifactId>-<version>...' 형태이므로 '-<version>' 포함 여부로 필터링
                                return name.contains("-" + version + ".") || name.contains("-" + version + "-") || name.endsWith("-" + version + ".jar") || name.endsWith("-" + version + ".pom");
                            });

                            if (files != null)
                                filesToBundle.addAll(Arrays.asList(files));
                        }

                        // 1-1) publications 폴더 내 다른 Publication들(예: pluginMaven 등)에 생성된 아티팩트도 포함
                        File publicationsRoot = new File(buildDir, "publications");
                        if (publicationsRoot.exists()) {
                            File[] pubDirs = publicationsRoot.listFiles(File::isDirectory);
                            if (pubDirs != null) {
                                for (File pubDir : pubDirs) {
                                    File[] pubFiles = pubDir.listFiles((dir, name) -> {
                                        if (!(name.endsWith(".jar") || name.endsWith(".asc") || name.endsWith(".pom")))
                                            return false;
                                        if (!isSnapshotVersion && name.contains("SNAPSHOT"))
                                            return false;
                                        return name.contains("-" + version + ".") || name.contains("-" + version + "-") || name.endsWith("-" + version + ".jar") || name.endsWith("-" + version + ".pom");
                                    });
                                    if (pubFiles != null)
                                        filesToBundle.addAll(Arrays.asList(pubFiles));
                                }
                            }
                        }

                        // 2) POM & Signature (publications/[publicationName] 폴더)
                        File pomDir = new File(buildDir, "publications/" + publicationName);
                        if (pomDir.exists()) {
                            File[] poms = pomDir.listFiles((dir, name) -> name.equals("pom-default.xml"));
                            if (poms != null) {
                                for (File pom : poms) {
                                    // POM 이름 변경 (artifactId-version.pom)
                                    File renamedPom = new File(bundleDir, artifactId + "-" + version + ".pom");
                                    try {
                                        Files.copy(pom.toPath(), renamedPom.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        filesToBundle.add(renamedPom);

                                        // POM 서명 파일 찾기 (.asc)
                                        File pomAsc = new File(pomDir, "pom-default.xml.asc");
                                        if (pomAsc.exists()) {
                                            File renamedPomAsc = new File(bundleDir, artifactId + "-" + version + ".pom.asc");
                                            Files.copy(pomAsc.toPath(), renamedPomAsc.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                            filesToBundle.add(renamedPomAsc);
                                        }
                                    } catch (IOException e) {
                                        error(project, "❌ [중앙 포털] POM 파일 처리 실패: " + e.getMessage(), "❌ [Central Portal] POM file processing failed: " + e.getMessage());
                                    }
                                }
                            }
                        }

                        // 2-1) 서명(.asc) 없는 파일 제외 & Checksum 생성
                        List<File> validatedFiles = new ArrayList<>();
                        // 서명 파일 존재 여부를 위한 Set
                        Set<String> fileNames = filesToBundle.stream().map(File::getName).collect(java.util.stream.Collectors.toSet());

                        // Checksum 파일 리스트
                        List<File> checksumFiles = new ArrayList<>();

                        for (File f : filesToBundle) {
                            // 서명/체크섬 파일 자체는 검증 대상에서 제외하고 그대로 포함
                            if (f.getName().endsWith(".asc") || f.getName().endsWith(".md5") || f.getName().endsWith(".sha1")) {
                                validatedFiles.add(f);
                                continue;
                            }

                            // 아티팩트(.jar, .pom)인 경우 서명 파일 존재 여부 확인
                            String ascName = f.getName() + ".asc";
                            if (!fileNames.contains(ascName)) {
                                warn(project, "⚠️ [중앙 포털] 서명(.asc) 파일이 없어 건너뜁니다: " + f.getName(), "⚠️ [Central Portal] Missing signature (.asc), skipping: " + f.getName());
                                continue;
                            }
                            validatedFiles.add(f);

                            // Checksum (MD5, SHA1) 생성
                            try {
                                byte[] content = Files.readAllBytes(f.toPath());

                                java.security.MessageDigest md5Digest = java.security.MessageDigest.getInstance("MD5");
                                String md5 = String.format("%032x", new java.math.BigInteger(1, md5Digest.digest(content)));

                                java.security.MessageDigest sha1Digest = java.security.MessageDigest.getInstance("SHA-1");
                                String sha1 = String.format("%040x", new java.math.BigInteger(1, sha1Digest.digest(content)));

                                File md5File = new File(bundleDir, f.getName() + ".md5");
                                File sha1File = new File(bundleDir, f.getName() + ".sha1");

                                Files.write(md5File.toPath(), md5.getBytes(StandardCharsets.UTF_8));
                                Files.write(sha1File.toPath(), sha1.getBytes(StandardCharsets.UTF_8));

                                checksumFiles.add(md5File);
                                checksumFiles.add(sha1File);
                            } catch (Exception e) {
                                warn(project, "⚠️ [중앙 포털] 체크섬 생성 실패: " + f.getName(), "⚠️ [Central Portal] Checksum generation failed: " + f.getName());
                            }
                        }
                        filesToBundle = validatedFiles;
                        filesToBundle.addAll(checksumFiles);

                        if (filesToBundle.isEmpty()) {
                            throw new org.gradle.api.GradleException("❌ [Central Portal] 번들링할 파일이 없습니다. (서명 파일 누락 등 확인 필요)");
                        }

                        // 3. Zip 번들 생성 (Maven Layout 적용)
                        // publicationName이 기본 'mavenJava'가 아닐 경우 이름에 publicationName을 추가하여
                        // pluginMaven 등 여러 출판물이 동일한 artifactId/버전으로 덮어쓰지 않도록 방지
                        String bundleFileName = artifactId + "-" + version + ("mavenJava".equals(publicationName) ? "" : "-" + publicationName) + ".zip";
                        File zipFile = new File(buildDir, "distributions/" + bundleFileName);
                        zipFile.getParentFile().mkdirs();

                        try (FileOutputStream fos = new FileOutputStream(zipFile);
                                ZipOutputStream zos = new ZipOutputStream(fos)) {

                            info(project, "📦 [중앙 포털] 번들링 대상 파일 목록 (Maven Layout 적용):", "📦 [Central Portal] Files for bundling (Maven Layout):");
                            // Publication의 groupId 사용
                            String groupPath = groupId.replace(".", "/");
                            String mavenPathPrefix = groupPath + "/" + artifactId + "/" + version + "/";

                            for (File file : filesToBundle) {
                                String entryName = mavenPathPrefix + file.getName();
                                info(project, "   - " + entryName + " (" + file.length() + " bytes)", "   - " + entryName + " (" + file.length() + " bytes)");

                                try {
                                    ZipEntry zipEntry = new ZipEntry(entryName);
                                    zos.putNextEntry(zipEntry);
                                    Files.copy(file.toPath(), zos);
                                    zos.closeEntry();
                                } catch (Exception e) {
                                    throw new IOException("파일 번들링 중 오류 발생: " + file.getName() + " - " + e.getMessage(), e);
                                }
                            }
                            info(project, "📦 [중앙 포털] Zip 번들 생성 완료: " + zipFile.getAbsolutePath(), "📦 [Central Portal] Zip bundle created: " + zipFile.getAbsolutePath());
                            info(project, "   - 포함된 파일 수: " + filesToBundle.size(), "   - Included files count: " + filesToBundle.size());
                        } catch (IOException e) {
                            // 상세 에러 메시지를 포함하여 예외 발생
                            error(project, "❌ [중앙 포털] Zip 생성 중 치명적 오류: " + e.getMessage(), "❌ [Central Portal] Critical error during Zip creation: " + e.getMessage());
                            throw new org.gradle.api.GradleException(isKorean() ? "❌ [중앙 포털] Zip 번들 생성 실패: " + e.getMessage() : "❌ [Central Portal] Zip bundle creation failed: " + e.getMessage(), e);
                        }

                        // 4. 업로드 (HttpClient)
                        String username = (String) project.findProperty("centralUsername");
                        String password = (String) project.findProperty("centralPassword");

                        if (username == null || password == null) {
                            warn(project, "⚠️ [중앙 포털] 업로드를 위한 인증 정보(centralUsername, centralPassword)가 없습니다. Zip 파일 생성까지만 진행되었습니다.", "⚠️ [Central Portal] Missing authentication (centralUsername, centralPassword). Zip creation completed, but upload skipped.");
                            return;
                        }

                        // 공백 및 따옴표 제거 (사용자 실수 방지)
                        username = username.trim().replace("\"", "").replace("'", "");
                        password = password.trim().replace("\"", "").replace("'", "");

                        info(project, "📤 [중앙 포털] 업로드를 시작합니다...", "📤 [Central Portal] Starting upload...");
                        info(project, "   - User: " + username, "   - User: " + username);
                        project.getLogger().debug("   - Password Length: " + password.length()); // 디버그용 (값은 노출하지 않음)

                        try {
                            String boundary = "---ContentBoundary" + System.currentTimeMillis();

                            try (java.io.ByteArrayOutputStream bodyOs = new java.io.ByteArrayOutputStream()) {
                                bodyOs.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                                // Content-Disposition에 실제 파일명 반영
                                bodyOs.write(("Content-Disposition: form-data; name=\"bundle\"; filename=\"" + bundleFileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                                bodyOs.write(("Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                bodyOs.write(Files.readAllBytes(zipFile.toPath()));
                                bodyOs.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

                                byte[] bodyBytes = bodyOs.toByteArray();

                                HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

                                String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(centralUploadUrl))
                                        .header("Authorization", "Basic " + auth)
                                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                        .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                                        .build();

                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                    info(project, "✅ [중앙 포털] 업로드 성공! (Deployment ID: " + response.body() + ")", "✅ [Central Portal] Upload successful! (Deployment ID: " + response.body() + ")");
                                } else {
                                    if (response.statusCode() == 401) {
                                        String msgKo = "🚨 [중앙 포털] 인증 실패 (401 Unauthorized)\n" +
                                                "   👉 Sonatype Central Portal은 로그인 비밀번호가 아닌 'User Token'을 사용해야 합니다.\n" +
                                                "   👉 토큰 생성: https://central.sonatype.com/account -> 'Generate User Token'\n" +
                                                "   👉 gradle.properties에 'User Token Name'을 centralUsername으로,\n" +
                                                "      'User Token Password'를 centralPassword로 설정하세요.";
                                        String msgEn = "🚨 [Central Portal] Authentication failed (401 Unauthorized)\n" +
                                                "   👉 Central Portal requires 'User Token', not login password.\n" +
                                                "   👉 Generate Token: https://central.sonatype.com/account -> 'Generate User Token'\n" +
                                                "   👉 Set 'User Token Name' as centralUsername and 'User Token Password' as centralPassword in gradle.properties.";

                                        if (isKorean()) {
                                            project.getLogger().error(msgKo);
                                        } else {
                                            project.getLogger().error(msgEn);
                                        }
                                    }
                                    throw new org.gradle.api.GradleException(isKorean() ? "❌ [중앙 포털] 업로드 실패 (HTTP " + response.statusCode() + "): " + response.body() : "❌ [Central Portal] Upload failed (HTTP " + response.statusCode() + "): " + response.body());
                                }
                            }
                        } catch (Exception e) {
                            error(project, "❌ [중앙 포털] 업로드 중 예외 발생: " + e.getMessage(), "❌ [Central Portal] Exception during upload: " + e.getMessage());
                            throw new org.gradle.api.GradleException(isKorean() ? "❌ [중앙 포털] 업로드 중 예외 발생: " + e.getMessage() : "❌ [Central Portal] Exception during upload: " + e.getMessage(), e);
                        }
                    });
                }
            });
        });
    }
}
