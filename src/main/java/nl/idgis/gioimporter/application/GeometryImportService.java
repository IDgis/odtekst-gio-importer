package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.port.GeometryRepository;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.StoredGeometry;

public final class GeometryImportService {

    private final GeometryRepository geometries;

    public GeometryImportService(GeometryRepository geometries) {
        this.geometries = geometries;
    }

    public StoredGeometry importGeometry(GioLocation location) {
        var geometry = location.geometry();
        var existing = geometries.findId(geometry.id());

        int id;
        if (existing.isPresent()) {
            id = existing.getAsInt();
            if (!geometries.isEqual(id, geometry)) {
                throw new IllegalArgumentException("Afwijkende geometrie bij bestaand basisgeo-ID: " + geometry.id());
            }
        } else {
            id = geometries.insert(location.name(), geometry);
        }

        return new StoredGeometry(id, geometries.getType(id), existing.isPresent());
    }
}
