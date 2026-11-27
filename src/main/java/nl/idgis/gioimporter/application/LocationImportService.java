package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.RegelingContext;

import java.time.Clock;
import java.time.LocalDate;

public final class LocationImportService {

    private final GeometryImportService geometries;
    private final LocationRepository locations;
    private final ImowIdentifierGenerator identifiers;
    private final Clock clock;

    public LocationImportService(GeometryImportService geometries, LocationRepository locations,
                                 ImowIdentifierGenerator identifiers, Clock clock) {
        this.geometries = geometries;
        this.locations = locations;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    public int importLocation(GioLocation location, RegelingContext context) {
        var geometry = geometries.importGeometry(location);
        if (geometry.existing()) {
            int id = locations.getId(geometry.id(), context.regelingVersionId());
            locations.updateName(id, location.name());

            return id;
        }

        int geometryId = geometry.id();
        GeometryType type = geometry.type();

        return locations.insert(new NewLocation(location.name(), LocalDate.now(clock),
                context.regelingId(), context.regelingVersionId(), type, geometryId,
                identifiers.location(context.authorityCode(), type)));
    }
}
