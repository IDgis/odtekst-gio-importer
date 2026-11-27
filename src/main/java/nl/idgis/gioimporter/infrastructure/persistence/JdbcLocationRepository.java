package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.domain.StoredLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcLocationRepository implements LocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredLocation> findByGeometryId(int geometryId) {
        String sql = """
                SELECT id, regeling_id, regelingversie_id
                FROM bzk.locatie
                WHERE geometrie_id = ?
                ORDER BY datum_begin DESC, id DESC
                """;

        ResultSetExtractor<List<StoredLocation>> extractor = rs -> {
            var locations = new ArrayList<StoredLocation>();
            while (rs.next()) {
                locations.add(new StoredLocation(
                        rs.getInt("id"),
                        rs.getInt("regeling_id"),
                        rs.getInt("regelingversie_id")));
            }

            return List.copyOf(locations);
        };

        return jdbcTemplate.query(sql, extractor, geometryId);
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
                VALUES (?, ?, false, ?, ?, ?, ?, ?)
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
