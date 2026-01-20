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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Smart Test Launcher for verifying artifacts (Shadow JARs).
 * This launcher attempts to execute validation logic with the following priority:
 * <ol>
 * <li><b>JUnit 5:</b> If JUnit 5 is detected on the classpath, it runs the class
 * using {@code ConsoleLauncher}.</li>
 * <li><b>Standard Main:</b> If JUnit 5 is missing, it looks for a
 * {@code public static void main(String[] args)} method and executes it.</li>
 * <li><b>Diagnostic Guide:</b> If neither is found, it prints a detailed setup
 * guide to the error console.</li>
 * </ol>
 *
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 아티팩트(Shadow JAR 등)의 런타임 정합성을 검증하기 위한 스마트 런처입니다.
 * <p>
 * 실행 환경의 의존성 상태에 따라 다음과 같은 우선순위로 검증을 시도합니다:
 * <ol>
 * <li><b>JUnit 5:</b> 클래스패스에 JUnit 5가 있으면 {@code ConsoleLauncher}를 통해 테스트로 실행합니다.</li>
 * <li><b>Standard Main:</b> JUnit 5가 없으면 타겟 클래스의 {@code main} 메서드를 찾아 직접 실행합니다.</li>
 * <li><b>Diagnostic Guide:</b> 둘 다 없는 경우, 개발자에게 JUnit 추가 또는 main 구현을 안내하는 상세 가이드를 출력합니다.</li>
 * </ol>
 *
 *
 * <h2>Diagnostic Guide (진단 가이드)</h2>
 * If validation fails, ensure one of the following requirements is met:
 * <ul>
 * <li><b>JUnit 5:</b> Add {@code org.junit.platform:junit-platform-console} as a {@code testRuntimeOnly} dependency.</li>
 * <li><b>Main Method:</b> Implement {@code public static void main(String[] args)} in the target class.</li>
 * </ul>
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 검증에 실패할 경우 다음 중 하나의 요구 사항이 충족되었는지 확인하십시오:
 * <ul>
 * <li><b>JUnit 5:</b> {@code testRuntimeOnly} 의존성으로 {@code org.junit.platform:junit-platform-console}을 추가하십시오.</li>
 * <li><b>Main 메서드:</b> 대상 클래스에 {@code public static void main(String[] args)} 메서드를 구현하십시오.</li>
 * </ul>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 */
public class S2TestLauncher {
    /**
     * Application entry point for artifact validation.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 아티팩트 검증을 위한 애플리케이션 시작 지점입니다.
     *
     * @param args Command-line arguments. The first argument must be the target class name. | 명령행 인자. 첫 번째 인자는 타겟 클래스명이어야 합니다.
     * @throws Exception If target class loading or execution fails | 타겟 클래스 로드 또는 실행 실패 시 발생
     */
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
            if (S2BuildUtils.isKorean()) {
                System.out.println("🚀 [Launcher] JUnit 5 의존성이 감지되어 테스트로 실행을 시작합니다.");
            } else {
                System.out.println("🚀 [Launcher] JUnit 5 dependency detected. Starting as a test.");
            }

            Method junitMain = consoleLauncher.getMethod("main", String[].class);
            String[] junitArgs = { "--select-class", targetClassName, "--reports-dir", "build/test-results/testArtifact" };

            junitMain.invoke(null, (Object) junitArgs);
            return; // 성공적으로 JUnit 실행 시 종료
        } catch (ClassNotFoundException e) {
            // JUnit 5 없음 - 다음 단계로
        } catch (Exception e) {
            if (S2BuildUtils.isKorean()) {
                System.err.println("❌ [Launcher] JUnit 실행 중 오류 발생: " + e.getMessage());
            } else {
                System.err.println("❌ [Launcher] Error occurred while running JUnit: " + e.getMessage());
            }
        }

        // 2. main(String[]) 메서드 탐색 및 실행 시도
        try {
            Method mainMethod = targetClass.getMethod("main", String[].class);
            if (Modifier.isStatic(mainMethod.getModifiers())) {
                if (S2BuildUtils.isKorean()) {
                    System.out.println("🚀 [Launcher] JUnit이 없으나 'main' 메서드가 감지되어 직접 실행합니다.");
                } else {
                    System.out.println("🚀 [Launcher] JUnit not found, but 'main' method detected. Running directly.");
                }

                String[] passArgs = new String[args.length - 1];
                System.arraycopy(args, 1, passArgs, 0, passArgs.length);

                mainMethod.invoke(null, (Object) passArgs);
                return;
            }
        } catch (NoSuchMethodException ignored) {
            // main 메서드 없음
        }

        // 3. 최종 실패 시 가이드 경고 로그 출력
        boolean isKo = S2BuildUtils.isKorean();
        System.err.println("\n" + "=".repeat(80));
        if (isKo) {
            System.err.println("❌ [Launcher] 에러: '" + targetClassName + "' 클래스를 검증할 수 없습니다.");
        } else {
            System.err.println("❌ [Launcher] Error: Unable to validate class '" + targetClassName + "'.");
        }
        System.err.println("=".repeat(80));

        if (isKo) {
            System.err.println("   검증을 완료하려면 다음 중 하나를 수행하세요:");
            System.err.println("   1. 클래스패스에 JUnit 5 런처를 추가하세요. (추천)");
            System.err.println("      -> build.gradle: testRuntimeOnly 'org.junit.platform:junit-platform-console'");
            System.err.println("   2. '" + targetClassName + "' 클래스에 'public static void main(String[] args)' 메서드를 구현하세요.");
        } else {
            System.err.println("   To complete validation, perform one of the following:");
            System.err.println("   1. Add JUnit 5 launcher to the classpath. (Recommended)");
            System.err.println("      -> build.gradle: testRuntimeOnly 'org.junit.platform:junit-platform-console'");
            System.err.println("   2. Implement 'public static void main(String[] args)' in class '" + targetClassName + "'.");
        }
        System.err.println("=".repeat(80) + "\n");

        System.exit(1);
    }
}
