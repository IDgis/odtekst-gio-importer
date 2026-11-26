package nl.idgis.gioimporter.domain;

import java.util.Locale;

public enum GeometryType {
    POINT("punt", "punt"),
    LINE("lijn", "lijn"),
    SURFACE("vlak", "gebied");

    private final String databaseValue;
    private final String imowObjectType;

    GeometryType(String databaseValue, String imowObjectType) {
        this.databaseValue = databaseValue;
        this.imowObjectType = imowObjectType;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public String imowObjectType() {
        return imowObjectType;
    }

    public static GeometryType fromPostgisType(String type) {
        if (type != null) {
            String normalized = type.toLowerCase(Locale.ROOT);
            if (normalized.contains("polygon")) {
                return SURFACE;
            }
            if (normalized.contains("line")) {
                return LINE;
            }
            if (normalized.contains("point")) {
                return POINT;
            }
        }

        throw new IllegalArgumentException("Kon het type van de geometrie niet bepalen");
    }
}
