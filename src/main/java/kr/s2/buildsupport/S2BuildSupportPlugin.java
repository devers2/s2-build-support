package kr.s2.buildsupport;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class S2BuildSupportPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Plugin logic here
        project.getLogger().lifecycle("S2BuildSupportPlugin applied");

        // Gradle Wrapper 버전 설정을 중앙에서 관리
        S2BuildUtils.configureGradleWrapper(project);

        // 소비자 프로젝트의 인코딩을 UTF-8로 강제
        S2BuildUtils.enforceUtf8Encoding(project);
    }
}
