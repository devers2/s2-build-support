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

import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S2BuildSupportPlugin 단위 테스트
 */
class S2BuildSupportPluginTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(S2BuildSupportPlugin.class);
    }

    @Test
    @DisplayName("S2BuildSupportPlugin이 정상적으로 프로젝트에 적용된다")
    void pluginAppliesSuccessfully() {
        assertTrue(project.getPlugins().hasPlugin(S2BuildSupportPlugin.class));
        assertTrue(project.getPlugins().hasPlugin("io.github.devers2.buildsupport"));
    }

    @Test
    @DisplayName("JavaCompile 태스크에 UTF-8 인코딩이 강제 설정된다")
    void enforceUtf8EncodingOnJavaCompile() {
        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        assertEquals("UTF-8", compileJava.getOptions().getEncoding(),
                "JavaCompile의 encoding은 UTF-8이어야 한다");
    }

    @Test
    @DisplayName("JavaCompile 태스크에 -parameters 컴파일러 옵션이 기본 적용된다")
    void configureCommonCompilerArgsParameters() {
        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        assertTrue(compileJava.getOptions().getCompilerArgs().contains("-parameters"),
                "컴파일러 인자에 -parameters가 포함되어야 한다");
    }

    @Test
    @DisplayName("compileOnlyInternal Configuration이 생성되고 compileOnly를 확장한다")
    void compileOnlyInternalConfigurationCreated() {
        var configuration = project.getConfigurations().findByName("compileOnlyInternal");
        assertNotNull(configuration, "compileOnlyInternal configuration이 생성되어야 한다");
    }
}
