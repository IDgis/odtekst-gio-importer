package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.LocationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

public final class JdbcLocationRepository implements LocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int getId(int geometryId, int regelingVersieId) {
        String sql = "SELECT id FROM bzk.locatie WHERE geometrie_id = ? AND regelingversie_id = ?";
        String errorMessage = "Kon de id van de locatie niet ophalen";

        Integer id = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> {
                    ps.setInt(1, geometryId);
                    ps.setInt(2, regelingVersieId);
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (id == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return id;
    }

    @Override
    public String getGeometryType(int locatieId) {
        String sql = "SELECT geometrietype FROM bzk.locatie WHERE id = ?";
        String errorMessage = "Kon het geometrietype van de locatie niet bepalen";

        String geometryType = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, locatieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getString("geometrietype");
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (geometryType == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return geometryType;
    }

    @Override
    public void updateName(int locatieId, String name) {
        String sql = "UPDATE bzk.locatie SET naam = ? WHERE id = ?";

        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, name);
            ps.setInt(2, locatieId);

            return ps;
        });
    }

    @Override
    public void linkToGroup(int locatieId, int locatieGroepId) {
        String sql = "INSERT INTO bzk.groep_locatie (locatiegroep_id, locatie_id) VALUES (?, ?)";

        jdbcTemplate.update(sql, ps -> {
            ps.setInt(1, locatieGroepId);
            ps.setInt(2, locatieId);
        });
    }

    @Override
    public int insert(NewLocation location) {
        String sql = """
                INSERT INTO bzk.locatie (naam, datum_begin, ind_groep_jn, regeling_id, geometrietype, geometrie_id, identificatie, regelingversie_id)
                VALUES (?, ?, false, ? ? ? ? ?)
                RETURNING id
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, location.name());
                    ps.setDate(2, Date.valueOf(location.dateStart()));
                    ps.setInt(3, location.regelingId());
                    ps.setString(4, location.geometryType().databaseValue());
                    ps.setInt(5, location.geometryId());
                    ps.setString(6, location.identification());
                    ps.setInt(7, location.regelingVersionId());

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de locatie"));
    }

    @Override
    public int insertGroup(NewLocationGroup location) {
        String sql = """
                INSERT INTO bzk.locatie (naam, datum_begin, ind_groep_jn, regeling_id, geometrietype, identificatie, regelingversie_id)
                VALUES (?, ?, true, ?, ?, ?, ?)
                RETURNING id
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, location.name());
                    ps.setDate(2, Date.valueOf(location.dateStart()));
                    ps.setInt(3, location.regelingId());
                    ps.setString(4, location.geometryType());
                    ps.setString(5, location.identification());
                    ps.setInt(6, location.regelingVersionId());

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de locatie"));
    }
}
