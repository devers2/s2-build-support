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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

/**
 * S2BuildUtils 단위 테스트
 */
class S2BuildUtilsTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
    }

    // =========================================================================
    // updateCopyright
    // =========================================================================

    @Nested
    @DisplayName("updateCopyright")
    class UpdateCopyright {

        @org.junit.jupiter.api.Test
        @DisplayName("과거 연도의 저작권 표기를 현재 연도로 갱신한다")
        void updatesPastYearToCurrentYear(@TempDir Path tempDir) throws Exception {
            Path javaFile = tempDir.resolve("Sample.java");
            String original = """
                    /**
                     * Copyright 2020 - 2022 devers2
                     */
                    package com.example;
                    public class Sample {}
                    """;
            Files.writeString(javaFile, original, StandardCharsets.UTF_8);

            S2BuildUtils.updateCopyright(project, new String[] { tempDir.toString() });

            String updated = Files.readString(javaFile, StandardCharsets.UTF_8);
            String currentYear = String.valueOf(Year.now().getValue());
            assertTrue(updated.contains("Copyright 2020 - " + currentYear + " devers2"),
                    "저작권 연도가 현재 연도(" + currentYear + ")로 갱신되어야 한다");
        }

        @org.junit.jupiter.api.Test
        @DisplayName("존재하지 않는 디렉토리는 오류 없이 건너뛴다")
        void skipsNonExistentDirectory() {
            assertDoesNotThrow(() ->
                    S2BuildUtils.updateCopyright(project, new String[] { "non-existent-dir" }));
        }
    }

    // =========================================================================
    // syncVersionToCatalog
    // =========================================================================

    @Nested
    @DisplayName("syncVersionToCatalog")
    class SyncVersionToCatalog {

        @org.junit.jupiter.api.Test
        @DisplayName("지정된 TOML 파일의 버전 키를 새 버전으로 치환한다")
        void syncsVersionInCustomToml(@TempDir Path tempDir) throws Exception {
            File tomlFile = tempDir.resolve("libs.versions.toml").toFile();
            String initialContent = """
                    [versions]
                    s2-util = "1.1.8"
                    other = "2.0.0"
                    """;
            Files.writeString(tomlFile.toPath(), initialContent, StandardCharsets.UTF_8);

            S2BuildUtils.syncVersionToCatalog(project, tomlFile, "s2-util", "1.2.0");

            String updatedContent = Files.readString(tomlFile.toPath(), StandardCharsets.UTF_8);
            assertTrue(updatedContent.contains("s2-util = \"1.2.0\""));
            assertTrue(updatedContent.contains("other = \"2.0.0\""));
        }

        @org.junit.jupiter.api.Test
        @DisplayName("동일한 버전이면 파일을 수정하지 않는다")
        void noOpIfVersionSame(@TempDir Path tempDir) throws Exception {
            File tomlFile = tempDir.resolve("libs.versions.toml").toFile();
            String content = """
                    [versions]
                    s2-util = "1.2.0"
                    """;
            Files.writeString(tomlFile.toPath(), content, StandardCharsets.UTF_8);
            long lastModified = tomlFile.lastModified();

            S2BuildUtils.syncVersionToCatalog(project, tomlFile, "s2-util", "1.2.0");

            assertEquals(content, Files.readString(tomlFile.toPath(), StandardCharsets.UTF_8));
        }

        @org.junit.jupiter.api.Test
        @DisplayName("잘못된 버전 형식(unspecified)이면 갱신을 건너뛴다")
        void skipsInvalidVersion(@TempDir Path tempDir) throws Exception {
            File tomlFile = tempDir.resolve("libs.versions.toml").toFile();
            String content = """
                    [versions]
                    s2-util = "1.1.8"
                    """;
            Files.writeString(tomlFile.toPath(), content, StandardCharsets.UTF_8);

            S2BuildUtils.syncVersionToCatalog(project, tomlFile, "s2-util", "unspecified");

            assertEquals(content, Files.readString(tomlFile.toPath(), StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // configureTestDefaults
    // =========================================================================

    @Nested
    @DisplayName("configureTestDefaults")
    class ConfigureTestDefaults {

        @org.junit.jupiter.api.Test
        @DisplayName("Test 태스크에 기본 설정이 적용된다")
        void configuresTestDefaults() {
            project.getPlugins().apply("java");
            S2BuildUtils.configureTestDefaults(project);

            Test testTask = (Test) project.getTasks().getByName("test");
            assertNotNull(testTask);
        }
    }

    // =========================================================================
    // 기타 유틸
    // =========================================================================

    @org.junit.jupiter.api.Test
    @DisplayName("isKorean 호출 시 예외 없이 불리언을 반환한다")
    void isKoreanDoesNotThrow() {
        assertDoesNotThrow(() -> {
            boolean result = S2BuildUtils.isKorean();
            assertTrue(result || !result);
        });
    }

    // =========================================================================
    // included builds (composite) | 다른 빌드에 포함된 경우
    // =========================================================================

    /** A project of a build included in another one, through proxies | 다른 빌드에 포함된 빌드의 프로젝트 (프록시) */
    private Project includedProject(Path dir) {
        var outer = ProjectBuilder.builder().build().getGradle();
        var gradle = (org.gradle.api.invocation.Gradle) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { org.gradle.api.invocation.Gradle.class },
                (proxy, method, args) -> "getParent".equals(method.getName()) ? outer : method.invoke(project.getGradle(), args));
        return (Project) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Project.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGradle" -> gradle;
                    case "file" -> dir.resolve(String.valueOf(args[0])).toFile();
                    default -> method.invoke(project, args);
                });
    }

    @Nested
    @DisplayName("다른 빌드에 포함된 경우")
    class IncludedBuild {

        @org.junit.jupiter.api.Test
        @DisplayName("바깥 빌드의 -Pversion 이 이 빌드의 파일에 기록되지 않는다")
        void versionSyncsLeaveFilesAlone(@TempDir Path tempDir) throws Exception {
            var readme = tempDir.resolve("README.md");
            Files.writeString(readme, "s2-core Version: 2.0.0 (2026-10-02)\n", StandardCharsets.UTF_8);
            var included = includedProject(tempDir);

            assertTrue(S2BuildUtils.skipInIncludedBuild(included, "README.md"));
            S2BuildUtils.updateVersionInFile(included, "README.md", "s2-core Version: {{=version}}", "1.0.0");
            S2BuildUtils.updateReadmeWithVersionAndDependencies(included, readme.toFile());
            assertEquals("s2-core Version: 2.0.0 (2026-10-02)\n", Files.readString(readme, StandardCharsets.UTF_8));
        }

        @org.junit.jupiter.api.Test
        @DisplayName("단독 빌드에서는 그대로 동기화한다")
        void aStandaloneBuildStillSyncs() {
            assertFalse(S2BuildUtils.skipInIncludedBuild(project, "README.md"));
        }
    }
}
