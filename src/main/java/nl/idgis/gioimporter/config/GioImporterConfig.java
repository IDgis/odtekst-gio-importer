package nl.idgis.gioimporter.config;

import nl.idgis.gioimporter.application.GioImportService;
import nl.idgis.gioimporter.application.ImowIdentifierGenerator;
import nl.idgis.gioimporter.application.ImportRequest;
import nl.idgis.gioimporter.application.LocationImportService;
import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcGeometryRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcInformationObjectRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcLocationRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcRegelingRepository;
import nl.idgis.gioimporter.infrastructure.xml.XmlGioReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Configuration
public class GioImporterConfig {

    private final Environment environment;

    @Autowired
    public GioImporterConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
        driverManagerDataSource.setDriverClassName("org.postgresql.Driver");
        driverManagerDataSource.setUrl(environment.getProperty("db.url"));
        driverManagerDataSource.setUsername(environment.getProperty("db.username"));
        driverManagerDataSource.setPassword(environment.getProperty("db.password"));

        return driverManagerDataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public GioReader gioReader() {
        return new XmlGioReader();
    }

    @Bean
    public RegelingRepository regelingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegelingRepository(jdbcTemplate);
    }

    @Bean
    public GeometryRepository geometryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGeometryRepository(jdbcTemplate);
    }

    @Bean
    public LocationRepository locationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcLocationRepository(jdbcTemplate);
    }

    @Bean
    public InformationObjectRepository informationObjectRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcInformationObjectRepository(jdbcTemplate);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ImowIdentifierGenerator imowIdentifierGenerator() {
        return new ImowIdentifierGenerator(UUID::randomUUID);
    }

    @Bean
    public ImportRequest importRequest() {
        return new ImportRequest(
                Path.of(Objects.requireNonNull(environment.getProperty("input.file"))),
                environment.getProperty("gio.name"),
                environment.getProperty("regeling.expression")
        );
    }

    @Bean
    public LocationImportService locationImportService(GeometryRepository geometryRepository,
            LocationRepository locationRepository, ImowIdentifierGenerator identifiers, Clock clock) {
        return new LocationImportService(geometryRepository, locationRepository, identifiers, clock);
    }

    @Bean
    public GioImportService gioImportService(GioReader reader, RegelingRepository regelingen,
            LocationImportService locationImporter, LocationRepository locations,
            InformationObjectRepository informationObjects, ImowIdentifierGenerator identifiers, Clock clock) {
        return new GioImportService(reader, regelingen, locationImporter, locations,
                informationObjects, identifiers, clock);
    }
}
