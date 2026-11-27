package nl.idgis.gioimporter.config;

import nl.idgis.gioimporter.application.*;
import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcGeometryRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcInformationObjectRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcLocationRepository;
import nl.idgis.gioimporter.infrastructure.persistence.JdbcRegelingRepository;
import nl.idgis.gioimporter.infrastructure.xml.XmlGioReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Configuration
@Import(DatabaseConfig.class)
@PropertySource("classpath:application.properties")
@PropertySource(value = "file:${spring.config.location}", ignoreResourceNotFound = true)
public class GioImporterConfig {

    private final Environment environment;

    @Autowired
    public GioImporterConfig(Environment environment) {
        this.environment = environment;
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
                environment.getProperty("regeling.expression"),
                parseOntwerp(environment.getProperty("regeling.ontwerp", "false"))
        );
    }

    static boolean parseOntwerp(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        throw new IllegalArgumentException("ONTWERP moet true of false zijn: " + value);
    }

    @Bean
    public GeometryImportService geometryImportService(GeometryRepository geometryRepository) {
        return new GeometryImportService(geometryRepository);
    }

    @Bean
    public LocationImportService locationImportService(LocationRepository locationRepository,
            ImowIdentifierGenerator identifiers, Clock clock) {
        return new LocationImportService(locationRepository, identifiers, clock);
    }

    @Bean
    public GioImportTransaction gioImportTransaction(RegelingRepository regelingen,
            GeometryImportService geometryImporter, LocationImportService locationImporter,
            LocationRepository locations, InformationObjectRepository informationObjects,
            ImowIdentifierGenerator identifiers, Clock clock) {
        return new GioImportTransaction(regelingen, geometryImporter, locationImporter, locations,
                informationObjects, identifiers, clock);
    }

    @Bean
    public GioImportService gioImportService(GioReader reader, GioImportTransaction transaction) {
        return new GioImportService(reader, transaction);
    }
}
