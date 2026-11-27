package nl.idgis.gioimporter.config;

import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.importer.GioImporter;
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
    public GioImporter gioImporter(GioReader gioReader, RegelingRepository regelingRepository,
                                   GeometryRepository geometryRepository, LocationRepository locationRepository,
                                   InformationObjectRepository informationObjectRepository) {
        String inputFile = environment.getProperty("input.file");
        String gioName = environment.getProperty("gio.name");
        String regelingExpression = environment.getProperty("regeling.expression");

        return new GioImporter(gioReader, regelingRepository, geometryRepository, locationRepository,
                informationObjectRepository, inputFile, gioName, regelingExpression);
    }
}
