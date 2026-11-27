package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GioImportTransactionTest {

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {}

    @TempDir
    Path temp;

    @Test
    void commitsSuccessfulImport() throws Exception {
        runImport(false, false, false, false, false);
    }

    @Test
    void rollsBackWhenFinalInsertFails() throws Exception {
        runImport(true, false, false, false, false);
    }

    @Test
    void rollsBackWhenFrbrExpressionAlreadyExists() throws Exception {
        runImport(false, true, false, false, false);
    }

    @Test
    void
    rollsBackWhenLaterGeometryDiffers() throws Exception {
        runImport(false, false, true, false, false);
    }

    @Test
    void rollsBackWhenLaterLocationBelongsToOtherRegeling() throws Exception {
        runImport(false, false, false, true, false);
    }

    @Test
    void rollsBackWhenGeometryTypesDiffer() throws Exception {
        runImport(false, false, false, false, true);
    }

    private void runImport(boolean fail, boolean duplicate, boolean mismatch, boolean foreign, boolean mixed) throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);

        when(ds.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        GioReader reader = mock(GioReader.class);
        RegelingRepository regelingen = mock(RegelingRepository.class);
        GeometryRepository geometries = mock(GeometryRepository.class);
        LocationRepository locations = mock(LocationRepository.class);
        InformationObjectRepository objects = mock(InformationObjectRepository.class);

        Clock clock = Clock.systemUTC();
        var identifiers = new ImowIdentifierGenerator(UUID::randomUUID);
        var locationImporter = new LocationImportService(locations, identifiers, clock);
        Path file = Files.createFile(temp.resolve("input.gml"));
        var location = new GioLocation("name", new GioGeometry("id", "gml"));
        var second = new GioLocation("second", new GioGeometry("existing", "different-gml"));
        var gio = new Gio("work", "expression",
                new GeographicContext(null, null, null),
                (mismatch || foreign || mixed) ? List.of(location, second) : List.of(location));

        if (mismatch || foreign) {
            when(geometries.findId("existing")).thenReturn(OptionalInt.of(11));
            when(geometries.isEqual(11, second.geometry())).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return foreign;
            });
        }
        if (mixed) {
            when(geometries.findId("existing")).thenReturn(OptionalInt.empty());
            when(geometries.insert("second", second.geometry())).thenReturn(11);
            when(geometries.getType(11)).thenReturn(GeometryType.POINT);
        }
        if (foreign) {
            when(geometries.getType(11)).thenReturn(GeometryType.SURFACE);
            when(locations.findByGeometryId(11)).thenReturn(List.of(new StoredLocation(22, 1, 2), new StoredLocation(21, 99, 98)));
        }

        when(reader.read(file)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return gio;
        });
        when(objects.existsByFrbrExpression("expression")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(connection, DataSourceUtils.getConnection(ds));
            DataSourceUtils.releaseConnection(connection, ds);
            return duplicate;
        });
        when(regelingen.getContext("regeling")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(connection, DataSourceUtils.getConnection(ds));
            DataSourceUtils.releaseConnection(connection, ds);
            return new RegelingContext(1, 2, 3, 4, "authority");
        });
        when(geometries.findId("id")).thenReturn(OptionalInt.empty());
        when(geometries.insert("name", location.geometry())).thenReturn(10);
        when(geometries.getType(10)).thenReturn(GeometryType.SURFACE);
        when(locations.insert(any())).thenReturn(20);
        when(locations.insertGroup(any())).thenReturn(30);
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(connection, DataSourceUtils.getConnection(ds));
            DataSourceUtils.releaseConnection(connection, ds);
            if (fail) throw new IllegalStateException("final insert failed");
            return null;
        }).when(objects).insert(any());

        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(TransactionConfig.class);
            ctx.registerBean("transactionManager", DataSourceTransactionManager.class,
                    () -> new DataSourceTransactionManager(ds));
            ctx.registerBean(GioImportTransaction.class, () -> new GioImportTransaction(
                    regelingen, new GeometryImportService(geometries), locationImporter, locations, objects, identifiers, clock));
            ctx.refresh();

            var service = new GioImportService(reader, ctx.getBean(GioImportTransaction.class));
            var request = new ImportRequest(file, "group", "regeling", false);
            if (duplicate || mismatch || foreign || mixed) {
                assertThrows(IllegalArgumentException.class, () -> service.importGio(request));
            } else if (fail) {
                assertThrows(IllegalStateException.class, () -> service.importGio(request));
            } else {
                service.importGio(request);
            }

            verify(objects).existsByFrbrExpression("expression");
            if (duplicate) {
                verifyNoInteractions(regelingen, geometries, locations);
                verifyNoMoreInteractions(objects);
            } else if (mixed) {
                verify(geometries).insert("name", location.geometry());
                verify(geometries).insert("second", second.geometry());
                verifyNoInteractions(locations);
                verify(objects, never()).insert(any());
            } else if (mismatch || foreign) {
                verify(geometries).insert("name", location.geometry());
                if (mismatch) {
                    verifyNoInteractions(locations);
                } else {
                    verify(locations).insert(any());
                }
                verify(geometries).isEqual(11, second.geometry());
                if (foreign) {
                    verify(locations).findByGeometryId(11);
                }
                verify(locations, never()).insertGroup(any());
                verify(locations, never()).linkToGroup(anyInt(), anyInt());
                verify(objects, never()).insert(any());
            } else {
                verify(locations).linkToGroup(20, 30);
                verify(objects).insert(any());
            }

            var order = inOrder(connection);
            order.verify(connection).setAutoCommit(false);
            if (fail || duplicate || mismatch || foreign || mixed) {
                order.verify(connection).rollback();
                verify(connection, never()).commit();
            } else {
                order.verify(connection).commit();
                verify(connection, never()).rollback();
            }

            order.verify(connection).setAutoCommit(true);
            order.verify(connection).close();
            verify(ds, times(1)).getConnection();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
