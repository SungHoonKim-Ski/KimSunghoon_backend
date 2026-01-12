package barley.wire.wirebarley.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimeUtil {
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static LocalDate nowDate() {
        return LocalDate.now(DEFAULT_ZONE_ID);
    }

    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now(DEFAULT_ZONE_ID);
    }

    public static LocalDateTime atStartOfDay() {
        return nowDate().atStartOfDay();
    }
}
