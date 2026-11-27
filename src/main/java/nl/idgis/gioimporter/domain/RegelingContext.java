package nl.idgis.gioimporter.domain;

public record RegelingContext(int regelingId, int regelingVersionId, int responsibleAuthorityId,
                              int creatorId, String authorityCode) {
}
