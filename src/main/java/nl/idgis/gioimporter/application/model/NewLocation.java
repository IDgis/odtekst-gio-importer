package nl.idgis.gioimporter.application.model;

import nl.idgis.gioimporter.domain.GeometryType;

import java.time.LocalDate;

public record NewLocation(String name, LocalDate dateStart, int regelingId, int regelingVersionId,
                          GeometryType geometryType, int geometryId, String identification) {
}
