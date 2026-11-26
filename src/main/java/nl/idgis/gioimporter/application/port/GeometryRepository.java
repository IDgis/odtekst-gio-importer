package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.domain.GeometryType;
import nl.idgis.gioimporter.domain.GioGeometry;

import java.util.OptionalInt;

public interface GeometryRepository {
    OptionalInt findId(String geometryIdentifier);
    int insert(String name, GioGeometry geometry);
    GeometryType getType(int geometryId);
}
