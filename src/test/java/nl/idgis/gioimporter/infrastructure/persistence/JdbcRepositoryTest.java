package nl.idgis.gioimporter.infrastructure.persistence;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.domain.GeographicContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JdbcRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void findsExistingGeometry() throws Exception {
        assertEquals(OptionalInt.of(42), findGeometry(true));
    }

    @Test
    void missingGeometryReturnsEmpty() throws Exception {
        assertEquals(OptionalInt.empty(), findGeometry(false));
    }

    private OptionalInt findGeometry(boolean exists) throws Exception {
        var rs = mock(ResultSet.class);

        when(rs.next()).thenReturn(exists);
        when(rs.getInt("id")).thenReturn(42);

        when(jdbc.query(
                eq("SELECT id FROM bzk.geometrie WHERE geometrie_id = ?"),
                ArgumentMatchers.<ResultSetExtractor<OptionalInt>>any(), eq("basis-id")
        )).thenAnswer(call ->
            call.<ResultSetExtractor<OptionalInt>>getArgument(1).extractData(rs));

        var result = new JdbcGeometryRepository(jdbc).findId("basis-id");

        verify(jdbc).query(
                eq("SELECT id FROM bzk.geometrie WHERE geometrie_id = ?"),
                ArgumentMatchers.<ResultSetExtractor<OptionalInt>>any(),
                eq("basis-id"));

        return result;
    }

    @Test
    void linksGroupAndLocationInSqlOrder() throws Exception {
        var ps = mock(PreparedStatement.class);

        when(jdbc.update(eq("INSERT INTO bzk.groep_locatie (locatiegroep_id, locatie_id) VALUES (?, ?)"),
                any(PreparedStatementSetter.class))).thenAnswer(call -> {
            call.<PreparedStatementSetter>getArgument(1).setValues(ps);
            return 1;
        });

        new JdbcLocationRepository(jdbc).linkToGroup(12, 34);
        verify(ps).setInt(1, 34);
        verify(ps).setInt(2, 12);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checksFrbrExpressionGlobally(boolean exists) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM bzk.informatieobjectversie
                    WHERE frbr_expression = ?
                )
                """;

        when(jdbc.queryForObject(sql, Boolean.class, "expression")).thenReturn(exists);
        assertEquals(exists, new JdbcInformationObjectRepository(jdbc).existsByFrbrExpression("expression"));
        verify(jdbc).queryForObject(sql, Boolean.class, "expression");
        verifyNoMoreInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void comparesGeometrySpatially(boolean equal) {
        String sql = """
                SELECT ST_Equals(geometrie, ST_GeomFromGML(?, 28992))
                FROM bzk.geometrie
                WHERE id = ?
                """;

        when(jdbc.queryForObject(sql, Boolean.class, "gml", 42)).thenReturn(equal);

        assertEquals(equal, new JdbcGeometryRepository(jdbc).isEqual(42,
                new nl.idgis.gioimporter.domain.GioGeometry("id", "gml")));
        verify(jdbc).queryForObject(sql, Boolean.class, "gml", 42);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void bindsInformationObjectMetadata() throws Exception {
        var ps = mock(PreparedStatement.class);

        when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenAnswer(call -> {
            call.<PreparedStatementSetter>getArgument(1).setValues(ps);
            return 1;
        });

        new JdbcInformationObjectRepository(jdbc).insert(new NewInformationObjectVersion(
                "work", "expression", 1, 2, 3, "name",
                new GeographicContext("BRT", "2026-11-02", "3"), 4));
        verify(ps).setObject(1, "work");
        verify(ps).setObject(2, "expression");
        verify(ps).setObject(3, 1);
        verify(ps).setObject(4, 2);
        verify(ps).setObject(5, 3);
        verify(ps).setObject(6, "name");
        verify(ps).setObject(7, "work");
        verify(ps).setObject(8, "BRT");
        verify(ps).setObject(9, Date.valueOf("2026-11-02"));
        verify(ps).setObject(10, 3);
        verify(ps).setObject(11, 4);
    }
}
