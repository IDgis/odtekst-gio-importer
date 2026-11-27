package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.port.GeometryRepository;
import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioGeometry;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.StoredGeometry;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GeometryImportServiceTest {

    private final GeometryRepository repository = mock(GeometryRepository.class);
    private final GeometryImportService service = new GeometryImportService(repository);
    private final GioLocation location = new GioLocation("name", new GioGeometry("id", "gml"));

    @Test
    void insertsNewGeometry() {
        when(repository.findId("id")).thenReturn(OptionalInt.empty());
        when(repository.insert("name", location.geometry())).thenReturn(10);
        when(repository.getType(10)).thenReturn(GeometryType.SURFACE);

        assertEquals(new StoredGeometry(10, GeometryType.SURFACE), service.importGeometry(location));

        var order = inOrder(repository);
        order.verify(repository).findId("id");
        order.verify(repository).insert("name", location.geometry());
        order.verify(repository).getType(10);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void reusesEqualGeometry() {
        when(repository.findId("id")).thenReturn(OptionalInt.of(10));
        when(repository.isEqual(10, location.geometry())).thenReturn(true);
        when(repository.getType(10)).thenReturn(GeometryType.LINE);

        assertEquals(new StoredGeometry(10, GeometryType.LINE), service.importGeometry(location));

        var order = inOrder(repository);
        order.verify(repository).findId("id");
        order.verify(repository).isEqual(10, location.geometry());
        order.verify(repository).getType(10);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void acceptsSameGmlWithDifferentBasisId() {
        var other = new GioLocation("other", new GioGeometry("other-id", location.geometry().gml()));

        when(repository.findId("id")).thenReturn(OptionalInt.of(10));
        when(repository.isEqual(10, location.geometry())).thenReturn(true);
        when(repository.getType(10)).thenReturn(GeometryType.SURFACE);
        when(repository.findId("other-id")).thenReturn(OptionalInt.empty());
        when(repository.insert("other", other.geometry())).thenReturn(11);
        when(repository.getType(11)).thenReturn(GeometryType.SURFACE);

        assertEquals(10, service.importGeometry(location).id());
        assertEquals(11, service.importGeometry(other).id());
        verify(repository, times(1)).isEqual(anyInt(), any());
        verify(repository).insert("other", other.geometry());
    }

    @Test void rejectsDifferentGeometry() {
        when(repository.findId("id")).thenReturn(OptionalInt.of(10));
        when(repository.isEqual(10, location.geometry())).thenReturn(false);

        var error = assertThrows(IllegalArgumentException.class, () -> service.importGeometry(location));
        assertEquals("Afwijkende geometrie bij bestaand basisgeo-ID: id", error.getMessage());
        verify(repository).findId("id");
        verify(repository).isEqual(10, location.geometry());
        verifyNoMoreInteractions(repository);
    }
}
