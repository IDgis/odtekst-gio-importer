package nl.idgis.gioimporter.application;

import java.nio.file.Path;

public record ImportRequest(Path inputFile, String gioName, String regelingExpression, boolean ontwerp) {
}
