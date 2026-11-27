package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GioImportServiceTest {

    @TempDir
    Path temp;

    private final GioReader reader = mock(GioReader.class);
    private final RegelingRepository regelingen = mock(RegelingRepository.class);
    private final GeometryRepository geometries = mock(GeometryRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final InformationObjectRepository objects = mock(InformationObjectRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDate date = LocalDate.of(2026, 11, 27);
    private final ImowIdentifierGenerator identifiers = new ImowIdentifierGenerator(
            () -> UUID.fromString("12345678-1234-1234-1234-123456789abc"));
    private final LocationImportService locationImporter = new LocationImportService(locations, identifiers, clock);
    private final GioImportService service = new GioImportService(reader, new GioImportTransaction(regelingen,
            new GeometryImportService(geometries), locationImporter, locations, objects, identifiers, clock));
    private final RegelingContext context = new RegelingContext(1, 2, 3, 4, "mnre1153");
    private final GioLocation first = new GioLocation("first", new GioGeometry("a", "gml-a"));
    private final GioLocation second = new GioLocation("second", new GioGeometry("b", "changed-gml"));
    private final GeographicContext metadata = new GeographicContext(null, null, null);

    private ImportRequest request(List<GioLocation> input) throws Exception {
        Path file = Files.createFile(temp.resolve("2020-01-01.gml"));

        when(regelingen.getContext("regeling")).thenReturn(context);
        when(reader.read(file)).thenReturn(new Gio("work", "expression", metadata, input));

        return new ImportRequest(file, "group", "regeling", false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void importsNewAndExistingLocationsWithUniformType(boolean ontwerp) throws Exception {
        ImportRequest base = request(List.of(first, second));
        ImportRequest request = new ImportRequest(base.inputFile(), base.gioName(), base.regelingExpression(), ontwerp);

        when(geometries.findId("a")).thenReturn(OptionalInt.empty());
        when(geometries.insert("first", first.geometry())).thenReturn(10);
        when(geometries.getType(10)).thenReturn(GeometryType.SURFACE);
        when(locations.insert(any(NewLocation.class))).thenReturn(20);
        when(geometries.findId("b")).thenReturn(OptionalInt.of(11));
        when(geometries.isEqual(11, second.geometry())).thenReturn(true);
        when(geometries.getType(11)).thenReturn(GeometryType.SURFACE);
        when(locations.findByGeometryId(11)).thenReturn(List.of(new StoredLocation(21, 1, 2)));
        when(locations.insertGroup(any(NewLocationGroup.class))).thenReturn(30);

        service.importGio(request);

        var order = inOrder(regelingen, reader, geometries, locations, objects);
        order.verify(reader).read(request.inputFile());
        order.verify(objects).existsByFrbrExpression("expression");
        order.verify(regelingen).getContext("regeling");
        order.verify(geometries).findId("a");
        order.verify(geometries).insert("first", first.geometry());
        order.verify(geometries).getType(10);
        order.verify(geometries).findId("b");
        order.verify(geometries).isEqual(11, second.geometry());
        order.verify(geometries).getType(11);
        order.verify(locations).findByGeometryId(10);
        order.verify(locations).insert(new NewLocation("first", date, 1, 2, GeometryType.SURFACE, 10,
                "nl.imow-mnre1153.gebied.12345678123412341234123456789abc", ontwerp));
        order.verify(locations).findByGeometryId(11);
        order.verify(locations).insertGroup(new NewLocationGroup("group", date, 1, 2, "vlak",
                "nl.imow-mnre1153.gebiedengroep.12345678123412341234123456789abc", ontwerp));
        order.verify(locations).linkToGroup(20, 30);
        order.verify(locations).linkToGroup(21, 30);
        order.verify(objects).insert(new NewInformationObjectVersion("work", "expression", 1, 3, 4, "group", metadata, 30));
        verifyNoMoreInteractions(regelingen, reader, geometries, locations, objects);
    }

    @ParameterizedTest
    @EnumSource(GeometryType.class)
    void derivesGroupTypeFromUniformGeometries(GeometryType type) throws Exception {
        ImportRequest request = request(List.of(first, second));

        when(geometries.findId("a")).thenReturn(OptionalInt.of(10));
        when(geometries.findId("b")).thenReturn(OptionalInt.of(11));
        when(geometries.isEqual(10, first.geometry())).thenReturn(true);
        when(geometries.isEqual(11, second.geometry())).thenReturn(true);
        when(geometries.getType(10)).thenReturn(type);
        when(geometries.getType(11)).thenReturn(type);
        when(locations.findByGeometryId(10)).thenReturn(List.of(new StoredLocation(20, 1, 2)));
        when(locations.findByGeometryId(11)).thenReturn(List.of(new StoredLocation(21, 1, 2)));
        when(locations.insertGroup(any())).thenReturn(30);

        service.importGio(request);

        verify(locations).insertGroup(new NewLocationGroup("group", date, 1, 2, type.databaseValue(),
                "nl.imow-mnre1153." + type.imowObjectType() + "engroep.12345678123412341234123456789abc", false));
        verify(locations).linkToGroup(20, 30);
        verify(locations).linkToGroup(21, 30);
        verify(locations, never()).insert(any());
    }

    @ParameterizedTest
    @CsvSource({"POINT,LINE", "POINT,SURFACE", "LINE,SURFACE"})
    void rejectsMixedCategoriesBeforeLocationProcessing(GeometryType firstType, GeometryType secondType) throws Exception {
        ImportRequest request = request(List.of(first, second));

        when(geometries.findId("a")).thenReturn(OptionalInt.empty());
        when(geometries.findId("b")).thenReturn(OptionalInt.empty());
        when(geometries.insert("first", first.geometry())).thenReturn(10);
        when(geometries.insert("second", second.geometry())).thenReturn(11);
        when(geometries.getType(10)).thenReturn(firstType);
        when(geometries.getType(11)).thenReturn(secondType);

        var error = assertThrows(IllegalArgumentException.class, () -> service.importGio(request));
        assertEquals("Gemengde geometrietypen: verwacht " + firstType.databaseValue()
                + ", gevonden " + secondType.databaseValue() + " bij basisgeo-ID b", error.getMessage());
        verifyNoInteractions(locations);
        verify(objects, never()).insert(any());
    }

    @Test
    void createsGroupEvenForOneExistingLocation() throws Exception {
        ImportRequest request = request(List.of(first));

        when(geometries.findId("a")).thenReturn(OptionalInt.of(10));
        when(geometries.isEqual(10, first.geometry())).thenReturn(true);
        when(geometries.getType(10)).thenReturn(GeometryType.POINT);
        when(locations.findByGeometryId(10)).thenReturn(List.of(new StoredLocation(20, 1, 99), new StoredLocation(19, 1, 98)));
        when(locations.insertGroup(any(NewLocationGroup.class))).thenReturn(30);
        service.importGio(request);

        verify(locations).insertGroup(new NewLocationGroup("group", date, 1, 2, "punt",
                "nl.imow-mnre1153.puntengroep.12345678123412341234123456789abc", false));
        verify(locations).linkToGroup(20, 30);
        verify(geometries, never()).insert(anyString(), any());
        verify(locations, never()).insert(any());
    }

    @Test
    void missingLocationForExistingGeometryIsCreated() throws Exception {
        ImportRequest request = request(List.of(first));

        when(geometries.findId("a")).thenReturn(OptionalInt.of(10));
        when(geometries.isEqual(10, first.geometry())).thenReturn(true);
        when(geometries.getType(10)).thenReturn(GeometryType.POINT);
        when(locations.findByGeometryId(10)).thenReturn(List.of());
        when(locations.insert(any())).thenReturn(20);
        when(locations.insertGroup(any())).thenReturn(30);

        service.importGio(request);

        verify(locations).insert(new NewLocation("first", date, 1, 2, GeometryType.POINT, 10,
                "nl.imow-mnre1153.punt.12345678123412341234123456789abc", false));
        verify(locations).linkToGroup(20, 30);
        verify(geometries, never()).insert(anyString(), any());
        verify(objects).insert(any());
    }

    @Test
    void existingFrbrExpressionStopsBeforeOtherRepositoryCalls() throws Exception {
        ImportRequest request = request(List.of(first));

        when(objects.existsByFrbrExpression("expression")).thenReturn(true);

        var error = assertThrows(IllegalArgumentException.class, () -> service.importGio(request));
        assertEquals("FRBRExpression bestaat al: expression", error.getMessage());

        var order = inOrder(reader, objects);
        order.verify(reader).read(request.inputFile());
        order.verify(objects).existsByFrbrExpression("expression");
        verifyNoMoreInteractions(reader, objects);
        verifyNoInteractions(regelingen, geometries, locations);
    }

    @Test
    void unreadableXmlStopsBeforeRepositoryCalls() throws Exception {
        Path file = Files.createFile(temp.resolve("invalid.gml"));

        when(reader.read(file)).thenThrow(new IllegalStateException("invalid XML"));

        assertThrows(IllegalStateException.class, () -> service.importGio(
                new ImportRequest(file, "group", "regeling", false)));
        verifyNoInteractions(regelingen, geometries, locations, objects);
    }

    @Test
    void missingFileStopsBeforeRepositoryCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.importGio(
                new ImportRequest(temp.resolve("missing.gml"), "group", "regeling", false)));
        verifyNoInteractions(reader, regelingen, geometries, locations, objects);
    }
}
