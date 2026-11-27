package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.port.GeometryRepository;
import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioGeometry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import java.util.OptionalInt;

public final class JdbcGeometryRepository implements GeometryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcGeometryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OptionalInt findId(String geometryIdentifier) {
        ResultSetExtractor<OptionalInt> extractor = rs ->
                rs.next() ? OptionalInt.of(rs.getInt("id")) : OptionalInt.empty();

        return jdbcTemplate.query("SELECT id FROM bzk.geometrie WHERE geometrie_id = ?", extractor, geometryIdentifier);
    }

    @Override
    public int insert(String naam, GioGeometry geometry) {
        String gmlId = geometry.id();
        String gml = geometry.gml();
        String sql = """
                INSERT INTO bzk.geometrie (naam, geometrie_id, geometrie)
                VALUES (?, ?, ST_GEOMFROMGML(?, 28992))
                RETURNING id
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, naam);
                    ps.setString(2, gmlId);
                    ps.setString(3, gml);

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de geometrie " + gmlId));
    }

    @Override
    public GeometryType getType(int geometrieId) {
        String sql = "SELECT ST_GEOMETRYTYPE(geometrie) geom_type FROM bzk.geometrie WHERE id = ?";
        String errorMessage = "Kon het type van de geometrie niet bepalen";

        GeometryType geometryType = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, geometrieId),
                rs -> {
                    if (rs.next()) {
                        return GeometryType.fromPostgisType(rs.getString("geom_type"));
                    }

                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (geometryType == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return geometryType;
    }
}
