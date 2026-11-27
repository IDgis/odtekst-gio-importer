package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.port.GioReader;
import nl.idgis.gioimporter.domain.Gio;

import java.nio.file.Files;

public class GioImportService {

    private final GioReader reader;
    private final GioImportTransaction transaction;

    public GioImportService(GioReader reader, GioImportTransaction transaction) {
        this.reader = reader;
        this.transaction = transaction;
    }

    public void importGio(ImportRequest request) {
        if (!Files.exists(request.inputFile())) {
            throw new IllegalArgumentException("Het GIO bestand op de volgende locatie kan niet gevonden worden: "
                    + request.inputFile().toAbsolutePath());
        }

        Gio gio = reader.read(request.inputFile());
        transaction.importGio(request, gio);
    }
}
