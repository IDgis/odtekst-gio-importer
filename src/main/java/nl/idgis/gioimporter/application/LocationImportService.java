package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.RegelingContext;
import nl.idgis.gioimporter.domain.StoredGeometry;

import java.time.Clock;
import java.time.LocalDate;

public final class LocationImportService {

    private final LocationRepository locations;
    private final ImowIdentifierGenerator identifiers;
    private final Clock clock;

    public LocationImportService(LocationRepository locations,
                                 ImowIdentifierGenerator identifiers, Clock clock) {
        this.locations = locations;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    public int importLocation(GioLocation location, StoredGeometry geometry, RegelingContext context, boolean ontwerp) {
        var existing = locations.findByGeometryId(geometry.id());

        for (var stored : existing) {
            if (stored.regelingId() != context.regelingId()) {
                throw new IllegalArgumentException("Basisgeo-ID is al in gebruik bij een andere regeling: "
                        + location.geometry().id() + ". Lever de geometrie aan met een nieuw basisgeo-ID");
            }
        }

        if (!existing.isEmpty()) {
            return existing.getFirst().id();
        }

        int geometryId = geometry.id();
        GeometryType type = geometry.type();

        return locations.insert(new NewLocation(location.name(), LocalDate.now(clock),
                context.regelingId(), context.regelingVersionId(), type, geometryId,
                identifiers.location(context.authorityCode(), type), ontwerp));
    }
}
