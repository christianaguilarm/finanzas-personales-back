package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.PropuestaAsistente;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropuestaAsistenteSchemaTest {
    @Test
    void scriptCreaEsquemaCompatibleConValidateYConservaDatosAlRepetirse() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var script = new ResourceDatabasePopulator(new FileSystemResource("sql/V2__propuesta_asistente.sql"));
        script.execute(dataSource);
        UUID id = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var insert = connection.prepareStatement("""
                     INSERT INTO propuesta_asistente (id, user_id, contenido, expira_en)
                     VALUES (?, 7, '{}', CURRENT_TIMESTAMP)
                     """)) {
            insert.setObject(1, id);
            insert.executeUpdate();
        }
        script.execute(dataSource);
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(PropuestaAsistente.class)
                    .getMetadataBuilder().applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                    .build();
            try (var sessionFactory = metadata.buildSessionFactory();
                 var session = sessionFactory.openSession()) {
                var propuesta = session.find(PropuestaAsistente.class, id);
                assertEquals(7L, propuesta.getUserId());
                assertEquals("{}", propuesta.getContenido());
                assertTrue(propuesta.getExpiraEn() != null);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
