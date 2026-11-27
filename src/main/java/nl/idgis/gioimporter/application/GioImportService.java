package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.port.GioReader;
import nl.idgis.gioimporter.domain.Gio;

import java.nio.file.Files;
import java.util.logging.Logger;

public class GioImportService {

    private static final Logger LOGGER = Logger.getLogger(GioImportService.class.getName());

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

        LOGGER.info("GIO-import succesvol afgerond");
    }
}
