package barley.wire.wirebarley.common.util;

import barley.wire.wirebarley.common.constants.TransferConstants;
import barley.wire.wirebarley.domain.account.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 통화별 금액 계산을 위한 유틸리티 클래스
 * 각 통화는 고유한 소수점 자릿수 요구사항을 가진다고 가정
 */
public class MoneyUtils {

    private static final int KRW_SCALE = 0; // 원화는 소수점 없음
    private static final int DEFAULT_SCALE = 2; // USD, JPY, EUR는 소수점 2자리

    private MoneyUtils() {

    }

    /**
     * 주어진 통화에 대한 스케일(소수점 자릿수) 반환
     */
    public static int getScale(Currency currency) {
        if (currency == Currency.KRW) {
            return KRW_SCALE;
        }
        return DEFAULT_SCALE;
    }

    /**
     * 통화에 따라 적절한 스케일과 라운딩 적용
     * HALF_UP 라운딩 모드 사용 (은행 반올림)
     */
    public static BigDecimal scale(BigDecimal amount, Currency currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(getScale(currency), RoundingMode.HALF_UP);
    }

    /**
     * 금액에 배율을 곱한 후 통화에 맞는 소수점 자리로 올림
     * 수수료 계산 시 사용
     */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal multiplier, Currency currency) {
        if (amount == null || multiplier == null || currency == null) {
            throw new IllegalArgumentException("Amount, multiplier, and currency must not be null");
        }
        return amount.multiply(multiplier).setScale(getScale(currency), RoundingMode.UP);
    }

    /**
     * 두 금액을 더하고 통화에 맞는 스케일 적용
     */
    public static BigDecimal add(BigDecimal amount1, BigDecimal amount2, Currency currency) {
        if (amount1 == null) {
            amount1 = BigDecimal.ZERO;
        }
        if (amount2 == null) {
            amount2 = BigDecimal.ZERO;
        }
        return amount1.add(amount2).setScale(getScale(currency), RoundingMode.HALF_UP);
    }

    /**
     * 두 금액을 빼고 통화에 맞는 스케일 적용
     */
    public static BigDecimal subtract(BigDecimal amount1, BigDecimal amount2, Currency currency) {
        if (amount1 == null) {
            amount1 = BigDecimal.ZERO;
        }
        if (amount2 == null) {
            amount2 = BigDecimal.ZERO;
        }
        return amount1.subtract(amount2).setScale(getScale(currency), RoundingMode.HALF_UP);
    }

    /**
     * 이체 수수료 계산 (원금의 1%)
     *
     * @param amount   원금
     * @param currency 통화
     * @return 수수료 (올림)
     */
    public static BigDecimal calculateTransferFee(BigDecimal amount, Currency currency) {
        return multiply(amount, TransferConstants.TRANSFER_FEE_RATE, currency);
    }

    /**
     * 환전 수수료 계산 (환전 금액의 0.5%)
     *
     * @param convertedAmount 환전 금액
     * @param currency        통화
     * @return 환전 수수료 (올림)
     */
    public static BigDecimal calculateExchangeFee(BigDecimal convertedAmount, Currency currency) {
        return multiply(convertedAmount, TransferConstants.EXCHANGE_FEE_RATE, currency);
    }

    /**
     * 수수료를 포함한 총 출금 금액 계산
     *
     * @param amount   원금
     * @param fee      수수료
     * @param currency 통화
     * @return 원금 + 수수료
     */
    public static BigDecimal calculateTotalWithdraw(BigDecimal amount, BigDecimal fee, Currency currency) {
        return add(amount, fee, currency);
    }

    /**
     * 환전 수수료를 차감한 최종 환전 금액 계산
     *
     * @param convertedAmount 환전 금액
     * @param exchangeFee     환전 수수료
     * @param currency        통화
     * @return 환전 금액 - 환전 수수료
     */
    public static BigDecimal calculateFinalConverted(BigDecimal convertedAmount, BigDecimal exchangeFee,
                                                     Currency currency) {
        return subtract(convertedAmount, exchangeFee, currency);
    }
}
