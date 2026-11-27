package nl.idgis.gioimporter.application.port;

import nl.idgis.gioimporter.domain.RegelingContext;

public interface RegelingRepository {
    RegelingContext getContext(String expression);
}
