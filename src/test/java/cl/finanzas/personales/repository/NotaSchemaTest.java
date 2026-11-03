package cl.finanzas.personales.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotaSchemaTest {
    @Test
    void scriptManualEsRepetibleYEvitaAvisosDuplicados() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table app_user (id bigint primary key)");
            statement.execute("insert into app_user (id) values (7)");
        }
        var script = new ResourceDatabasePopulator(new FileSystemResource("sql/V5__notas_recordatorios.sql"));
        script.execute(ds);
        script.execute(ds);
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            statement.execute("insert into nota (id, user_id, texto, fecha_limite, resuelta) values (1, 7, 'Pagar luz', DATE '2026-11-05', false)");
            statement.execute("insert into nota_aviso (id, nota_id, fecha_aviso, vinculo_id, chat_id, entregado, fallos, proximo_intento) values ('00000000-0000-0000-0000-000000000001', 1, DATE '2026-11-03', '00000000-0000-0000-0000-000000000002', 123, false, 0, CURRENT_TIMESTAMP)");
            assertThrows(SQLException.class, () -> statement.execute("insert into nota_aviso (id, nota_id, fecha_aviso, vinculo_id, chat_id, entregado, fallos, proximo_intento) values ('00000000-0000-0000-0000-000000000003', 1, DATE '2026-11-03', '00000000-0000-0000-0000-000000000002', 123, false, 0, CURRENT_TIMESTAMP)"));
            statement.execute("delete from nota where id = 1");
            try (var result = statement.executeQuery("select count(*) from nota_aviso")) {
                result.next(); assertEquals(0, result.getInt(1));
            }
        }
    }
}
