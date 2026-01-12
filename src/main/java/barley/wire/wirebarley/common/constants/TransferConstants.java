package barley.wire.wirebarley.common.constants;

import java.math.BigDecimal;

/**
 * 송금 관련 상수
 */
public final class TransferConstants {

    private TransferConstants() {
    }

    // 수수료율
    public static final BigDecimal TRANSFER_FEE_RATE = new BigDecimal("0.01"); // 1%
    public static final BigDecimal EXCHANGE_FEE_RATE = new BigDecimal("0.005"); // 0.5%
 
    // 일일 한도
    public static final BigDecimal DAILY_WITHDRAW_LIMIT = new BigDecimal("1000000"); // 100만원
    public static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal("3000000"); // 300만원
}
