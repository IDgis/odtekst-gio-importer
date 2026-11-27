package nl.idgis.gioimporter.domain;

public record StoredGeometry(int id, GeometryType type, boolean existing) {
}
