package com.edge.pulse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Integration test — requires running PostgreSQL and Redis. Run via: docker compose up")
class PulseApplicationTests {

    @Test
    void contextLoads() {
    }

}
