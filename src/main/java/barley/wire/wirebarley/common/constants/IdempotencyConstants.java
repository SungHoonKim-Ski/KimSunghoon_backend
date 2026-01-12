package barley.wire.wirebarley.common.constants;

import java.time.Duration;

/**
 * 멱등성 관련 상수
 */
public final class IdempotencyConstants {

    private IdempotencyConstants() {
    }

    public static final int TTL_MINUTES = 10;
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(TTL_MINUTES);
}
