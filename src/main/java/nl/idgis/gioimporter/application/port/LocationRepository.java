package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.domain.StoredLocation;

import java.util.List;

public interface LocationRepository {
    List<StoredLocation> findByGeometryId(int geometryId);
    int insert(NewLocation location);
    int insertGroup(NewLocationGroup group);
    void linkToGroup(int locationId, int groupId);
}
