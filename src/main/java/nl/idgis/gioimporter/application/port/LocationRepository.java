package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;

public interface LocationRepository {
    int getId(int geometryId, int regelingVersionId);
    String getGeometryType(int locationId);
    void updateName(int locationId, String name);
    int insert(NewLocation location);
    int insertGroup(NewLocationGroup group);
    void linkToGroup(int locationId, int groupId);
}
