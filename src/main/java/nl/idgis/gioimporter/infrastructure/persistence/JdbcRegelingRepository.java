package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.port.RegelingRepository;
import nl.idgis.gioimporter.domain.RegelingContext;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRegelingRepository implements RegelingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRegelingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RegelingContext getContext(String expression) {
        int versionId = getRegelingVersieId(expression);
        int regelingId = getRegelingId(versionId);
        int authorityId = getEindverantwoordelijkeId(versionId);
        int creatorId = getMakerId(versionId);

        return new RegelingContext(regelingId, versionId, authorityId, creatorId,
                getEindverantwoordelijke(authorityId));
    }

    private int getRegelingVersieId(String expressionId) {
        String sql = "SELECT id FROM bzk.regelingversie WHERE frbr_expression = ?";
        String errorMessage = "Expression ID niet gevonden in de database: " + expressionId;

        Integer regelingVersieId = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setString(1, expressionId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (regelingVersieId == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return regelingVersieId;
    }

    private int getRegelingId(int regelingVersieId) {
        String sql = """
                SELECT r.id FROM bzk.regeling r
                JOIN bzk.regelingversie rv ON rv.regeling_id = r.id
                WHERE rv.id = ?
                """;
        String errorMessage = "Regeling niet gevonden in de database met regelingversie id: " + regelingVersieId;

        Integer regelingId = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (regelingId == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return regelingId;
    }

    private int getEindverantwoordelijkeId(int regelingVersieId) {
        String sql = "SELECT eindverantwoordelijke_id FROM bzk.regelingversie WHERE id = ?";
        String errorMessage = "Eindverantwoordelijke niet gevonden in de database";

        Integer eindverantwoordelijke = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("eindverantwoordelijke_id");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (eindverantwoordelijke == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return eindverantwoordelijke;
    }

    private int getMakerId(int regelingVersieId) {
        String sql = "SELECT maker_id FROM bzk.regelingversie WHERE id = ?";
        String errorMessage = "Maker niet gevonden in de database";

        Integer maker = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("maker_id");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (maker == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return maker;
    }

    private String getEindverantwoordelijke(int id) {
        String sql = "SELECT stop_id FROM public.stop_waarde WHERE id = ?";
        String errorMessage = "Eindverantwoordelijke niet gevonden in de database";

        String eindverantwoordelijke = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, id),
                rs -> {
                    if (rs.next()) {
                        return rs.getString(1);
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (eindverantwoordelijke == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return eindverantwoordelijke.substring(eindverantwoordelijke.lastIndexOf('/') + 1);
    }
}
