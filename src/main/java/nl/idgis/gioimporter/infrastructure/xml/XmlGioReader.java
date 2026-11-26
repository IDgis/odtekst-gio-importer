package nl.idgis.gioimporter.infrastructure.xml;

import nl.idgis.gioimporter.application.port.GioReader;
import nl.idgis.gioimporter.domain.GeographicContext;
import nl.idgis.gioimporter.domain.Gio;
import nl.idgis.gioimporter.domain.GioGeometry;
import nl.idgis.gioimporter.domain.GioLocation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;

public final class XmlGioReader implements GioReader {

    @Override
    public Gio read(Path path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = dbf.newDocumentBuilder().parse(path.toFile());
            XPath xPath = new XPathBuilder()
                .xmlns("basisgeo", "http://www.geostandaarden.nl/basisgeometrie/1.0")
                .xmlns("geo", "https://standaarden.overheid.nl/stop/imop/geo/")
                .xmlns("gio", "https://standaarden.overheid.nl/stop/imop/gio/")
                .xmlns("gml", "http://www.opengis.net/gml/3.2")
                .build();
            NodeList nodes = (NodeList) xPath.evaluate("//geo:Locatie", doc, XPathConstants.NODESET);

            List<GioLocation> locations = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node location = nodes.item(i);
                Node geometry = requiredNode(xPath, location, ".//basisgeo:geometrie");

                locations.add(new GioLocation(
                        requiredNode(xPath, location, "./geo:naam").getTextContent(),
                        new GioGeometry(
                                requiredNode(xPath, location, ".//basisgeo:id").getTextContent(),
                                getGml(requiredNode(xPath, geometry, ".//gml:*"))
                        )
                ));
            }

            return new Gio(
                    requiredNode(xPath, doc, "//geo:FRBRWork").getTextContent(),
                    requiredNode(xPath, doc, "//geo:FRBRExpression").getTextContent(),
                    new GeographicContext(
                            optionalText(xPath, doc, "//gio:achtergrondVerwijzing"),
                            optionalText(xPath, doc, "//gio:achtergrondActualiteit"),
                            optionalText(xPath, doc, "//gio:nauwkeurigheid")
                    ),
                    locations
            );
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            throw new IllegalStateException("GIO kon niet gelezen worden: " + path, e);
        }
    }

    private Node requiredNode(XPath xpath, Node context, String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, context, XPathConstants.NODE);
        if (node == null) {
            throw new IllegalArgumentException("Ontbrekend XML-element: " + expression);
        }

        return node;
    }

    private String optionalText(XPath xpath, Node context, String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, context, XPathConstants.NODE);

        return node == null ? null : node.getTextContent();
    }

    private String getGml(Node gmlNode) {
        try (StringWriter sw = new StringWriter()) {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new DOMSource(gmlNode), new StreamResult(sw));

            return sw.toString();
        } catch (IOException | TransformerException e) {
            throw new IllegalStateException("GML kon niet geparsed worden", e);
        }
    }

    private static class XPathBuilder {

        private final Map<String, String> namespaces = new HashMap<>();

        public XPathBuilder xmlns(String prefix, String namespaceURI) {
            namespaces.put(prefix, namespaceURI);
            return this;
        }

        public XPath build() {
            XPath xp = XPathFactory.newInstance().newXPath();
            xp.setNamespaceContext(new NamespaceContext() {

                @Override
                public String getNamespaceURI(String prefix) {
                    return namespaces.get(prefix);
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    return getPrefixes(namespaceURI).next();
                }

                @Override
                public Iterator<String> getPrefixes(String namespaceURI) {
                    return namespaces.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(namespaceURI))
                        .map(Map.Entry::getKey)
                        .iterator();
                }
            });

            return xp;
        }
    }
}
