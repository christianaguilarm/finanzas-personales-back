package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.TelegramConexion;
import cl.finanzas.personales.model.TelegramPropuesta;
import cl.finanzas.personales.model.TelegramUpdate;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TelegramSchemaTest {
    @Test
    void migracionEsCompatibleConHibernateValidateYNoBorraDatosAlRepetirse() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var script = new ResourceDatabasePopulator(new FileSystemResource("sql/V3__telegram.sql"));
        script.execute(ds);
        try (var conn = ds.getConnection(); var statement = conn.createStatement()) {
            statement.executeUpdate("insert into telegram_conexion (user_id) values (7)");
        }
        script.execute(ds);
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
                .applySetting("hibernate.hbm2ddl.auto", "validate").build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(TelegramConexion.class)
                    .addAnnotatedClass(TelegramUpdate.class).addAnnotatedClass(TelegramPropuesta.class)
                    .getMetadataBuilder().applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy()).build();
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                assertEquals("[]", session.find(TelegramConexion.class, 7L).getHistorial());
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
