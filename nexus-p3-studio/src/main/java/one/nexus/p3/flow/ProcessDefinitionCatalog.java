package one.nexus.p3.flow;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Read-only view of classpath BPMN. Does not deploy or save definitions. */
public final class ProcessDefinitionCatalog {
    private static final String PATTERN = "classpath*:processes/*.bpmn20.xml";

    public List<Map<String, Object>> list() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PATTERN);
            List<Map<String, Object>> definitions = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                definitions.addAll(read(resource));
            }
            definitions.sort(Comparator.comparing(row -> String.valueOf(row.get("key"))));
            return List.copyOf(definitions);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read process definitions", ex);
        }
    }

    public Map<String, Object> get(String key) {
        return list().stream()
                .filter(row -> key.equals(row.get("key")))
                .findFirst()
                .orElseThrow(() -> FlowException.notFound("process definition not found"));
    }

    private static List<Map<String, Object>> read(Resource resource) throws IOException {
        Element root = parse(resource);
        String source = resource.getFilename() == null ? "classpath" : "processes/" + resource.getFilename();
        List<Map<String, Object>> definitions = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "process".equals(element.getLocalName())) {
                definitions.add(process(element, source));
            }
        }
        return definitions;
    }

    private static Map<String, Object> process(Element process, String source) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", process.getAttribute("id"));
        json.put("name", text(process.getAttribute("name")));
        json.put("source", "classpath");
        json.put("resource", source);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> flows = new ArrayList<>();
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if ("sequenceFlow".equals(element.getLocalName())) {
                flows.add(flow(element));
            } else if (element.getAttribute("id") != null && !element.getAttribute("id").isBlank()) {
                nodes.add(node(element));
            }
        }
        json.put("nodes", List.copyOf(nodes));
        json.put("flows", List.copyOf(flows));
        return json;
    }

    private static Map<String, Object> node(Element element) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", element.getAttribute("id"));
        json.put("name", text(element.getAttribute("name")));
        json.put("type", element.getLocalName());
        return json;
    }

    private static Map<String, Object> flow(Element element) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", element.getAttribute("id"));
        json.put("source_ref", element.getAttribute("sourceRef"));
        json.put("target_ref", element.getAttribute("targetRef"));
        json.put("condition", condition(element));
        return json;
    }

    private static String condition(Element flow) {
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "conditionExpression".equals(element.getLocalName())) {
                return text(element.getTextContent());
            }
        }
        return null;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Element parse(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
                // Parser does not support these attributes; secure processing stays on.
            }
            return factory.newDocumentBuilder().parse(input).getDocumentElement();
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("cannot parse " + resource.getDescription(), ex);
        }
    }
}
