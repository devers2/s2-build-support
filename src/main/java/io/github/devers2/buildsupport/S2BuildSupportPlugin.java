/**
 * S2Util Library
 *
 * Copyright 2020 - 2026 devers2 (Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.devers2.buildsupport;

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
