package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;

public interface InformationObjectRepository {
    void insert(NewInformationObjectVersion version);
}
