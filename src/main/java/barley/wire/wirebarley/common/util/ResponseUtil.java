package barley.wire.wirebarley.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * HTTP 응답 재구성 유틸리티 클래스
 * 캐시된 응답 데이터를 ResponseEntity로 변환합니다.
 */
@Slf4j
public class ResponseUtil {

    private ResponseUtil() {

    }

    /**
     * 캐시된 응답을 ResponseEntity로 재구성
     *
     * @param responseBody 응답 본문 (JSON 문자열)
     * @param status       HTTP 상태 코드
     * @param contentType  Content-Type 헤더 값
     * @return 재구성된 ResponseEntity
     */
    public static ResponseEntity<String> reconstructResponse(
            String responseBody,
            int status,
            String contentType) {

        HttpHeaders headers = new HttpHeaders();
        if (contentType != null && !contentType.isBlank()) {
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        } else {
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        }

        return ResponseEntity
                .status(HttpStatus.valueOf(status))
                .headers(headers)
                .body(responseBody);
    }

    /**
     * 기본 Content-Type (application/json)으로 응답 재구성
     *
     * @param responseBody 응답 본문
     * @param status       HTTP 상태 코드
     * @return 재구성된 ResponseEntity
     */
    public static ResponseEntity<String> reconstructResponse(String responseBody, int status) {
        return reconstructResponse(responseBody, status, "application/json");
    }
}
