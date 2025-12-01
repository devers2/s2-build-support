package kr.s2.buildsupport;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * GitHub Packages에 아티팩트가 이미 존재하는지 확인
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
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Basic 인증 설정 (필요한 경우)
            if (user != null && token != null) {
                String auth = user + ":" + token;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            int responseCode = connection.getResponseCode();
            return responseCode == 200;

        } catch (IOException e) {
            // 네트워크 오류 또는 URL 오류 시 존재하지 않는 것으로 처리
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * GitHub API를 호출하여 리포지토리가 비공개인지 확인
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
        // 예: "https://maven.pkg.github.com/Placelink-HUB/libs-repo" -> "Placelink-HUB/libs-repo"
        Pattern pattern = Pattern.compile("github\\.com/([^/]+)/([^/]+)");
        Matcher matcher = pattern.matcher(repoBaseUrl);

        if (!matcher.find()) {
            System.err.println("⚠️  REPO_BASE_URL 형식이 올바르지 않습니다: " + repoBaseUrl);
            return false;
        }

        String repoOwner = matcher.group(1);
        String repoName = matcher.group(2);

        HttpURLConnection connection = null;
        try {
            // GitHub REST API v3 엔드포인트 구성
            String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName;
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            // 인증 토큰 설정 (Private 리포지토리 접근 시 필요)
            if (githubToken != null) {
                connection.setRequestProperty("Authorization", "token " + githubToken);
            }

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                // JSON 응답 파싱 (간단한 문자열 검색 사용)
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream(), "UTF-8")
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // "private":true 또는 "private": true 패턴 찾기
                boolean isPrivate = response.toString().contains("\"private\":true") ||
                        response.toString().contains("\"private\": true");

                String visibility = isPrivate ? "비공개(Private)" : "공개 (Public)";
                System.out.println("✅ 리포지토리 공개 상태 확인: " + repoOwner + "/" + repoName + " -> " + visibility);

                return isPrivate;
            } else {
                System.err.println("⚠️  GitHub API 호출 실패 (HTTP " + responseCode + "). 공개 리포지토리로 간주합니다.");
                return false;
            }
        } catch (IOException e) {
            System.err.println("⚠️  리포지토리 공개 상태 확인 실패: " + e.getMessage() + ". 공개 리포지토리로 간주합니다.");
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
