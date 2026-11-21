# Changelog

**English** | [한국어](./CHANGELOG.ko.md)

## [1.0.1] - Unreleased

### ⚠️ Changes in behavior

- **GitHub Packages smart publishing** (`MavenPublishStrategy`) stops the publish and reports why when it cannot check
  whether an artifact exists (a response other than 200/404, or a network error). Before, it took that as "missing" and
  tried to publish, failing with 409 Conflict or hiding the cause. `GitHubPackagesClient.checkArtifactExists` is unchanged.

### Fixed

- **Package relocation** also moved the packages of `api` dependencies sharing the first two segments with an
  `implementation` dependency (for example `org.apache.commons.lang3` under `org.apache`), so the api classes in the
  public API were renamed and broke consumers. Api packages are now excluded from relocation.
- **Shadow publishing mode** failed with `Cannot publish module metadata` for a `from(components.java)` publication
  (including `configureLibraryPublishing`) when sources/javadoc JARs exist. That publication now ships no Gradle Module
  Metadata, only the POM (api dependencies as compile). The check forbidding `from components.java`, which never actually ran, is removed.
- A project with `shadedPackagePrefix` and the Shadow plugin but without `maven-publish` failed at configuration
  (no `PublishingExtension`).
- GitHub Packages smart publishing built the existence-check path from the project's groupId instead of the publication's.
- Dependencies: Shadow plugin 9.6.1, Gradle Wrapper 9.9.1.
- **`S2TestLauncher` (`testArtifact`)** failed at once with `NoClassDefFoundError: org/gradle/api/JavaVersion` on a
  classpath without the Gradle API (it loaded `S2BuildUtils`). It now uses only the JDK.
- **`LibrariesPublisher`** registered every file as `.jar` whatever the scanned extension. It now keeps the file's real
  extension, and recognizes versions with a JDK update number (`1.8.0_422`, `1.8.0_422+8`) as documented.
- **Central Portal upload** has a connect timeout (60 seconds).
- **README example** had its `import` after `plugins {}`, which does not compile in the Kotlin DSL. It is now at the top.
- `S2BuildUtils` Javadoc: the `configureProject` steps (a `copyDependencies` step it never ran) and the README sync description now match the code.

## [1.0.0] - 2026-10-27

The first stable release. The API is fixed from here; later releases only fix bugs and keep up with new Gradle versions.

### ⚠️ Changes in behavior

- **`configureJavaCompatibility` compiles with `--release`.** When `releaseCompatibility` is older than the toolchain
  (for example JDK 21 building Java 17 bytecode), a call to a newer JDK API such as `List.getFirst()` is now a compile
  error. Before, only `-source`/`-target` were set, so such a call compiled and failed on the older runtime.
- **Central Portal publishing fails instead of looking successful** when the credentials are missing, an artifact or
  its signature (`.asc`) is missing, or the upload is rejected. Before, missing credentials only printed a warning, a
  file without a signature was silently left out, and (for this plugin's own publishing) a rejected upload was only
  printed.
- **The Central bundle holds exactly the publication's artifacts** (`artifactId-version[-classifier].ext`) and its POM,
  instead of every file in `build/libs` whose name contains the version.
- **Javadoc lists public and protected members only** (`-private` removed) and is titled with the project name and
  version instead of "S2Util API Documentation".
- **Removed `BuildVariantsTask`** (unused since builds stopped producing several variants).

### Fixed

- **Gradle 10:** settings such as `excludedSources`, `activeFeatures`, `skipPackaging`, `shadedPackagePrefix`,
  `buildFatJar` and `artifactTestClassNames` were read with `Project.hasProperty`/`property`, which looks them up in
  parent projects implicitly (deprecated, an error in Gradle 10). They are now read from the project's own extra
  properties, then the root project's, then Gradle properties (`gradle.properties`, `-P`).
- **Test and run encoding:** `-Dsun.jnu.encoding=UTF-8` (`configureTestDefaults`) and `-Dfile.encoding=UTF-8` for
  application run tasks never reached the JVM, because they were added to the copy `getJvmArgs()` returns. They now use
  `jvmArgs(...)` and `defaultCharacterEncoding`, and run tasks are covered even when the `application` plugin is
  applied later.
- **Source toggle never overwrites:** switching a feature renamed `.java` ↔ `.java.txt` without checking; on Linux the
  rename replaced an existing file, so an edited `X.java` could be overwritten by a stale `X.java.txt`. Now nothing is
  renamed when both exist, with a warning.
- **README sync** writes a file only when its content changed.
- **Signing** is no longer required for `publishToMavenLocal`.
- **`S2TestLauncher`** uses the `execute` subcommand of JUnit's console launcher from JUnit Platform 1.10 / 6.x.
- **`GitHubPackagesClient`** sends no empty `Authorization` header and no longer uses the deprecated `new URL(String)`.
- Debug output ("[Packaging Debug] ...") moved to the debug log level.
