package io.github.devers2.buildsupport;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * License files of bundled dependencies are recognized and copied per dependency.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * Fat JAR 에 포함되는 의존성의 라이선스 파일을 알아보고 의존성별로 복사하는지 확인합니다.
 */
class DependencyNoticesTest {

    @Test
    void recognizesNoticeEntries() {
        for (var name : new String[] { "LICENSE", "LICENSE.txt", "NOTICE", "README.md", "README.ko.md",
                "licenses/NOTICE", "licenses/LICENSE-APACHE-2.0", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt",
                "META-INF/LICENSE.JZlib.txt" }) {
            assertTrue(S2BuildUtils.isDependencyNotice(name), name);
        }
        for (var name : new String[] { "com/example/LICENSE.class", "META-INF/MANIFEST.MF", "licenses/",
                "META-INF/versions/11/LICENSE", "docs/README.md", "LICENSES_OF_OTHERS/x" }) {
            assertFalse(S2BuildUtils.isDependencyNotice(name), name);
        }
    }

    @Test
    void copiesNoticesOfAJarKeepingPaths(@TempDir Path dir) throws Exception {
        var jar = dir.resolve("dep.jar");
        try (var zip = new ZipOutputStream(new FileOutputStream(jar.toFile()))) {
            for (var name : new String[] { "licenses/NOTICE", "META-INF/LICENSE.txt", "com/x/A.class", "../evil" }) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        var target = dir.resolve("out/dep");
        S2BuildUtils.copyDependencyNotices(jar.toFile(), target);
        assertEquals("licenses/NOTICE", Files.readString(target.resolve("licenses/NOTICE")));
        assertEquals("META-INF/LICENSE.txt", Files.readString(target.resolve("META-INF/LICENSE.txt")));
        assertFalse(Files.exists(target.resolve("com/x/A.class")));
        assertFalse(Files.exists(dir.resolve("out/evil")));
    }
}
