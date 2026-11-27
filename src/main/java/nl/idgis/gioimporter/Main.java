package nl.idgis.gioimporter;

import nl.idgis.gioimporter.application.GioImportService;
import nl.idgis.gioimporter.application.ImportRequest;
import nl.idgis.gioimporter.config.GioImporterConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(GioImporterConfig.class)) {
            GioImportService service = context.getBean(GioImportService.class);
            service.importGio(context.getBean(ImportRequest.class));
        }
    }
}
