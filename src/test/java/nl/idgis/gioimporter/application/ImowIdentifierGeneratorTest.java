package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.domain.GeometryType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImowIdentifierGeneratorTest {

    private final ImowIdentifierGenerator generator = new ImowIdentifierGenerator(
            () -> UUID.fromString("12345678-1234-1234-1234-123456789abc"));

    @ParameterizedTest
    @CsvSource({"POINT,punt", "LINE,lijn", "SURFACE,gebied"})
    void locationIdentifiers(GeometryType type, String objectType) {
        assertEquals("nl.imow-mnre1153." + objectType + ".12345678123412341234123456789abc",
                generator.location("mnre1153", type));
    }

    @ParameterizedTest
    @CsvSource({"punt,puntengroep", "lijn,lijnengroep", "vlak,gebiedengroep", "other,otherengroep"})
    void groupIdentifiers(String type, String objectType) {
        assertEquals("nl.imow-mnre1153." + objectType + ".12345678123412341234123456789abc",
                generator.group("mnre1153", type));
    }
}
