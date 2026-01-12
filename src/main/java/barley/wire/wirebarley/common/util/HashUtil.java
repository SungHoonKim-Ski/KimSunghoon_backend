package barley.wire.wirebarley.common.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 해시 계산 유틸리티 클래스
 * SHA-256 알고리즘을 사용하여 문자열의 해시값을 생성합니다.
 */
@Slf4j
public class HashUtil {

    private static final String ALGORITHM = "SHA-256";

    private HashUtil() {
        // 유틸리티 클래스, 인스턴스 생성 방지
    }

    /**
     * 주어진 문자열의 SHA-256 해시값을 16진수 문자열로 반환
     *
     * @param input 해시를 계산할 입력 문자열
     * @return 16진수 형식의 해시값, 실패 시 null
     */
    public static String calculateSHA256(String input) {
        if (input == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.warn("SHA-256 해시 계산 실패", e);
            return null;
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     *
     * @param bytes 변환할 바이트 배열
     * @return 16진수 문자열
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
