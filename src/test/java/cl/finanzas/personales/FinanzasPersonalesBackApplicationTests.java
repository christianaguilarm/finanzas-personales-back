package cl.finanzas.personales;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
class FinanzasPersonalesBackApplicationTests {

    // El arranque verifica Spring/JPA, sin descubrir claves remotas de Auth0.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}
