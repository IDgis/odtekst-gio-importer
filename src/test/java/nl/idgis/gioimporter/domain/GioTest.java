package nl.idgis.gioimporter.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GioTest {

    @Test
    void copiesLocationList() {
        var location = new GioLocation("Test", new GioGeometry("id", "gml"));
        var source = new ArrayList<>(List.of(location));
        var gio = new Gio("work", "expression", new GeographicContext(null, null, null), source);

        source.clear();

        assertEquals(List.of(location), gio.locations());
        assertThrows(UnsupportedOperationException.class, () -> gio.locations().clear());
    }

    @Test
    void preservesLocationOrderAndDuplicates() {
        var a = new GioLocation("A", new GioGeometry("a", "gml"));
        var b = new GioLocation("B", new GioGeometry("b", "gml"));
        var locations = List.of(b, a, b);

        assertEquals(locations, new Gio("work", "expression", null, locations).locations());
    }

    @Test
    void rejectsEmptyLocationList() {
        assertThrows(IllegalArgumentException.class,
                () -> new Gio("work", "expression", null, List.of()));
    }
}
