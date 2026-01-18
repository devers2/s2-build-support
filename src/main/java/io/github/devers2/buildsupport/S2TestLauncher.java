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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Shadow JAR 런타임 검증을 위한 스마트 런처
 * <p>
 * 사용자의 요구사항에 따라 다음과 같은 우선순위로 실행을 시도합니다:
 * 1. JUnit 5 의존성이 있으면 JUnit 5 테스트로 실행합니다.
 * 2. JUnit 5가 없으면 타겟 클래스의 public static void main(String[] args) 메서드를 찾아 직접 실행합니다.
 * 3. 둘 다 없으면 사용자에게 명확한 가이드(JUnit 추가 또는 main 구현)를 경고 로그로 출력합니다.
 * </p>
 */
public class S2TestLauncher {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java io.github.devers2.buildsupport.S2TestLauncher <targetClass> [args...]");
            System.exit(1);
        }

        String targetClassName = args[0];
        Class<?> targetClass = Class.forName(targetClassName);

        // 1. JUnit 5 ConsoleLauncher 존재 여부 확인 및 실행 시도
        try {
            Class<?> consoleLauncher = Class.forName("org.junit.platform.console.ConsoleLauncher");
            System.out.println("🚀 [Launcher] JUnit 5 의존성이 감지되어 테스트로 실행을 시작합니다.");

            Method junitMain = consoleLauncher.getMethod("main", String[].class);
            String[] junitArgs = { "--select-class", targetClassName, "--reports-dir", "build/test-results/testArtifact" };

            junitMain.invoke(null, (Object) junitArgs);
            return; // 성공적으로 JUnit 실행 시 종료
        } catch (ClassNotFoundException e) {
            // JUnit 5 없음 - 다음 단계로
        } catch (Exception e) {
            System.err.println("❌ [Launcher] JUnit 실행 중 오류 발생: " + e.getMessage());
        }

        // 2. main(String[]) 메서드 탐색 및 실행 시도
        try {
            Method mainMethod = targetClass.getMethod("main", String[].class);
            if (Modifier.isStatic(mainMethod.getModifiers())) {
                System.out.println("🚀 [Launcher] JUnit이 없으나 'main' 메서드가 감지되어 직접 실행합니다.");

                String[] passArgs = new String[args.length - 1];
                System.arraycopy(args, 1, passArgs, 0, passArgs.length);

                mainMethod.invoke(null, (Object) passArgs);
                return;
            }
        } catch (NoSuchMethodException ignored) {
            // main 메서드 없음
        }

        // 3. 최종 실패 시 가이드 경고 로그 출력
        System.err.println("\n" + "=".repeat(80));
        System.err.println("❌ [Launcher] 에러: '" + targetClassName + "' 클래스를 검증할 수 없습니다.");
        System.err.println("=".repeat(80));
        System.err.println("   검증을 완료하려면 다음 중 하나를 수행하세요:");
        System.err.println("   1. 클래스패스에 JUnit 5 런처를 추가하세요. (추천)");
        System.err.println("      -> build.gradle: testRuntimeOnly 'org.junit.platform:junit-platform-console'");
        System.err.println("   2. '" + targetClassName + "' 클래스에 'public static void main(String[] args)' 메서드를 구현하세요.");
        System.err.println("=".repeat(80) + "\n");

        System.exit(1);
    }
}
