# Changelog

**English** | [한국어](./CHANGELOG.ko.md)

## [1.0.0] - Unreleased

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
