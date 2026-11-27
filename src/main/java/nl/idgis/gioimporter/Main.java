package nl.idgis.gioimporter;

import nl.idgis.gioimporter.application.GioImportService;
import nl.idgis.gioimporter.application.ImportRequest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan
@PropertySource("classpath:application.properties")
@PropertySource(value = "file:${spring.config.location}", ignoreResourceNotFound = true)
public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Main.class)) {
            GioImportService service = context.getBean(GioImportService.class);
            service.importGio(context.getBean(ImportRequest.class));
        }
    }
}
