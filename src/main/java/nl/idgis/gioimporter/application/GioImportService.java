package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.GioReader;
import nl.idgis.gioimporter.application.port.InformationObjectRepository;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.application.port.RegelingRepository;
import nl.idgis.gioimporter.domain.Gio;
import nl.idgis.gioimporter.domain.GioLocation;
import nl.idgis.gioimporter.domain.RegelingContext;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GioImportService {

    private final GioReader reader;
    private final RegelingRepository regelingen;
    private final LocationImportService locationImporter;
    private final LocationRepository locations;
    private final InformationObjectRepository informationObjects;
    private final ImowIdentifierGenerator identifiers;
    private final Clock clock;

    public GioImportService(GioReader reader, RegelingRepository regelingen,
                            LocationImportService locationImporter, LocationRepository locations,
                            InformationObjectRepository informationObjects,
                            ImowIdentifierGenerator identifiers, Clock clock) {
        this.reader = reader;
        this.regelingen = regelingen;
        this.locationImporter = locationImporter;
        this.locations = locations;
        this.informationObjects = informationObjects;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    public void importGio(ImportRequest request) {
        if (!Files.exists(request.inputFile())) {
            throw new IllegalArgumentException("Het GIO bestand op de volgende locatie kan niet gevonden worden: "
                    + request.inputFile().toAbsolutePath());
        }

        RegelingContext context = regelingen.getContext(request.regelingExpression());
        Gio gio = reader.read(request.inputFile());

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < gio.locations().size(); i++) {
            GioLocation location = gio.locations().get(i);

            System.out.printf("Bezig met verwerken van locatie '%s' (%d/%d)%n",
                    location.name(), i + 1, gio.locations().size());
            ids.add(locationImporter.importLocation(location, context));
        }

        System.out.println("Bezig met het maken van de groepslocatie");
        String type = locations.getGeometryType(ids.getFirst());
        int groupId = locations.insertGroup(new NewLocationGroup(request.gioName(), LocalDate.now(clock),
                context.regelingId(), context.regelingVersionId(), type,
                identifiers.group(context.authorityCode(), type)));
        ids.forEach(id -> locations.linkToGroup(id, groupId));

        System.out.println("Bezig met het verwerken van de GIO");
        informationObjects.insert(new NewInformationObjectVersion(gio.frbrWork(), gio.frbrExpression(),
                context.regelingId(), context.responsibleAuthorityId(), context.creatorId(), request.gioName(),
                gio.geographicContext(), groupId));
    }
}
