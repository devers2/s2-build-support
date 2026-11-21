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
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.tools.ToolProvider;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Relocation must not move the packages of {@code api} dependencies, even when they share the relocated prefix with an
 * {@code implementation} dependency.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 재배치(relocation)는 {@code implementation} 의존성과 같은 접두어를 쓰는 {@code api} 의존성의 패키지를 옮기지 않아야 합니다.
 */
class ShadowRelocationTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("api 의존성 패키지는 impl 의존성과 접두어(a.b)가 같아도 재배치하지 않는다")
    void apiPackagesSharingThePrefixAreNotRelocated() throws Exception {
        Path repo = dir.resolve("repo");
        module(repo, "api", "a.b.api", "Bar");
        module(repo, "impl", "a.b.impl", "Foo");

        Path project = Files.createDirectories(dir.resolve("demo"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'com.gradleup.shadow'
                    id 'io.github.devers2.buildsupport'
                }
                group = 'demo'
                version = '1.0'
                repositories { maven { url = uri('%s') } }
                ext.shadedPackagePrefix = 'x.shaded'
                dependencies {
                    api 'test:api:1.0'
                    implementation 'test:impl:1.0'
                }
                io.github.devers2.buildsupport.S2BuildUtils.configureProject(project)
                """.formatted(repo.toUri()));
        Path src = Files.createDirectories(project.resolve("src/main/java/demo"));
        Files.writeString(src.resolve("Use.java"),
                "package demo; public class Use { public a.b.api.Bar bar; a.b.impl.Foo foo; }");

        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("shadowJar", "--stacktrace").build();

        File jar = project.resolve("build/libs/demo-1.0.jar").toFile();
        assertTrue(jar.isFile(), result.getOutput());
        try (JarFile zip = new JarFile(jar)) {
            assertNotNull(zip.getEntry("x/shaded/a/b/impl/Foo.class"), "impl 은 재배치");
            assertNotNull(zip.getEntry("a/b/api/Bar.class"), "api 는 원래 패키지 그대로");
            String use = new String(zip.getInputStream(zip.getEntry("demo/Use.class")).readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            assertTrue(use.contains("x/shaded/a/b/impl/Foo"), "impl 참조는 재배치된 이름");
            assertTrue(use.contains("a/b/api/Bar") && !use.contains("x/shaded/a/b/api/Bar"),
                    "api 참조는 원래 이름이어야 사용하는 쪽에서 깨지지 않는다");
        }
    }

    @Test
    @DisplayName("from(components.java) Publication 도 Shadow 배포 모드로 배포된다 (재배치 jar, api 만 담은 POM, module metadata 없음)")
    void shadedPublicationFromTheJavaComponentPublishes() throws Exception {
        Path repo = dir.resolve("repo");
        module(repo, "api", "a.b.api", "Bar");
        module(repo, "impl", "a.b.impl", "Foo");
        Path out = dir.resolve("out");

        Path project = Files.createDirectories(dir.resolve("demo"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                    id 'com.gradleup.shadow'
                    id 'io.github.devers2.buildsupport'
                }
                group = 'demo'
                version = '1.0'
                repositories { maven { url = uri('%s') } }
                ext.shadedPackagePrefix = 'x.shaded'
                dependencies {
                    api 'test:api:1.0'
                    implementation 'test:impl:1.0'
                }
                java { withSourcesJar(); withJavadocJar() } // s2-util(configureLibraryPublishing)과 같은 구성
                publishing {
                    publications { mavenJava(MavenPublication) { from components.java } }
                    repositories { maven { name = 'out'; url = uri('%s') } }
                }
                io.github.devers2.buildsupport.S2BuildUtils.configureProject(project)
                """.formatted(repo.toUri(), out.toUri()));
        Path src = Files.createDirectories(project.resolve("src/main/java/demo"));
        Files.writeString(src.resolve("Use.java"),
                "package demo; public class Use { public a.b.api.Bar bar; a.b.impl.Foo foo; }");

        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("publishMavenJavaPublicationToOutRepository", "--stacktrace").build();

        Path published = out.resolve("demo/demo/1.0");
        try (JarFile zip = new JarFile(published.resolve("demo-1.0.jar").toFile())) {
            assertNotNull(zip.getEntry("x/shaded/a/b/impl/Foo.class"), result.getOutput());
            assertNull(zip.getEntry("a/b/api/Bar.class"), "배포 모드에서 api 는 jar 에 넣지 않는다");
        }
        String pom = Files.readString(published.resolve("demo-1.0.pom"));
        assertTrue(pom.contains("<artifactId>api</artifactId>"), pom);
        assertFalse(pom.contains("<artifactId>impl</artifactId>"), "재배치되어 들어간 impl 은 POM 에 없어야 한다: " + pom);
        assertFalse(Files.exists(published.resolve("demo-1.0.module")), "component 기준 메타데이터는 배포하지 않는다");
    }

    @Test
    @DisplayName("한 단계짜리 패키지(org)의 의존성은 그 패키지 클래스만 재배치한다 (JDK 의 org.w3c 참조는 그대로)")
    void singleSegmentPackagesRelocateOnlyTheirOwnClasses() throws Exception {
        Path repo = dir.resolve("repo");
        module(repo, "flat", "org", "Top");

        Path project = Files.createDirectories(dir.resolve("demo"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'com.gradleup.shadow'
                    id 'io.github.devers2.buildsupport'
                }
                group = 'demo'
                version = '1.0'
                repositories { maven { url = uri('%s') } }
                ext.shadedPackagePrefix = 'x.shaded'
                dependencies { implementation 'test:flat:1.0' }
                io.github.devers2.buildsupport.S2BuildUtils.configureProject(project)
                """.formatted(repo.toUri()));
        Path src = Files.createDirectories(project.resolve("src/main/java/demo"));
        Files.writeString(src.resolve("Use.java"),
                "package demo; public class Use { org.Top top; public org.w3c.dom.Node node; }");

        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("shadowJar", "--stacktrace").build();

        try (JarFile zip = new JarFile(project.resolve("build/libs/demo-1.0.jar").toFile())) {
            assertNotNull(zip.getEntry("x/shaded/org/Top.class"), result.getOutput());
            String use = new String(zip.getInputStream(zip.getEntry("demo/Use.class")).readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            assertTrue(use.contains("x/shaded/org/Top"), "의존성 클래스는 재배치");
            assertTrue(use.contains("org/w3c/dom/Node") && !use.contains("x/shaded/org/w3c"),
                    "JDK 의 org.w3c 참조까지 옮기면 실행 중에 클래스를 찾지 못한다");
        }
    }

    /** 클래스 하나를 담은 jar 와 pom 을 Maven 형식 저장소에 만든다 */
    private void module(Path repo, String name, String pkg, String cls) throws Exception {
        Path src = Files.createDirectories(dir.resolve("src-" + name + "/" + pkg.replace('.', '/')));
        Path java = src.resolve(cls + ".java");
        Files.writeString(java, "package " + pkg + "; public class " + cls + " {}");
        Path classes = Files.createDirectories(dir.resolve("classes-" + name));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(),
                java.toString()));

        Path moduleDir = Files.createDirectories(repo.resolve("test/" + name + "/1.0"));
        String entry = pkg.replace('.', '/') + "/" + cls + ".class";
        try (ZipOutputStream zip = new ZipOutputStream(
                new FileOutputStream(moduleDir.resolve(name + "-1.0.jar").toFile()))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(Files.readAllBytes(classes.resolve(entry)));
            zip.closeEntry();
        }
        Files.writeString(moduleDir.resolve(name + "-1.0.pom"), """
                <project><modelVersion>4.0.0</modelVersion>
                <groupId>test</groupId><artifactId>%s</artifactId><version>1.0</version></project>
                """.formatted(name));
    }
}
