package Backend.BrechoAPI;

import Backend.BrechoAPI.security.JwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;














@TestConfiguration
public class WebSecuritySliceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtService jwtService(Clock clock) {
        return new JwtService("h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=", 1_800_000L, clock);
    }
}
