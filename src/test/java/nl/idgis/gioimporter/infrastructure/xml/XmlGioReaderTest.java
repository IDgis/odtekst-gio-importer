package nl.idgis.gioimporter.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XmlGioReaderTest {

    private final XmlGioReader reader = new XmlGioReader();

    @TempDir
    Path temp;

    @Test
    void readsRealGio() throws Exception {
        var gio = reader.read(Path.of(getClass().getResource("/gio/N2000_KempenlandWest_N2000_2026-11-05.gio.gml").toURI()));

        assertEquals("/join/id/regdata/mnre1153/2026/N2000_KempenlandWest_N2000", gio.frbrWork());
        assertEquals(gio.frbrWork() + "/nld@2026-11-05", gio.frbrExpression());
        assertEquals("BRT achtergrondkaart", gio.geographicContext().backgroundReference());
        assertEquals("2026-11-02", gio.geographicContext().backgroundActuality());
        assertEquals("3", gio.geographicContext().accuracy());
        assertEquals(1, gio.locations().size());

        var location = gio.locations().getFirst();

        assertEquals("Kempenland-West (HR:NL1000022)", location.name());
        assertEquals("19c1546c-9511-4579-8192-bdfddfbfd417", location.geometry().id());

        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(location.geometry().gml().getBytes(StandardCharsets.UTF_8)));

        assertEquals("MultiSurface", doc.getDocumentElement().getLocalName());
        assertEquals("urn:ogc:def:crs:EPSG::28992", doc.getDocumentElement().getAttribute("srsName"));
        assertEquals(8, doc.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "Polygon").getLength());
    }

    @Test
    void readsAbsentOptionalMetadataAndAlternativePrefix() throws Exception {
        var file = Files.writeString(temp.resolve("minimal.xml"), """
            <x:GeoInformatieObjectVaststelling schemaversie="1.3.0"
                xmlns:x="https://standaarden.overheid.nl/stop/imop/geo/"
                xmlns:b="http://www.geostandaarden.nl/basisgeometrie/1.0"
                xmlns:g="http://www.opengis.net/gml/3.2">
              <x:FRBRWork>/join/id/regdata/mnre1153/2026/test</x:FRBRWork><x:FRBRExpression>/join/id/regdata/mnre1153/2026/test/nld@2026-11-05</x:FRBRExpression>
              <x:Locatie><x:naam>Test</x:naam><b:id>id</b:id>
                <b:geometrie><g:Point><g:pos>1 2</g:pos></g:Point></b:geometrie>
              </x:Locatie>
            </x:GeoInformatieObjectVaststelling>
            """);
        var gio = reader.read(file);

        assertEquals("/join/id/regdata/mnre1153/2026/test", gio.frbrWork());
        assertNull(gio.geographicContext().backgroundReference());
        assertNull(gio.geographicContext().backgroundActuality());
        assertNull(gio.geographicContext().accuracy());
        assertEquals(1, gio.locations().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.0", "1.4.0", "", " 1.3.0 "})
    void rejectsUnsupportedSchemaVersion(String version) throws Exception {
        var file = Files.writeString(temp.resolve("version.xml"),
                "<root schemaversie=\"" + version + "\"/>");

        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(file));
        assertTrue(error.getMessage().contains("STOP-schemaversie"));
    }

    @Test
    void rejectsMissingSchemaVersion() throws Exception {
        var file = Files.writeString(temp.resolve("version.xml"), "<root/>");

        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(file));
        assertTrue(error.getMessage().contains("STOP-schemaversie"));
    }

    @Test
    void rejectsGioWithoutLocations() throws Exception {
        var file = Files.writeString(temp.resolve("empty.xml"), """
            <geo:GeoInformatieObjectVaststelling schemaversie="1.3.0"
                xmlns:geo="https://standaarden.overheid.nl/stop/imop/geo/">
              <geo:FRBRWork>/join/id/regdata/mnre1153/2026/test</geo:FRBRWork>
              <geo:FRBRExpression>/join/id/regdata/mnre1153/2026/test/nld@2026-11-05</geo:FRBRExpression>
            </geo:GeoInformatieObjectVaststelling>
            """);

        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(file));
        assertTrue(error.getMessage().contains("minimaal één locatie"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026/test", "2026-11-05/other", "2026/a.b+(c)", "2026-99-99/rest"})
    void acceptsFrbrFormats(String tail) throws Exception {
        String work = "/join/id/regdata/mnre1153/" + tail;
        assertEquals(work, reader.read(frbrFile(work, work + "/nld@2026-99-99")).frbrWork());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/join/id/regdata/other/2026/test",
            "/join/id/regdata/mnre1153/26/test", "/join/id/regdata/mnre1153/2026/",
            "/join/id/regdata/mnre1153/2026-1-01/test"})
    void rejectsInvalidFrbrWork(String work) throws Exception {
        var file = frbrFile(work, work + "/nld@2026-11-05");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> reader.read(file))
                .getMessage().contains("FRBRWork"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/nld@2026", "/eng@2026-11-05", "/nld@2026-1-05",
            "/nld@2026-11-05/extra"})
    void rejectsInvalidExpressionSuffix(String suffix) throws Exception {
        String work = "/join/id/regdata/mnre1153/2026/test";
        var file = frbrFile(work, work + suffix);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> reader.read(file))
                .getMessage().contains("FRBRExpression"));
    }

    @Test
    void rejectsDifferentWorkEvenWithRegexCharacters() throws Exception {
        String work = "/join/id/regdata/mnre1153/2026/a.b";
        var file = frbrFile(work, work.replace("a.b", "axb") + "/nld@2026-11-05");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> reader.read(file))
                .getMessage().contains("FRBRExpression"));
    }

    private Path frbrFile(String work, String expression) throws Exception {
        return Files.writeString(temp.resolve("frbr.xml"), """
            <geo:GeoInformatieObjectVaststelling schemaversie="1.3.0"
                xmlns:geo="https://standaarden.overheid.nl/stop/imop/geo/"
                xmlns:b="http://www.geostandaarden.nl/basisgeometrie/1.0"
                xmlns:g="http://www.opengis.net/gml/3.2">
              <geo:FRBRWork>%s</geo:FRBRWork>
              <geo:FRBRExpression>%s</geo:FRBRExpression>
              <geo:Locatie><geo:naam>Test</geo:naam><b:id>id</b:id>
                <b:geometrie><g:Point><g:pos>1 2</g:pos></g:Point></b:geometrie>
              </geo:Locatie>
            </geo:GeoInformatieObjectVaststelling>
            """.formatted(work, expression));
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        var file = Files.writeString(temp.resolve("broken.xml"), "<broken>");
        assertThrows(IllegalStateException.class, () -> reader.read(file));
    }

    @Test
    void rejectsDoctype() throws Exception {
        var file = Files.writeString(temp.resolve("doctype.xml"), "<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>");
        assertThrows(IllegalStateException.class, () -> reader.read(file));
    }

    @Test
    void rejectsMissingRequiredElement() throws Exception {
        var file = Files.writeString(temp.resolve("missing.xml"), "<root schemaversie=\"1.3.0\"/>");
        assertThrows(IllegalArgumentException.class, () -> reader.read(file));
    }
}
