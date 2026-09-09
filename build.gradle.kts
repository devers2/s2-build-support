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

import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * [Version Catalog]
 * 의존성 및 플러그인 버전은 'gradle/libs.versions.toml' 파일에서 통합 관리
 * 별도 설정 없이 Gradle이 기본 경로(gradle/libs.versions.toml)를 자동으로 인식하여 'libs' 접근자로 제공
 */
plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = project.property("group").toString()
version = project.property("version").toString()

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    /**
     * [Java Toolchain]
     * 빌드 실행 환경(JAVA_HOME)과 프로젝트 컴파일 환경을 분리하는 현대적인 방식
     */
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
    // Maven Central 필수 요건: 소스 및 자바독 JAR 생성
    // [의도된 예외] 다른 s2-* 프로젝트는 S2BuildUtils.determineSourceJarStatus()로 조건부 생성하지만,
    // 이 프로젝트는 자기 자신을 빌드하는 중이라 아직 컴파일된 S2BuildUtils를 여기서 참조할 수 없다
    // (Central Portal Zip 번들링 중복과 동일한 부트스트래핑 문제). 이 프로젝트는 항상 CentralPortal로만
    // 배포되므로 무조건 생성이 오히려 안전하다 (건너뛰면 Central 검증에서 배포 자체가 거부됨).
    withSourcesJar()
    withJavadocJar()
}

// GPG 서명 설정 (Maven Central 필수)
signing {
    sign(publishing.publications)
}

dependencies {
    implementation(gradleApi())
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.0")
}

/*
 * [JAR 패키징 설정]
 * JAR 파일 루트에 README.md와 LICENSE 파일을 포함한다.
 */
tasks.jar {
    from(".") {
        include("README.md")
        include("README.ko.md")
        include("LICENSE")
    }
}


/*
 * [Gradle 플러그인 배포 - Maven Central]
 *
 * Tasks → publishing → publishAllPublicationsToCentralPortalRepository 태스크 실행
 *
 * [사용 방법]
 *
 * 1. settings.gradle.kts에 저장소 추가:
 *    pluginManagement {
 *        repositories {
 *            gradlePluginPortal()
 *            mavenCentral() // 중앙 저장소에서 플러그인을 찾도록 추가
 *        }
 *    }
 *
 * 2. build.gradle.kts에 플러그인 적용:
 *    plugins {
 *        id("io.github.devers2.buildsupport") version "0.1.0"
 *    }
 */
gradlePlugin {
    plugins {
        create("s2BuildSupportPlugin") {
            id = "io.github.devers2.buildsupport"
            implementationClass = "io.github.devers2.buildsupport.S2BuildSupportPlugin"
            displayName = "S2 Build Support"
            description = "S2 project build utilities"
        }
    }
}

publishing {
    publications {
        // 모든 출판물에 공통적으로 POM 메타데이터를 적용 (Central Portal 필수)
        withType<MavenPublication>().configureEach {
            pom {
                name = project.name
                description = "S2BuildSupport Plugin - A comprehensive utility library for Java"
                url = "https://github.com/devers2/s2-build-support"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "devers2"
                        name = "이승수"
                        email = "eseungsu.dev@gmail.com"
                        organization = "devers2"
                        organizationUrl = "https://github.com/devers2"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/devers2/s2-build-support.git"
                    developerConnection = "scm:git:ssh://github.com/devers2/s2-build-support.git"
                    url = "https://github.com/devers2/s2-build-support"
                }
            }
        }
    }
    repositories {
        // Central Portal 배포 설정
        maven {
            name = "CentralPortal"
            // Central Portal Zip Bundle Upload API (v1)
            // publishingType → AUTOMATIC : 자동 배포, USER_MANAGED : 사용자 관리 배포 (수동 승인/배포 필요 시)
            url = uri("https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED")
            credentials {
                username = project.findProperty("centralUsername")?.toString()
                password = project.findProperty("centralPassword")?.toString()
            }
        }
    }
}

/**
 * [Central Portal Publication Hack]
 * The Central Portal API (v1) requires a Zip bundle for uploads and does not support
 * individual PUT requests (which results in 404 Not Found).
 * This block hijacks the default publishing task to create and upload a Zip bundle instead.
 *
 * ⚠️ [Intentional duplication / 의도된 중복]
 * This is a Kotlin-DSL twin of `S2BuildUtils.configureCentralPortalPublishing()`
 * (src/main/java/io/github/devers2/buildsupport/S2BuildUtils.java). Every other S2 project
 * reuses that Java method via `S2BuildUtils.configureProject(project)`, but this project
 * (s2-build-support) cannot apply its own plugin to itself while it is still being built
 * (the compiled classes don't exist yet on the build script classpath) — a bootstrapping
 * chicken-and-egg problem. So this block re-implements the same bundling logic inline.
 * If you fix a bug or add a safety check in `configureCentralPortalPublishing()`, please
 * port the same change here (and vice versa) to avoid behavioral drift between the two.
 */
afterEvaluate {
    tasks.withType<PublishToMavenRepository>().configureEach {
        if (repository?.name != "CentralPortal") return@configureEach

        // 1. Clear default actions to prevent individual file PUTs (404 error)
        actions.clear()

        // 2. Ensure signing tasks for this publication are executed before upload
        val publication = this.publication
        dependsOn(tasks.withType<Sign>())

        // 실행 시점이 아닌 구성 시점에 값을 확보한다. (Kotlin DSL에서의 지연 평가 회피)
        val centralUploadUrl = repository!!.url.toString()
        val buildDirFile = project.layout.buildDirectory.asFile.get()
        val centralUser = project.findProperty("centralUsername")?.toString()?.trim()
        val centralPass = project.findProperty("centralPassword")?.toString()?.trim()

        doLast {
            val isKo = Locale.getDefault().language == "ko"
            // 한국어 로케일이면 첫 번째 메시지, 그 외에는 두 번째 메시지를 출력한다.
            fun say(ko: String, en: String) = println(if (isKo) ko else en)

            val pubName = publication.name
            say(
                "🚀 [중앙 포털] Zip 번들 업로드를 시작합니다 [$pubName]...",
                "🚀 [Central Portal] Starting Zip bundle upload [$pubName]..."
            )

            val bundleDir = File(buildDirFile, "distributions/central-bundle/$pubName")
            if (bundleDir.exists()) bundleDir.deleteRecursively()
            bundleDir.mkdirs()

            val pubVersion = publication.version
            val pubArtifactId = publication.artifactId
            val pubGroupId = publication.groupId
            // S2BuildUtils.configureCentralPortalPublishing()과 동일하게, 릴리즈 배포 시에는
            // 이전 SNAPSHOT 빌드 산출물이 libs 폴더에 잔존해 있어도 번들에 섞여 들어가지 않도록 걸러낸다.
            val isSnapshotVersion = pubVersion.contains("SNAPSHOT")

            val filesToBundle = mutableListOf<File>()

            // 1) JARs & Signatures (Only for publications that have JARs)
            if (pubName == "mavenJava" || pubName == "pluginMaven") {
                val libsDir = File(buildDirFile, "libs")
                if (libsDir.exists()) {
                    // Find main JAR, sources, javadoc and their signatures
                    val baseName = "$pubArtifactId-$pubVersion"
                    libsDir.listFiles()?.forEach { f ->
                        if (!isSnapshotVersion && f.name.contains("SNAPSHOT")) {
                            return@forEach
                        }
                        if (f.name.startsWith(baseName) && (f.name.endsWith(".jar") || f.name.endsWith(".asc"))) {
                            filesToBundle += f
                        }
                    }
                }
            }

            // 2) POM & Signature
            val pomDir = File(buildDirFile, "publications/$pubName")
            if (pomDir.exists()) {
                val pomFile = File(pomDir, "pom-default.xml")
                if (pomFile.exists()) {
                    val renamedPom = File(bundleDir, "$pubArtifactId-$pubVersion.pom")
                    Files.copy(pomFile.toPath(), renamedPom.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    filesToBundle += renamedPom

                    // Also look for signed POM
                    val pomAsc = File(pomDir, "pom-default.xml.asc")
                    if (pomAsc.exists()) {
                        val renamedPomAsc = File(bundleDir, "$pubArtifactId-$pubVersion.pom.asc")
                        Files.copy(pomAsc.toPath(), renamedPomAsc.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        filesToBundle += renamedPomAsc
                    }
                }
            }

            if (filesToBundle.isEmpty()) {
                say(
                    "⚠️ [중앙 포털] 번들링할 파일을 찾지 못했습니다 [$pubName]. 구성을 건너뜁니다.",
                    "⚠️ [Central Portal] No files found to bundle [$pubName]. Skipping."
                )
                return@doLast
            }

            // 3) Create Checksums & Verify Signatures
            val authenticatedFiles = mutableListOf<File>()
            val fileNames = filesToBundle.map { it.name }.toSet()

            filesToBundle.forEach { f ->
                if (f.name.endsWith(".asc")) {
                    authenticatedFiles += f
                    return@forEach
                }

                if (!fileNames.contains(f.name + ".asc")) {
                    say(
                        "⚠️ [중앙 포털] 서명(.asc) 파일이 없어 건너뜁니다: ${f.name}. Maven Central은 서명이 필수입니다.",
                        "⚠️ [Central Portal] Missing signature (.asc), skipping: ${f.name}. Signatures are required for Maven Central."
                    )
                    return@forEach
                }
                authenticatedFiles += f

                // Generate MD5 & SHA1 (Recommended/Required for Maven Central layout)
                val content = f.readBytes()
                mapOf("MD5" to 32, "SHA-1" to 40).forEach { (algo, len) ->
                    val digest = MessageDigest.getInstance(algo)
                    val hash = String.format("%0${len}x", BigInteger(1, digest.digest(content)))
                    val hashFile = File(bundleDir, "${f.name}.${algo.lowercase().replace("-", "")}")
                    hashFile.writeText(hash)
                    authenticatedFiles += hashFile
                }
            }

            val hasSignedArtifact = authenticatedFiles.any {
                !it.name.endsWith(".asc") && !it.name.endsWith(".md5") && !it.name.endsWith(".sha1")
            }
            if (!hasSignedArtifact) {
                say(
                    "⚠️ [중앙 포털] 서명된 아티팩트가 없어 업로드를 중단합니다 [$pubName].",
                    "⚠️ [Central Portal] No signed artifacts found, aborting upload [$pubName]."
                )
                return@doLast
            }

            // 4) Create Zip Bundle
            val bundleFileName = "$pubArtifactId-$pubVersion.zip"
            val zipFile = File(buildDirFile, "distributions/$bundleFileName")
            zipFile.parentFile.mkdirs()

            val groupPath = pubGroupId.replace('.', '/')
            val mavenPathPrefix = "$groupPath/$pubArtifactId/$pubVersion/"

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                authenticatedFiles.distinctBy { it.absolutePath }.forEach { file ->
                    zos.putNextEntry(ZipEntry(mavenPathPrefix + file.name))
                    zos.write(file.readBytes())
                    zos.closeEntry()
                }
            }
            say(
                "📦 [중앙 포털] Zip 번들 생성 완료: ${zipFile.absolutePath}",
                "📦 [Central Portal] Zip bundle created: ${zipFile.absolutePath}"
            )

            // 5) Upload
            if (centralUser.isNullOrEmpty() || centralPass.isNullOrEmpty()) {
                say(
                    "⚠️ [중앙 포털] 인증 정보가 없어 업로드를 건너뜁니다.",
                    "⚠️ [Central Portal] Missing credentials, skipping upload."
                )
                return@doLast
            }

            val boundary = "Boundary-" + System.currentTimeMillis()
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build()

            // Construct multipart body
            val header = "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"bundle\"; filename=\"$bundleFileName\"\r\n" +
                    "Content-Type: application/zip\r\n\r\n"
            val footer = "\r\n--$boundary--\r\n"

            val baos = ByteArrayOutputStream()
            baos.write(header.toByteArray(StandardCharsets.UTF_8))
            baos.write(zipFile.readBytes())
            baos.write(footer.toByteArray(StandardCharsets.UTF_8))

            val auth = Base64.getEncoder()
                .encodeToString("$centralUser:$centralPass".toByteArray(StandardCharsets.UTF_8))
            val request = HttpRequest.newBuilder()
                .uri(URI.create(centralUploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .header("Authorization", "Basic $auth")
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() in 200..299) {
                say(
                    "✅ [중앙 포털] 업로드 성공! [$pubName]",
                    "✅ [Central Portal] Upload successful! [$pubName]"
                )
            } else {
                say(
                    "❌ [중앙 포털] 업로드 실패 (HTTP ${response.statusCode()}): ${response.body()}",
                    "❌ [Central Portal] Upload failed (HTTP ${response.statusCode()}): ${response.body()}"
                )
            }
        }
    }
}
