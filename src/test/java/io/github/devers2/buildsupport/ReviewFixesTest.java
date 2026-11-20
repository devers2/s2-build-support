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
import java.util.concurrent.TimeUnit;

import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fixes from the full code review: the artifact launcher without the Gradle API, real file extensions for libs
 * publications, and JDK update versions.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 전체 코드 검토에서 고친 사항을 확인합니다: Gradle API 없는 런처 실행, libs 배포의 실제 확장자, JDK 업데이트 버전.
 */
class ReviewFixesTest {

    @TempDir
    Path dir;

    /** 런처가 main 으로 실행할 대상 (JUnit·Gradle API 를 쓰지 않음) */
    public static class LauncherTarget {
        public static void main(String[] args) {
            System.out.println("target ran: " + String.join(",", args));
        }
    }

    @Test
    @DisplayName("S2TestLauncher 는 Gradle API 가 없는 클래스패스(testArtifact 실행 환경)에서도 동작한다")
    void launcherRunsWithoutGradleApi() throws Exception {
        String mainClasses = new File(S2TestLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getPath();
        String testClasses = new File(LauncherTarget.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getPath();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", mainClasses + File.pathSeparator + testClasses,
                S2TestLauncher.class.getName(), LauncherTarget.class.getName(), "a", "b")
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "launcher timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("target ran: a,b"), output);
    }

    @Test
    @DisplayName("libs 배포는 jar 가 아닌 파일도 실제 확장자로 등록한다")
    void librariesKeepTheirExtension() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        project.getPluginManager().apply("maven-publish");
        project.setGroup("demo.group");
        Path libs = Files.createDirectories(dir.resolve("libs"));
        Files.writeString(libs.resolve("native-lib-1.2.zip"), "zip");

        LibrariesPublisher.registerPublications(project, new Object[][] { { libs.toFile(), "zip" } }, null);

        MavenPublication pub = (MavenPublication) project.getExtensions().getByType(PublishingExtension.class)
                .getPublications().getByName("native_lib_1_2");
        assertEquals("1.2", pub.getVersion());
        MavenArtifact artifact = pub.getArtifacts().iterator().next();
        assertEquals("zip", artifact.getExtension());
    }

    @Test
    @DisplayName("JDK 업데이트 버전(1.8.0_422, 1.8.0_422+8)도 버전으로 인식한다 (문서에 허용으로 적힌 형식)")
    void jdkUpdateVersionsAreRecognized() {
        LibrariesPublisher.VersionInfo plain = LibrariesPublisher.extractVersion("jdk-1.8.0_422");
        assertEquals("jdk", plain.baseName);
        assertEquals("1.8.0_422", plain.version);

        LibrariesPublisher.VersionInfo build = LibrariesPublisher.extractVersion("jdk-1.8.0_422+8");
        assertEquals("1.8.0_422+8", build.version);

        // 기존 동작 유지
        assertEquals("1.78", LibrariesPublisher.extractVersion("bcprov-jdk18on-1.78").version);
        assertFalse(LibrariesPublisher.extractVersion("lib-1.0-hello").hasVersion);
    }
}
