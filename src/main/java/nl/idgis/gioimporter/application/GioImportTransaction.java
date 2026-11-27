package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.application.model.NewInformationObjectVersion;
import nl.idgis.gioimporter.application.model.NewLocationGroup;
import nl.idgis.gioimporter.application.port.InformationObjectRepository;
import nl.idgis.gioimporter.application.port.LocationRepository;
import nl.idgis.gioimporter.application.port.RegelingRepository;
import nl.idgis.gioimporter.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GioImportTransaction {

    private static final Logger LOGGER = Logger.getLogger(GioImportTransaction.class.getName());

    private final RegelingRepository regelingen;
    private final GeometryImportService geometryImporter;
    private final LocationImportService locationImporter;
    private final LocationRepository locations;
    private final InformationObjectRepository informationObjects;
    private final ImowIdentifierGenerator identifiers;
    private final Clock clock;

    public GioImportTransaction(RegelingRepository regelingen, GeometryImportService geometryImporter,
            LocationImportService locationImporter, LocationRepository locations,
            InformationObjectRepository informationObjects, ImowIdentifierGenerator identifiers, Clock clock) {
        this.regelingen = regelingen;
        this.geometryImporter = geometryImporter;
        this.locationImporter = locationImporter;
        this.locations = locations;
        this.informationObjects = informationObjects;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void importGio(ImportRequest request, Gio gio) {
        if (informationObjects.existsByFrbrExpression(gio.frbrExpression())) {
            throw new IllegalArgumentException("FRBRExpression bestaat al: " + gio.frbrExpression());
        }

        RegelingContext context = regelingen.getContext(request.regelingExpression());

        List<StoredGeometry> storedGeometries = new ArrayList<>();
        for (GioLocation location : gio.locations()) {
            storedGeometries.add(geometryImporter.importGeometry(location));
        }

        GeometryType geometryType = storedGeometries.getFirst().type();
        for (int i = 1; i < storedGeometries.size(); i++) {
            GeometryType actual = storedGeometries.get(i).type();
            if (actual != geometryType) {
                throw new IllegalArgumentException("Gemengde geometrietypen: verwacht "
                        + geometryType.databaseValue() + ", gevonden " + actual.databaseValue()
                        + " bij basisgeo-ID " + gio.locations().get(i).geometry().id());
            }
        }

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < gio.locations().size(); i++) {
            GioLocation location = gio.locations().get(i);

            LOGGER.log(Level.INFO, "Bezig met verwerken van locatie ''{0}'' ({1}/{2})",
                    new Object[] { location.name(), i + 1, gio.locations().size() });
            ids.add(locationImporter.importLocation(location, storedGeometries.get(i), context, request.ontwerp()));
        }

        LOGGER.info("Bezig met het maken van de groepslocatie");
        String type = geometryType.databaseValue();
        int groupId = locations.insertGroup(new NewLocationGroup(request.gioName(), LocalDate.now(clock),
                context.regelingId(), context.regelingVersionId(), type,
                identifiers.group(context.authorityCode(), type), request.ontwerp()));
        ids.forEach(id -> locations.linkToGroup(id, groupId));

        LOGGER.info("Bezig met het verwerken van de GIO");
        informationObjects.insert(new NewInformationObjectVersion(gio.frbrWork(), gio.frbrExpression(),
                context.regelingId(), context.responsibleAuthorityId(), context.creatorId(), request.gioName(),
                gio.geographicContext(), groupId));
    }
}
