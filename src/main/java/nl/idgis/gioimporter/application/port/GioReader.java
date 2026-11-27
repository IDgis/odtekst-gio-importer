package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.domain.Gio;

import java.nio.file.Path;

public interface GioReader {
    Gio read(Path path);
}
