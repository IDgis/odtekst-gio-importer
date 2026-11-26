package nl.idgis.gioimporter.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void readAbsentOptionalMetadataAndAlternativePrefix() throws Exception {
        var file = Files.writeString(temp.resolve("minimal.xml"), """
            <x:root xmlns:x="https://standaarden.overheid.nl/stop/imop/geo/">
              <x:FRBRWork>work</x:FRBRWork><x:FRBRExpression>expression</x:FRBRExpression>
            </x:root>
            """);
        var gio = reader.read(file);

        assertEquals("work", gio.frbrWork());
        assertNull(gio.geographicContext().backgroundReference());
        assertNull(gio.geographicContext().backgroundActuality());
        assertNull(gio.geographicContext().accuracy());
        assertTrue(gio.locations().isEmpty());
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
        var file = Files.writeString(temp.resolve("missing.xml"), "<root/>");
        assertThrows(IllegalArgumentException.class, () -> reader.read(file));
    }
}
