package cl.finanzas.personales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@EnableSpringDataWebSupport
@SpringBootApplication
public class FinanzasPersonalesBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanzasPersonalesBackApplication.class, args);
    }

}
