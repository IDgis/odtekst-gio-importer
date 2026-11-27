package nl.idgis.gioimporter.domain;

import java.util.List;

public record Gio(String frbrWork, String frbrExpression, GeographicContext geographicContext, List<GioLocation> locations) {

    public static final String SUPPORTED_SCHEMA_VERSION = "1.3.0";

    public Gio {
        locations = List.copyOf(locations);
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("Een GIO moet minimaal één locatie bevatten");
        }
    }
}
