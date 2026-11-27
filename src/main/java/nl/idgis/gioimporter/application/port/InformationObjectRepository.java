package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;

public interface InformationObjectRepository {
    boolean existsByFrbrExpression(String frbrExpression);
    void insert(NewInformationObjectVersion version);
}
