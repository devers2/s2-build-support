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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Client class for communicating with GitHub Packages.
 * <p>
 * This class handles HTTP communication with the GitHub Packages API to provide
 * functionality for checking artifact existence and repository visibility.
 * </p>
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * GitHub Packages와의 통신을 담당하는 클라이언트 클래스입니다.
 * <p>
 * GitHub Packages API와의 HTTP 통신을 처리하여 아티팩트 존재 여부 확인 및
 * 리포지토리 공개 상태(Public/Private) 확인 기능을 제공합니다.
 * </p>
 *
 * <b>Key Features (주요 기능)</b>
 * <ul>
 * <li><b>Artifact Existence Check:</b> Uses HTTP HEAD requests to verify if an artifact exists.</li>
 * <li><b>Repository Visibility Check:</b> Calls GitHub API to determine if a repository is private.</li>
 * </ul>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 * @see MavenPublishStrategy
 */
public class GitHubPackagesClient {

    private static final Logger logger = Logging.getLogger(GitHubPackagesClient.class);

    // GitHub API 관련 상수
    private static final String GITHUB_API_BASE_URL = "https://api.github.com/repos/";
    private static final String GITHUB_API_ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/]+)");

    // HTTP 타임아웃 설정 (밀리초)
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    /**
     * Checks if an artifact already exists in GitHub Packages.
     * <p>
     * Performs an HTTP HEAD request to verify existence. A 200 response code
     * indicates the artifact exists.
     * </p>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * GitHub Packages에 아티팩트가 이미 존재하는지 확인합니다.
     * <p>
     * HTTP HEAD 요청을 통해 아티팩트의 존재 여부를 확인하며, 응답 코드가 200인 경우 존재하는 것으로 판단합니다.
     * </p>
     *
     * @param urlString The URL of the artifact to check | 확인할 아티팩트의 URL
     * @param user      GitHub username for authentication | GitHub 사용자명 (인증용)
     * @param token     GitHub token for authentication | GitHub 토큰 (인증용)
     * @return {@code true} if artifact exists | 아티팩트 존재 시 true
     */
    public static boolean checkArtifactExists(String urlString, String user, String token) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("HEAD");
                applyCommonSettings(connection);

                // Basic 인증 설정 (필요한 경우)
                if (user != null && token != null) {
                    applyBasicAuth(connection, user, token);
                }

                int responseCode = connection.getResponseCode();
                return responseCode == 200;

            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            // 네트워크 오류 또는 URL 오류 시 존재하지 않는 것으로 처리
            if (S2BuildUtils.isKorean()) {
                logger.debug("아티팩트 존재 확인 실패: {}", e.getMessage());
            } else {
                logger.debug("Failed to verify artifact existence: {}", e.getMessage());
            }
            return false;
        }
    }

    /**
     * Checks if a repository is private via GitHub API.
     * <p>
     * Queries repository metadata and checks the "private" field value.
     * </p>
     *
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * GitHub API를 호출하여 리포지토리가 비공개인지 확인합니다.
     * <p>
     * GitHub REST API를 통해 리포지토리 메타데이터를 조회하고 "private" 필드 값을 확인합니다.
     * </p>
     *
     * @param repoBaseUrl Repository base URL (e.g., https://maven.pkg.github.com/owner/repo) | 리포지토리 베이스 URL
     * @param githubToken GitHub token for API access | GitHub 토큰 (API 접근용)
     * @return {@code true} if repository is private | 비공개 리포지토리인 경우 true
     */
    public static boolean isRepoPrivate(String repoBaseUrl, String githubToken) {
        // REPO_BASE_URL에서 owner/repo 추출
        Matcher matcher = GITHUB_REPO_PATTERN.matcher(repoBaseUrl);

        if (!matcher.find()) {
            if (S2BuildUtils.isKorean()) {
                logger.warn("⚠️  REPO_BASE_URL 형식이 올바르지 않습니다: {}", repoBaseUrl);
            } else {
                logger.warn("⚠️  Invalid REPO_BASE_URL format: {}", repoBaseUrl);
            }
            return false;
        }

        String repoOwner = matcher.group(1);
        String repoName = matcher.group(2);

        try {
            // GitHub REST API v3 엔드포인트 구성
            String apiUrl = GITHUB_API_BASE_URL + repoOwner + "/" + repoName;
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", GITHUB_API_ACCEPT_HEADER);
                applyCommonSettings(connection);

                // 인증 토큰 설정 (Private 리포지토리 접근 시 필요)
                if (githubToken != null) {
                    connection.setRequestProperty("Authorization", "token " + githubToken);
                }

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    // JSON 응답 읽기
                    String responseBody = readResponseBody(connection);

                    // "private" 필드 값 확인 (공백 유무에 관계없이 매칭되도록 정규식 사용)
                    boolean isPrivate = Pattern.compile("\"private\"\\s*:\\s*true").matcher(responseBody).find();

                    if (S2BuildUtils.isKorean()) {
                        String visibility = isPrivate ? "비공개(Private)" : "공개 (Public)";
                        logger.lifecycle("✅ 리포지토리 공개 상태 확인: {}/{} → {}", repoOwner, repoName, visibility);
                    } else {
                        String visibility = isPrivate ? "Private" : "Public";
                        logger.lifecycle("✅ Repository visibility check: {}/{} → {}", repoOwner, repoName, visibility);
                    }

                    return isPrivate;
                } else {
                    if (S2BuildUtils.isKorean()) {
                        logger.warn("⚠️  GitHub API 호출 실패 (HTTP {}). 공개 리포지토리로 간주합니다.", responseCode);
                    } else {
                        logger.warn("⚠️  GitHub API call failed (HTTP {}). Treating as public repository.", responseCode);
                    }
                    return false;
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            if (S2BuildUtils.isKorean()) {
                logger.warn("⚠️  리포지토리 공개 상태 확인 실패: {}. 공개 리포지토리로 간주합니다.", e.getMessage());
            } else {
                logger.warn("⚠️  Failed to check repository visibility: {}. Treating as public repository.", e.getMessage());
            }
            return false;
        }
    }

    // ========================================================================
    // Private 헬퍼 메서드 (Helper Methods)
    // ========================================================================

    /**
     * Applies common HTTP settings (timeouts) to the connection.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@link HttpURLConnection}에 공통 설정(타임아웃 등)을 적용합니다.
     *
     * @param connection HTTP connection object | HTTP 연결 객체
     */
    private static void applyCommonSettings(HttpURLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
    }

    /**
     * Applies Basic Authentication header to the connection.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@link HttpURLConnection}에 Basic 인증 헤더를 추가합니다.
     *
     * @param connection HTTP connection object | HTTP 연결 객체
     * @param user       GitHub username | 사용자명
     * @param token      GitHub token | 토큰
     */
    private static void applyBasicAuth(HttpURLConnection connection, String user, String token) {
        String auth = user + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
    }

    /**
     * Reads the HTTP response body as a string.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * HTTP 응답 본문을 문자열로 읽습니다.
     *
     * @param connection HTTP connection object | HTTP 연결 객체
     * @return Response body as string | 응답 본문 문자열
     * @throws IOException If an I/O error occurs | 입출력 오류 발생 시
     */
    private static String readResponseBody(HttpURLConnection connection) throws IOException {
        // try-with-resources로 자동 리소스 정리
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
        )) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
