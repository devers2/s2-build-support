package kr.s2.buildsupport;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class S2BuildSupportPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Plugin logic here
        project.getLogger().lifecycle("S2BuildSupportPlugin applied");

        // 빌드 일관성 검증: Gradle 실제 실행 버전과 설정된 목표 버전이 일치하는지 확인하여 사용자에게 안내한다.
        S2BuildUtils.checkGradleConsistency(project);

        // 소비자 프로젝트의 인코딩을 UTF-8로 강제
        S2BuildUtils.enforceUtf8Encoding(project);
    }
}
