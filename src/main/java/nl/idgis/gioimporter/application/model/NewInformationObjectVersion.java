package nl.idgis.gioimporter.application.model;

import nl.idgis.gioimporter.domain.GeographicContext;

public record NewInformationObjectVersion(String frbrWork, String frbrExpression, int regelingId, int responsibleAuthorityId,
                                          int creatorId, String name, GeographicContext geographicContext, int locationGroupId) {
}
