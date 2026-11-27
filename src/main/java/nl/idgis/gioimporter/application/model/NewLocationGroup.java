package nl.idgis.gioimporter.application.model;

import java.time.LocalDate;

public record NewLocationGroup(String name, LocalDate dateStart, int regelingId, int regelingVersionId,
                               String geometryType, String identification, boolean ontwerp) {
}
