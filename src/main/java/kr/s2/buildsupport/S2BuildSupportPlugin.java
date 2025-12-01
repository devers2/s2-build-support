package kr.s2.buildsupport;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class S2BuildSupportPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Plugin logic here
        project.getLogger().lifecycle("S2BuildSupportPlugin applied");
    }
}
