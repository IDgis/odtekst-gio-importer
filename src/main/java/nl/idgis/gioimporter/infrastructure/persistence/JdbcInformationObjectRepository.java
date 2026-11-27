package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.application.port.InformationObjectRepository;
import nl.idgis.gioimporter.domain.GeographicContext;
import nl.idgis.gioimporter.domain.Gio;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;

public final class JdbcInformationObjectRepository implements InformationObjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInformationObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByFrbrExpression(String frbrExpression) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM bzk.informatieobjectversie
                    WHERE frbr_expression = ?
                )
                """;

        return jdbcTemplate.queryForObject(sql, Boolean.class, frbrExpression);
    }

    @Override
    public void insert(NewInformationObjectVersion version) {
        GeographicContext context = version.geographicContext();
        String sql = """
                INSERT INTO bzk.informatieobjectversie (frbr_work, frbr_expression, soort_work_id, regeling_id, eindverantwoordelijke_id, maker_id,
                formaat_informatieobject_id, naam, officiele_titel, publicatie_instructie_id, stop_schema_versie, achtergrond_verwijzing,
                achtergrond_actualiteit, nauwkeurigheid, locatie_id)
                VALUES (?, ?, 2056, ?, ?, ?, 1410, ?, ?, 3, '%s', ?, ?, ?, ?)
                """.formatted(Gio.SUPPORTED_SCHEMA_VERSION);

        jdbcTemplate.update(sql, ps -> {
            ps.setObject(1, version.frbrWork());
            ps.setObject(2, version.frbrExpression());
            ps.setObject(3, version.regelingId());
            ps.setObject(4, version.responsibleAuthorityId());
            ps.setObject(5, version.creatorId());
            ps.setObject(6, version.name());
            ps.setObject(7, version.frbrWork());
            ps.setObject(8, context.backgroundReference());
            ps.setObject(9, context.backgroundActuality() != null ? Date.valueOf(context.backgroundActuality()) : null);
            ps.setObject(10, context.accuracy() != null ? Integer.parseInt(context.accuracy()) : null);
            ps.setObject(11, version.locationGroupId());
        });
    }
}
