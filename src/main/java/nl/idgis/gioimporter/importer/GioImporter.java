package nl.idgis.gioimporter.importer;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.application.model.NewLocation;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.*;
import nl.idgis.gioimporter.domain.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class GioImporter {

    private final GioReader gioReader;
    private final RegelingRepository regelingRepository;
    private final GeometryRepository geometryRepository;
    private final LocationRepository locationRepository;
    private final InformationObjectRepository informationObjectRepository;

    private final String inputFile;
    private final String gioName;
    private final String regelingExpression;

    public GioImporter(GioReader gioReader, RegelingRepository regelingRepository,
                       GeometryRepository geometryRepository, LocationRepository locationRepository,
                       InformationObjectRepository informationObjectRepository,
                       String inputFile, String gioName, String regelingExpression) {
        this.gioReader = gioReader;
        this.regelingRepository = regelingRepository;
        this.geometryRepository = geometryRepository;
        this.locationRepository = locationRepository;
        this.informationObjectRepository = informationObjectRepository;
        this.inputFile = inputFile;
        this.gioName = gioName;
        this.regelingExpression = regelingExpression;
    }

    public void importGio() {
        File file = new File(inputFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("Het GIO bestand op de volgende locatie kan niet gevonden worden: " + file.getAbsolutePath());
        }

        RegelingContext context = regelingRepository.getContext(regelingExpression);
        Gio gio = gioReader.read(file.toPath());
        List<Integer> locatieIds = new ArrayList<>();
        for (int i = 0; i < gio.locations().size(); i++) {
            GioLocation locatie = gio.locations().get(i);

            System.out.printf("Bezig met verwerken van locatie '%s' (%d/%d)%n", locatie.name(), i + 1, gio.locations().size());
            OptionalInt existing = geometryRepository.findId(locatie.geometry().id());
            if (existing.isEmpty()) {
                int geometryId = geometryRepository.insert(locatie.name(), locatie.geometry());
                GeometryType type = geometryRepository.getType(geometryId);
                locatieIds.add(locationRepository.insert(new NewLocation(locatie.name(), LocalDate.now(),
                        context.regelingId(), context.regelingVersionId(), type, geometryId,
                        identification(context.authorityCode(), type.imowObjectType()))));
            } else {
                int locationId = locationRepository.getId(existing.getAsInt(), context.regelingVersionId());
                locationRepository.updateName(locationId, locatie.name());
                locatieIds.add(locationId);
            }
        }

        System.out.println("Bezig met het maken van de groepslocatie");
        String type = locationRepository.getGeometryType(locatieIds.getFirst());
        String objectType = "vlak".equals(type) ? "gebied" : type;
        int groupId = locationRepository.insertGroup(new NewLocationGroup(gioName, LocalDate.now(),
                context.regelingId(), context.regelingVersionId(), type,
                identification(context.authorityCode(), objectType + "engroep")));
        locatieIds.forEach(id -> locationRepository.linkToGroup(id, groupId));

        System.out.println("Bezig met het verwerken van de GIO");
        informationObjectRepository.insert(new NewInformationObjectVersion(gio.frbrWork(), gio.frbrExpression(),
                context.regelingId(), context.responsibleAuthorityId(), context.creatorId(), gioName,
                gio.geographicContext(), groupId));
    }

    private String identification(String authorityCode, String objectType) {
        return "nl.imow-" + authorityCode + "." + objectType + "." +
                UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }
}
