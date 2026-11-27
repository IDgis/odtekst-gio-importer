package nl.idgis.gioimporter.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeometryTypeTest {

    @ParameterizedTest
    @CsvSource({
            "ST_Point, POINT, punt, punt",
            "ST_MultiPoint, POINT, punt, punt",
            "ST_LineString, LINE, lijn, lijn",
            "ST_MultiLineString, LINE, lijn, lijn",
            "ST_Polygon, SURFACE, vlak, gebied",
            "ST_MultiPolygon, SURFACE, vlak, gebied",
            "st_polygon, SURFACE, vlak, gebied"
    })
    void mapExistingTypes(String input, GeometryType expected, String database, String imow) {
        var actual = GeometryType.fromPostgisType(input);
        assertEquals(expected, actual);
        assertEquals(database, actual.databaseValue());
        assertEquals(imow, actual.imowObjectType());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "ST_GeometryCollection", "ST_MultiSurface" })
    void rejectsUnrecognizedPostgisTypes(String input) {
        assertThrows(IllegalArgumentException.class, () -> GeometryType.fromPostgisType(input));
    }
}
