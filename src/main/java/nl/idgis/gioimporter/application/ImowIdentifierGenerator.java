package nl.idgis.gioimporter.application;

import nl.idgis.gioimporter.domain.GeometryType;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public final class ImowIdentifierGenerator {

    private final Supplier<UUID> uuids;

    public ImowIdentifierGenerator(Supplier<UUID> uuids) {
        this.uuids = uuids;
    }

    public String location(String authorityCode, GeometryType type) {
        return identification(authorityCode, type.imowObjectType());
    }

    public String group(String authorityCode, String type) {
        String objectType = "vlak".equals(type) ? "gebied" : type;

        return identification(authorityCode, objectType + "engroep");
    }

    private String identification(String authorityCode, String objectType) {
        return "nl.imow-" + authorityCode + "." + objectType + "."
                + uuids.get().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }
}
