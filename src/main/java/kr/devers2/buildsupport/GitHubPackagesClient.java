package kr.devers2.buildsupport;

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
 * GitHub Packages와의 통신을 담당하는 클라이언트 클래스
 *
 * <p>
 * 이 클래스는 GitHub Packages API와의 HTTP 통신을 처리하여
 * 아티팩트 존재 여부 확인 및 리포지토리 공개 상태 확인 기능을 제공합니다.
 * </p>
 *
 * <p>
 * 주요 기능:
 * </p>
 * <ul>
 * <li>아티팩트 존재 여부 확인 (HEAD 요청)</li>
 * <li>리포지토리 공개 상태 확인 (GitHub API 호출)</li>
 * </ul>
 *
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
     * GitHub Packages에 아티팩트가 이미 존재하는지 확인한다.
     *
     * <p>
     * HTTP HEAD 요청을 통해 아티팩트의 존재 여부를 확인합니다.
     * 200 응답 코드가 반환되면 아티팩트가 존재하는 것으로 판단합니다.
     * </p>
     *
     * @param urlString 확인할 아티팩트의 URL
     * @param user      GitHub 사용자명 (인증이 필요한 경우)
     * @param token     GitHub 토큰 (인증이 필요한 경우)
     * @return true: 아티팩트 존재, false: 아티팩트 없음 또는 오류 발생
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
            logger.debug("아티팩트 존재 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * GitHub API를 호출하여 리포지토리가 비공개인지 확인한다.
     *
     * <p>
     * GitHub REST API를 통해 리포지토리의 메타데이터를 조회하고,
     * "private" 필드 값을 확인하여 공개 상태를 판단합니다.
     * </p>
     *
     * <p>
     * 리포지토리 URL 형식 예시:
     * {@code https://maven.pkg.github.com/owner/repo} 에서
     * {@code owner/repo} 부분을 추출하여 API 호출에 사용합니다.
     * </p>
     *
     * @param repoBaseUrl 리포지토리 베이스 URL (예: https://maven.pkg.github.com/owner/repo)
     * @param githubToken GitHub 토큰 (Private 리포지토리 조회 시 필요)
     * @return true: 비공개 리포지토리, false: 공개 리포지토리 또는 확인 실패
     */
    public static boolean isRepoPrivate(String repoBaseUrl, String githubToken) {
        // REPO_BASE_URL에서 owner/repo 추출
        Matcher matcher = GITHUB_REPO_PATTERN.matcher(repoBaseUrl);

        if (!matcher.find()) {
            logger.warn("⚠️  REPO_BASE_URL 형식이 올바르지 않습니다: {}", repoBaseUrl);
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

                    // "private":true 또는 "private": true 패턴 찾기
                    boolean isPrivate = responseBody.contains("\"private\":true") ||
                            responseBody.contains("\"private\": true");

                    String visibility = isPrivate ? "비공개(Private)" : "공개 (Public)";
                    logger.lifecycle("✅ 리포지토리 공개 상태 확인: {}/{} → {}", repoOwner, repoName, visibility);

                    return isPrivate;
                } else {
                    logger.warn("⚠️  GitHub API 호출 실패 (HTTP {}). 공개 리포지토리로 간주합니다.", responseCode);
                    return false;
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            logger.warn("⚠️  리포지토리 공개 상태 확인 실패: {}. 공개 리포지토리로 간주합니다.", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // Private 헬퍼 메서드 (Helper Methods)
    // ========================================================================

    /**
     * HttpURLConnection에 공통 설정을 적용한다.
     *
     * @param connection HTTP 연결 객체
     */
    private static void applyCommonSettings(HttpURLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
    }

    /**
     * HttpURLConnection에 Basic 인증 헤더를 추가한다.
     *
     * @param connection HTTP 연결 객체
     * @param user       사용자명
     * @param token      토큰
     */
    private static void applyBasicAuth(HttpURLConnection connection, String user, String token) {
        String auth = user + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
    }

    /**
     * HTTP 응답 본문을 문자열로 읽는다.
     *
     * @param connection HTTP 연결 객체
     * @return 응답 본문 문자열
     * @throws IOException 입출력 오류 발생 시
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
