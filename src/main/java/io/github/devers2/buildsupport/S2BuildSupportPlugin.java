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

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin for common S2 build support.
 * <p>
 * This plugin integrates core build utilities for S2 projects, including
 * automatic UTF-8 enforcement and Gradle version consistency checks.
 * </p>
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * S2 프로젝트 공통 빌드 지원을 위한 Gradle 플러그인입니다.
 * <p>
 * UTF-8 인코딩 강제 설정, Gradle 버전 정합성 검사 등 S2 프로젝트 빌드에 필요한
 * 핵심 유틸리티 로직을 프로젝트에 통합합니다.
 * </p>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 */
public class S2BuildSupportPlugin implements Plugin<Project> {

    /**
     * Applies the S2 build support plugin to the specified project.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 지정된 프로젝트에 S2 빌드 지원 플러그인을 적용합니다.
     *
     * @param project The Gradle project instance | Gradle 프로젝트 객체
     */
    @Override
    public void apply(Project project) {
        // Plugin logic here
        if (S2BuildUtils.isKorean()) {
            project.getLogger().lifecycle("S2BuildSupportPlugin 플러그인이 적용되었습니다.");
        } else {
            project.getLogger().lifecycle("S2BuildSupportPlugin applied");
        }

        // 빌드 일관성 검증: Gradle 실제 실행 버전과 설정된 목표 버전이 일치하는지 확인하여 사용자에게 안내한다.
        S2BuildUtils.checkGradleConsistency(project);

        // 소비자 프로젝트의 인코딩을 UTF-8로 강제
        S2BuildUtils.enforceUtf8Encoding(project);

        // 소비자 프로젝트에 안전한 컴파일러 옵션(-parameters 등)을 기본 적용
        S2BuildUtils.configureCommonCompilerArgs(project);
    }
}
