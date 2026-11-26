package nl.idgis.gioimporter.importer;

import nl.idgis.gioimporter.application.port.GioReader;
import nl.idgis.gioimporter.domain.GeographicContext;
import nl.idgis.gioimporter.domain.Gio;
import nl.idgis.gioimporter.domain.GioLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.io.File;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class GioImporter {

    private final JdbcTemplate jdbcTemplate;
    private final GioReader gioReader;

    private final String inputFile;
    private final String gioName;
    private final String regelingExpression;

    public GioImporter(JdbcTemplate jdbcTemplate, GioReader gioReader, String inputFile, String gioName, String regelingExpression) {
        this.jdbcTemplate = jdbcTemplate;
        this.gioReader = gioReader;
        this.inputFile = inputFile;
        this.gioName = gioName;
        this.regelingExpression = regelingExpression;
    }

    public void importGio() {
        File file = new File(inputFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("Het GIO bestand op de volgende locatie kan niet gevonden worden: " + file.getAbsolutePath());
        }

        // Haal regelingId op
        int regelingVersieId = getRegelingVersieId(regelingExpression);
        int regelingId = getRegelingId(regelingVersieId);
        int eindverantwoordelijkeId = getEindverantwoordelijkeId(regelingVersieId);
        int makerId = getMakerId(regelingVersieId);
        String eindverantwoordelijke = getEindverantwoordelijke(eindverantwoordelijkeId);

        Gio gio = gioReader.read(file.toPath());
        List<Integer> locatieIds = new ArrayList<>();
        for (int i = 0; i < gio.locations().size(); i++) {
            GioLocation locatie = gio.locations().get(i);
            String naam = locatie.name();
            String id = locatie.geometry().id();

            System.out.printf("Bezig met verwerken van locatie '%s' (%d/%d)%n", naam, i + 1, gio.locations().size());
            if (!geometryExists(id)) {
                int geometrieId = insertGeometry(id, naam, locatie.geometry().gml());
                String geometryType = getGeometryType(geometrieId);
                locatieIds.add(insertLocatie(naam, LocalDate.now(), regelingId, geometryType, geometrieId, eindverantwoordelijke, regelingVersieId));
            } else {
                int geometrieId = getGeometrieId(id);
                int locatieId = getLocatieId(geometrieId, regelingVersieId);
                updateLocatie(locatieId, naam);
                locatieIds.add(locatieId);
            }
        }

        System.out.println("Bezig met het maken van de groepslocatie");
        String geometryType= getLocatieGeometryType(locatieIds.get(0));
        int locatieGroepId = insertLocatie(gioName, LocalDate.now(), regelingId, geometryType, eindverantwoordelijke, regelingVersieId);
        locatieIds.forEach(locatieId -> linkLocatieToGroep(locatieId, locatieGroepId));

        System.out.println("Bezig met het verwerken van de GIO");
        GeographicContext context = gio.geographicContext();
        insertInformatieObjectVersie(gio.frbrWork(), gio.frbrExpression(), regelingId, eindverantwoordelijkeId, makerId,
                gioName, context.backgroundReference(), context.backgroundActuality(), context.accuracy(), locatieGroepId);
    }

    private int getRegelingVersieId(String expressionId) {
        String sql = "SELECT id FROM bzk.regelingversie WHERE frbr_expression = ?";
        String errorMessage = "Expression ID niet gevonden in de database: " + expressionId;

        Integer regelingVersieId = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setString(1, expressionId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (regelingVersieId == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return regelingVersieId;
    }

    private int getRegelingId(int regelingVersieId) {
        String sql =
                "SELECT r.id FROM bzk.regeling r " +
                "JOIN bzk.regelingversie rv ON rv.regeling_id = r.id " +
                "WHERE rv.id = ?";
        String errorMessage = "Regeling niet gevonden in de database met regelingversie id: " + regelingVersieId;

        Integer regelingId = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (regelingId == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return regelingId;
    }

    private int getEindverantwoordelijkeId(int regelingVersieId) {
        String sql = "SELECT eindverantwoordelijke_id FROM bzk.regelingversie WHERE id = ?";
        String errorMessage = "Eindverantwoordelijke niet gevonden in de database";

        Integer eindverantwoordelijke = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("eindverantwoordelijke_id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (eindverantwoordelijke == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return eindverantwoordelijke;
    }

    private String getEindverantwoordelijke(int id) {
        String sql = "SELECT stop_id FROM public.stop_waarde WHERE id = ?";
        String errorMessage = "Eindverantwoordelijke niet gevonden in de database";

        String eindverantwoordelijke = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, id),
                rs -> {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (eindverantwoordelijke == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return eindverantwoordelijke.substring(eindverantwoordelijke.lastIndexOf('/') + 1);
    }

    private int getMakerId(int regelingVersieId) {
        String sql = "SELECT maker_id FROM bzk.regelingversie WHERE id = ?";
        String errorMessage = "Maker niet gevonden in de database";

        Integer maker = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, regelingVersieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("maker_id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (maker == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return maker;
    }

    private boolean geometryExists(String gmlId) {
        String sql = "SELECT * FROM bzk.geometrie WHERE geometrie_id = ?";

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, gmlId),
                (rs, rowNum) -> rs.getInt(1)
        ).stream().findFirst().isPresent();
    }

    private int insertGeometry(String gmlId, String naam, String gml) {
        String sql =
                "INSERT INTO bzk.geometrie (naam, geometrie_id, geometrie) " +
                "VALUES (?, ?, ST_GEOMFROMGML(?, 28992)) " +
                "RETURNING id";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, naam);
                    ps.setString(2, gmlId);
                    ps.setString(3, gml);

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de geometrie " + gmlId));
    }

    private int getGeometrieId(String gmlId) {
        String sql = "SELECT id FROM bzk.geometrie WHERE geometrie_id = ?";
        String errorMessage = "Kon de id van de geometrie '" + gmlId + "' niet ophalen";

        Integer id = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setString(1, gmlId),
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (id == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return id;
    }

    private String getGeometryType(int geometrieId) {
        String sql = "SELECT ST_GEOMETRYTYPE(geometrie) geom_type FROM bzk.geometrie WHERE id = ?";
        String errorMessage = "Kon het type van de geometrie niet bepalen";

        String geometryType = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, geometrieId),
                rs -> {
                    if (rs.next()) {
                        String type = rs.getString("geom_type").toLowerCase();
                        if (type.contains("polygon")) {
                            return "vlak";
                        } else if (type.contains("line")) {
                            return "lijn";
                        } else if (type.contains("point")) {
                            return "punt";
                        }
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (geometryType == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return geometryType;
    }

    private String getLocatieGeometryType(int locatieId) {
        String sql = "SELECT geometrietype FROM bzk.locatie WHERE id = ?";
        String errorMessage = "Kon het geometrietype van de locatie niet bepalen";

        String geometryType = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> ps.setInt(1, locatieId),
                rs -> {
                    if (rs.next()) {
                        return rs.getString("geometrietype");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (geometryType == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return geometryType;
    }

    private int getLocatieId(int geometrieId, int regelingversie_id) {
        String sql = "SELECT id FROM bzk.locatie WHERE geometrie_id = ? and regelingversie_id = ?";
        String errorMessage = "Kon de id van de locatie niet ophalen";

        Integer id = jdbcTemplate.query(
                conn -> conn.prepareStatement(sql),
                ps -> {
                    ps.setInt(1, geometrieId);
                    ps.setInt(2, regelingversie_id);
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    throw new IllegalArgumentException(errorMessage);
                }
        );

        if (id == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return id;
    }

    private void updateLocatie(int locatieId, String name) {
        String sql = "UPDATE bzk.locatie SET naam = ? WHERE id = ?";

        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, name);
            ps.setInt(2, locatieId);

            return ps;
        });
    }

    private int insertLocatie(String name, LocalDate dateStart, int regelingId, String geometryType, int geometryId, String bgCode, int regelingversieId) {
        String sql =
                "INSERT INTO bzk.locatie (naam, datum_begin, ind_groep_jn, regeling_id, geometrietype, geometrie_id, identificatie, regelingversie_id) " +
                "VALUES (?, ?, false, ?, ?, ?, ?, ?) " +
                "RETURNING id";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String objectType = "vlak".equals(geometryType) ? "gebied" : geometryType;
        String identificatie = "nl.imow-" + bgCode + "." + objectType + "." + UUID.randomUUID().toString().replace("-", "").toLowerCase();
        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, name);
                    ps.setDate(2, Date.valueOf(dateStart));
                    ps.setInt(3, regelingId);
                    ps.setString(4, geometryType);
                    ps.setInt(5, geometryId);
                    ps.setString(6, identificatie);
                    ps.setInt(7, regelingversieId);

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de locatie"));
    }

    private int insertLocatie(String name, LocalDate dateStart, int regelingId, String geometryType, String bgCode, int regelingversieId) {
        String sql =
                "INSERT INTO bzk.locatie (naam, datum_begin, ind_groep_jn, regeling_id, geometrietype, identificatie, regelingversie_id) " +
                "VALUES (?, ?, true, ?, ?, ?, ?) " +
                "RETURNING id";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String objectType = "vlak".equals(geometryType) ? "gebied" : geometryType;
        String identificatie = "nl.imow-" + bgCode + "." + objectType + "engroep." + UUID.randomUUID().toString().replace("-", "").toLowerCase();
        jdbcTemplate.update(
                conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, name);
                    ps.setDate(2, Date.valueOf(dateStart));
                    ps.setInt(3, regelingId);
                    ps.setString(4, geometryType);
                    ps.setString(5, identificatie);
                    ps.setInt(6, regelingversieId);

                    return ps;
                },
                keyHolder
        );

        return Optional.ofNullable(keyHolder.getKey())
            .map(Number::intValue)
            .orElseThrow(() -> new IllegalStateException("Er ging iets mis bij het inserten van de locatie"));
    }

    private void linkLocatieToGroep(int locatieId, int locatieGroepId) {
        String sql = "INSERT INTO bzk.groep_locatie (locatiegroep_id, locatie_id) VALUES (?, ?)";

        jdbcTemplate.update(sql, ps -> {
            ps.setInt(1, locatieGroepId);
            ps.setInt(2, locatieId);
        });
    }

    private void insertInformatieObjectVersie(String frbrWork, String frbrExpression, int regelingId, int eindverantwoordelijkeId,
                      int makerId, String gioName, String achtergrondVerwijzing, String achergrondActualiteit, String nauwkeurigheid,
                      int locatieId) {
        String sql =
                "INSERT INTO bzk.informatieobjectversie (frbr_work, frbr_expression, soort_work_id, regeling_id, eindverantwoordelijke_id, maker_id, " +
                        "formaat_informatieobject_id, naam, officiele_titel, publicatie_instructie_id, stop_schema_versie, achtergrond_verwijzing, " +
                        "achtergrond_actualiteit, nauwkeurigheid, locatie_id) " +
                "VALUES (?, ?, 2056, ?, ?, ?, 1410, ?, ?, 3, '1.3.0', ?, ?, ?, ?)";

        jdbcTemplate.update(sql, ps -> {
            ps.setObject(1, frbrWork);
            ps.setObject(2, frbrExpression);
            ps.setObject(3, regelingId);
            ps.setObject(4, eindverantwoordelijkeId);
            ps.setObject(5, makerId);
            ps.setObject(6, gioName);
            ps.setObject(7, frbrWork);
            ps.setObject(8, achtergrondVerwijzing);
            ps.setObject(9, achergrondActualiteit != null ? Date.valueOf(achergrondActualiteit) : null);
            ps.setObject(10, nauwkeurigheid != null ? Integer.parseInt(nauwkeurigheid) : null);
            ps.setObject(11, locatieId);
        });
    }
}
