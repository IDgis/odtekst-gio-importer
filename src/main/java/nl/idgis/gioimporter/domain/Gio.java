package nl.idgis.gioimporter.domain;

import java.util.List;

public record Gio(String frbrWork, String frbrExpression, GeographicContext geographicContext, List<GioLocation> locations) {

    public Gio {
        locations = List.copyOf(locations);
    }
}
