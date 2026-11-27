package nl.idgis.gioimporter.config;

import nl.idgis.gioimporter.application.GioImportService;
import nl.idgis.gioimporter.application.GioImportTransaction;
import nl.idgis.gioimporter.application.ImportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GioImporterConfigTest {

    @Test
    void wiresProductionConfigurationWithoutConnectingToDatabase() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "input.file", "input.gml",
                    "gio.name", "group",
                    "regeling.expression", "regeling",
                    "db.url", "jdbc:postgresql://localhost:1/unused",
                    "db.username", "unused",
                    "db.password", "unused",
                    "spring.config.location", "nonexistent-test.properties")));
            context.register(GioImporterConfig.class);
            context.refresh();

            assertNotNull(context.getBean(GioImportService.class));
            assertTrue(AopUtils.isAopProxy(context.getBean(GioImportTransaction.class)));

            DataSource ds = context.getBean(DataSource.class);

            assertSame(ds, context.getBean(JdbcTemplate.class).getDataSource());
            assertSame(ds, context.getBean(DataSourceTransactionManager.class).getDataSource());
            assertEquals(new ImportRequest(Path.of("input.gml"), "group", "regeling"),
                    context.getBean(ImportRequest.class));
        }
    }
}
