package scheduler.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class GsonConfigTest {
    @Test
    void parseLocalTime_stripsEmbeddedWhitespace() {
        assertEquals(LocalTime.of(17, 0), GsonConfig.parseLocalTime("17\n        :00"));
        assertEquals(LocalTime.of(8, 0), GsonConfig.parseLocalTime(" 08:00 "));
    }
}
