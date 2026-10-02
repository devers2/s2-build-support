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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixes made for 1.0.0: settings without implicit parent lookup, {@code --release}, test encoding, safe source
 * toggling, Central bundles from the publication, and the JUnit console launcher arguments.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 1.0.0 수정 사항을 확인합니다: 부모 암묵 조회 없는 설정 조회, {@code --release}, 테스트 인코딩, 안전한 소스 토글, Publication 기준
 * Central 번들, JUnit 콘솔 런처 인자.
 */
class OneZeroFixesTest {

    @TempDir
    Path dir;

    @org.junit.jupiter.api.Test
    @DisplayName("설정은 자기 프로젝트 → 루트 extra → Gradle 속성 순으로 찾는다 (부모 암묵 조회 없음)")
    void settingsAreFoundWithoutImplicitParentLookup() {
        Project root = ProjectBuilder.builder().withProjectDir(dir.resolve("root").toFile()).build();
        Project child = ProjectBuilder.builder().withParent(root).withName("child").build();
        root.getExtensions().getExtraProperties().set("excludedSources", Set.of("a.java.txt"));
        root.getExtensions().getExtraProperties().set("skipPackaging", "true");
        child.getExtensions().getExtraProperties().set("activeFeatures", Set.of("x"));

        assertEquals(Set.of("a.java.txt"), S2BuildUtils.setting(child, "excludedSources"));
        assertEquals(Set.of("x"), S2BuildUtils.setting(child, "activeFeatures"));
        assertNull(S2BuildUtils.setting(child, "missing"));
        assertTrue(S2BuildUtils.isTrue(S2BuildUtils.setting(child, "skipPackaging")), "text \"true\" from -P counts");
        assertFalse(S2BuildUtils.isTrue(null));

        child.getExtensions().getExtraProperties().set("shadedPackagePrefix", "  ");
        assertNull(S2BuildUtils.shadedPackagePrefix(child), "blank means unset");
        child.getExtensions().getExtraProperties().set("shadedPackagePrefix", " a.b.shaded ");
        assertEquals("a.b.shaded", S2BuildUtils.shadedPackagePrefix(child));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("releaseCompatibility 로 --release 를 지정해 이전 버전에 없는 JDK API 를 막는다")
    void releaseIsSetSoNewerJdkApisDoNotCompile() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getExtensions().getExtraProperties().set("javaVersion", JavaVersion.VERSION_21);
        project.getExtensions().getExtraProperties().set("releaseCompatibility", JavaVersion.VERSION_17);
        S2BuildUtils.configureJavaCompatibility(project);
        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        assertEquals(17, compileJava.getOptions().getRelease().get());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("테스트 JVM 인코딩 설정이 실제로 반영된다 (getJvmArgs() 사본에 add 하지 않음)")
    void testEncodingIsApplied() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(S2BuildSupportPlugin.class);
        S2BuildUtils.configureTestDefaults(project);
        Test test = (Test) project.getTasks().getByName("test");
        assertEquals("UTF-8", test.getDefaultCharacterEncoding());
        S2BuildUtils.configureTestDefaults(project); // called twice | 두 번 호출
        List<String> args = test.getAllJvmArgs();
        assertEquals(1, args.stream().filter("-Dsun.jnu.encoding=UTF-8"::equals).count(), "added once: " + args);
        assertTrue(args.contains("-Dfile.encoding=UTF-8"), args.toString());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("소스 토글은 이미 있는 파일을 덮어쓰지 않는다")
    void sourceToggleNeverOverwrites() throws IOException {
        Project project = ProjectBuilder.builder().build();
        File java = Files.writeString(dir.resolve("A.java"), "edited").toFile();
        File txt = Files.writeString(dir.resolve("A.java.txt"), "stale").toFile();

        assertFalse(S2BuildUtils.toggleSource(project, txt, java));
        assertEquals("edited", Files.readString(java.toPath()), "the edited source is kept");
        assertTrue(txt.exists());

        Files.delete(java.toPath());
        assertTrue(S2BuildUtils.toggleSource(project, txt, java));
        assertEquals("stale", Files.readString(java.toPath()));
        assertFalse(txt.exists());
        assertFalse(S2BuildUtils.toggleSource(project, txt, java), "nothing to rename");
    }

    private MavenPublication publication(Project project) {
        project.getPlugins().apply("maven-publish");
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        return publishing.getPublications().create("mavenJava", MavenPublication.class, pub -> {
            pub.setGroupId("io.example");
            pub.setArtifactId("demo");
            pub.setVersion("1.0.0");
        });
    }

    private static File write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content, StandardCharsets.UTF_8).toFile();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Central 번들은 Publication 의 아티팩트만 Maven 이름으로 담고, 체크섬을 만든다")
    void centralBundleTakesOnlyThePublication() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        MavenPublication pub = publication(project);
        Path libs = dir.resolve("build/libs");
        File jar = write(libs.resolve("demo-1.0.0.jar"), "jar");
        write(libs.resolve("demo-1.0.0.jar.asc"), "sig");
        File sources = write(libs.resolve("other-name-sources.jar"), "src");
        write(libs.resolve("other-name-sources.jar.asc"), "sig");
        // Not part of the publication, unsigned: must not be picked up | Publication 밖의 서명 없는 파일은 담지 않음
        write(libs.resolve("demo-1.0.0-standard.jar"), "standard");
        pub.artifact(jar);
        pub.artifact(sources, a -> a.setClassifier("sources"));
        write(dir.resolve("build/publications/mavenJava/pom-default.xml"), "<project/>");
        write(dir.resolve("build/publications/mavenJava/pom-default.xml.asc"), "sig");

        File bundleDir = Files.createDirectories(dir.resolve("bundle")).toFile();
        List<File> files = S2BuildUtils.centralBundleFiles(pub, dir.resolve("build").toFile(), bundleDir);
        Set<String> names = files.stream().map(File::getName).collect(Collectors.toSet());

        assertEquals(Set.of(
                "demo-1.0.0.jar", "demo-1.0.0.jar.asc", "demo-1.0.0.jar.md5", "demo-1.0.0.jar.sha1",
                "demo-1.0.0-sources.jar", "demo-1.0.0-sources.jar.asc", "demo-1.0.0-sources.jar.md5",
                "demo-1.0.0-sources.jar.sha1",
                "demo-1.0.0.pom", "demo-1.0.0.pom.asc", "demo-1.0.0.pom.md5", "demo-1.0.0.pom.sha1"), names);
        assertEquals(32, Files.readString(bundleDir.toPath().resolve("demo-1.0.0.jar.md5")).length(), "hex MD5");
        assertEquals("jar", Files.readString(bundleDir.toPath().resolve("demo-1.0.0.jar")), "the publication's file");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("서명이 없는 아티팩트가 있으면 번들을 만들지 않고 실패한다")
    void aMissingSignatureFails() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        MavenPublication pub = publication(project);
        pub.artifact(write(dir.resolve("build/libs/demo-1.0.0.jar"), "jar"));
        write(dir.resolve("build/publications/mavenJava/pom-default.xml"), "<project/>");
        write(dir.resolve("build/publications/mavenJava/pom-default.xml.asc"), "sig");

        File bundleDir = Files.createDirectories(dir.resolve("bundle")).toFile();
        IOException e = assertThrows(IOException.class,
                () -> S2BuildUtils.centralBundleFiles(pub, dir.resolve("build").toFile(), bundleDir));
        assertTrue(e.getMessage().contains("demo-1.0.0.jar.asc"), e.getMessage());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("JUnit 콘솔 런처: 1.10 이상·6.x 는 execute 하위 명령, 그 밖에는 이전 형식")
    void consoleLauncherArgumentsFollowTheVersion() {
        assertEquals("execute", S2TestLauncher.consoleLauncherArgs("6.0.2", "a.B")[0]);
        assertEquals("execute", S2TestLauncher.consoleLauncherArgs("1.10.0", "a.B")[0]);
        assertEquals("--select-class", S2TestLauncher.consoleLauncherArgs("1.9.3", "a.B")[0]);
        assertEquals("--select-class", S2TestLauncher.consoleLauncherArgs(null, "a.B")[0]);
        assertTrue(List.of(S2TestLauncher.consoleLauncherArgs("6.0.2", "a.B")).contains("a.B"));
    }
}
