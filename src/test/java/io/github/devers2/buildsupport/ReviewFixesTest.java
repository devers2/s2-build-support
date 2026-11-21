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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

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

    @Test
    @DisplayName("GitHub Packages 존재 확인: 404 만 '없음', 그 밖의 응답은 배포를 시도하지 않고 실패")
    void unknownExistenceStopsThePublish() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int code = path.startsWith("/ok/") ? 200 : path.startsWith("/missing/") ? 404 : 500;
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertTrue(GitHubPackagesClient.artifactExists(base + "/ok/a.pom", null, null));
            assertFalse(GitHubPackagesClient.artifactExists(base + "/missing/a.pom", null, null));
            assertThrows(IOException.class, () -> GitHubPackagesClient.artifactExists(base + "/error/a.pom", null, null));
            assertFalse(GitHubPackagesClient.checkArtifactExists(base + "/error/a.pom", null, null), "공개 메서드는 기존 동작 유지");

            List<MavenPublishStrategy.ArtifactInfo> jar = List.of(new MavenPublishStrategy.ArtifactInfo(null, "jar"));
            assertTrue(MavenPublishStrategy.shouldPublish(base + "/missing", "a", "1.0", jar, null, null).shouldPublish);
            assertFalse(MavenPublishStrategy.shouldPublish(base + "/ok", "a", "1.0", jar, null, null).shouldPublish);
            // 예전에는 500 도 "POM 없음 → 전체 배포"로 판단했다
            assertThrows(GradleException.class,
                    () -> MavenPublishStrategy.shouldPublish(base + "/error", "a", "1.0", jar, null, null));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("artifactTestClassNames 에 단순 이름이 같은 클래스가 있어도 태스크 이름이 겹치지 않는다")
    void sameSimpleNamesGetDistinctTasks() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        project.getPluginManager().apply("java");
        project.getExtensions().getExtraProperties().set("artifactTestClassNames", List.of("a.Smoke", "b.Smoke", "c.Other"));
        org.gradle.api.Task shadow = project.getTasks().create("fakeShadowJar");
        java.lang.reflect.Method register = S2BuildUtils.class.getDeclaredMethod("registerTestArtifactTask", Project.class,
                org.gradle.api.Task.class);
        register.setAccessible(true);
        register.invoke(null, project, shadow);
        assertNotNull(project.getTasks().findByName("testArtifact_a_Smoke"));
        assertNotNull(project.getTasks().findByName("testArtifact_b_Smoke"));
        assertNotNull(project.getTasks().findByName("testArtifact_Other"), "겹치지 않는 이름은 그대로");
    }

    @Test
    @DisplayName("문자열이 아닌 프로젝트 속성(REPO_BASE_URL 등)도 ClassCastException 없이 읽는다")
    void nonStringPropertiesAreRead() {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        project.getGradle().getStartParameter().setTaskNames(List.of("publish"));
        project.getExtensions().getExtraProperties().set("REPO_BASE_URL", java.net.URI.create("https://example.invalid/x"));
        project.getExtensions().getExtraProperties().set("GITHUB_TOKEN", new StringBuilder("token"));
        assertFalse(S2BuildUtils.determineSourceJarStatus(project), "GitHub 주소가 아니면 공개로 보고 소스 JAR 를 만들지 않음");
    }

    @Test
    @DisplayName("updateServletImports 는 지정한 방향으로 import·import static 만 바꾸고 줄바꿈을 유지한다")
    void servletImportsFollowTheGivenDirection() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        Path src = Files.createDirectories(dir.resolve("src/main/java/demo"));
        Path a = src.resolve("A.java");
        Files.writeString(a, "package demo;\r\n"
                + "import javax.servlet.http.HttpServletRequest;\r\n"
                + "import static javax.servlet.http.HttpServletResponse.SC_OK;\r\n"
                + "public class A { String note = \"javax.servlet.x\"; javax.servlet.Filter f; }\r\n");
        Path b = Files.writeString(src.resolve("B.java"), "package demo;\nimport java.util.List;\nclass B {}\n");
        long bModified = Files.getLastModifiedTime(b).toMillis();

        S2BuildUtils.updateServletImports(project, "src/main/java", true);
        String converted = Files.readString(a);
        assertEquals("package demo;\r\n"
                + "import jakarta.servlet.http.HttpServletRequest;\r\n"
                + "import static jakarta.servlet.http.HttpServletResponse.SC_OK;\r\n"
                + "public class A { String note = \"javax.servlet.x\"; javax.servlet.Filter f; }\r\n", converted,
                "import 줄만 바뀌고 CRLF 는 그대로");
        assertEquals(bModified, Files.getLastModifiedTime(b).toMillis(), "바뀔 것이 없는 파일은 쓰지 않는다");

        S2BuildUtils.updateServletImports(project, "src/main/java", false);
        assertTrue(Files.readString(a).contains("import javax.servlet.http.HttpServletRequest;\r\n"), "반대 방향도 동작");
        assertTrue(Files.readString(a).contains("import static javax.servlet.http.HttpServletResponse.SC_OK;"));
    }
}
