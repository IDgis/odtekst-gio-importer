package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.port.GeometryRepository;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.RegelingContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;

public final class LocationImportService {

    private final GeometryRepository geometries;
    private final LocationRepository locations;
    private final ImowIdentifierGenerator identifiers;
    private final Clock clock;

    public LocationImportService(GeometryRepository geometries, LocationRepository locations,
                                 ImowIdentifierGenerator identifiers, Clock clock) {
        this.geometries = geometries;
        this.locations = locations;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    public int importLocation(GioLocation location, RegelingContext context) {
        OptionalInt existing = geometries.findId(location.geometry().id());
        if (existing.isPresent()) {
            int id = locations.getId(existing.getAsInt(), context.regelingVersionId());
            locations.updateName(id, location.name());

            return id;
        }

        int geometryId = geometries.insert(location.name(), location.geometry());
        GeometryType type = geometries.getType(geometryId);

        return locations.insert(new NewLocation(location.name(), LocalDate.now(clock),
                context.regelingId(), context.regelingVersionId(), type, geometryId,
                identifiers.location(context.authorityCode(), type)));
    }
}
