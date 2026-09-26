package one.nexus.p3.flow;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import one.nexus.p3.flow.ProcessDefinitionValidationResult.ValidationError;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Structural BPMN checks only. This validator never deploys a process. */
@Component
public class ProcessDefinitionValidator {
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    public ProcessDefinitionValidationResult validate(String bpmnXml) {
        List<ValidationError> errors = new ArrayList<>();
        Element process;
        try {
            process = parseProcess(bpmnXml);
        } catch (Exception ex) {
            return invalid("INVALID_BPMN_XML", "BPMN XML 无法安全解析");
        }

        Map<String, Element> nodes = new LinkedHashMap<>();
        List<Element> flows = new ArrayList<>();
        List<String> starts = new ArrayList<>();
        List<String> ends = new ArrayList<>();
        for (Node child = process.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element element)) continue;
            String type = localName(element);
            if ("sequenceFlow".equals(type)) {
                flows.add(element);
                continue;
            }
            String id = element.getAttribute("id").trim();
            if (id.isEmpty()) continue;
            if (nodes.putIfAbsent(id, element) != null) {
                errors.add(error("DUPLICATE_NODE_ID", "节点标识必须唯一", id, null));
            }
            if ("startEvent".equals(type)) starts.add(id);
            if ("endEvent".equals(type)) ends.add(id);
            if ("userTask".equals(type)) {
                if (element.getAttribute("name").isBlank()) {
                    errors.add(error("USER_TASK_NAME_REQUIRED", "用户任务必须填写名称", id, null));
                }
                String assignee = element.getAttributeNS(FLOWABLE_NS, "assignee");
                if (assignee == null || assignee.isBlank()) {
                    assignee = element.getAttribute("flowable:assignee");
                }
                if (assignee.isBlank()) {
                    errors.add(error("USER_TASK_ASSIGNEE_REQUIRED", "用户任务必须填写审批人引用", id, null));
                }
            }
        }

        if (starts.isEmpty()) errors.add(error("START_REQUIRED", "流程至少需要一个开始节点", null, null));
        if (ends.isEmpty()) errors.add(error("END_REQUIRED", "流程至少需要一个结束节点", null, null));

        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<Element>> outgoingFlows = new HashMap<>();
        Map<String, Integer> incomingCounts = new HashMap<>();
        for (String id : nodes.keySet()) outgoing.put(id, new ArrayList<>());
        for (Element flow : flows) {
            String id = flow.getAttribute("id");
            String source = flow.getAttribute("sourceRef");
            String target = flow.getAttribute("targetRef");
            if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
                errors.add(error("DANGLING_FLOW", "连线必须引用当前流程中的节点", null, id));
            } else {
                outgoing.get(source).add(target);
                outgoingFlows.computeIfAbsent(source, ignored -> new ArrayList<>()).add(flow);
                incomingCounts.merge(target, 1, Integer::sum);
            }
            for (Node child = flow.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element condition && "conditionExpression".equals(localName(condition))
                        && condition.getTextContent().isBlank()) {
                    errors.add(error("FLOW_CONDITION_EMPTY", "连线条件不能为空；无条件连线请移除条件", null, id));
                }
            }
        }

        for (var entry : nodes.entrySet()) {
            Element gateway = entry.getValue();
            if (!"exclusiveGateway".equals(localName(gateway))) continue;
            String id = entry.getKey();
            List<Element> exits = outgoingFlows.getOrDefault(id, List.of());
            if (incomingCounts.getOrDefault(id, 0) == 0 || exits.isEmpty()) {
                errors.add(error("GATEWAY_CONNECTION_REQUIRED", "网关必须有进入和离开的连线", id, null));
            }
            String defaultFlow = gateway.getAttribute("default");
            if (!defaultFlow.isBlank() && exits.stream().noneMatch(flow -> defaultFlow.equals(flow.getAttribute("id")))) {
                errors.add(error("GATEWAY_DEFAULT_FLOW_INVALID", "默认连线必须是当前网关的出线", id, defaultFlow));
            }
        }

        if (!starts.isEmpty()) {
            Set<String> reachable = new HashSet<>(starts);
            ArrayDeque<String> queue = new ArrayDeque<>(starts);
            while (!queue.isEmpty()) {
                for (String next : outgoing.getOrDefault(queue.removeFirst(), List.of())) {
                    if (reachable.add(next)) queue.addLast(next);
                }
            }
            for (String id : nodes.keySet()) {
                if (!reachable.contains(id)) {
                    errors.add(error("UNREACHABLE_NODE", "节点无法从开始节点到达", id, null));
                }
            }
        }

        return new ProcessDefinitionValidationResult(errors.isEmpty(), errors);
    }

    private static Element parseProcess(String xml) throws Exception {
        if (xml == null || xml.isBlank()) throw new IllegalArgumentException("BPMN is empty");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException ex) throws SAXException {
                throw ex;
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXException {
                throw ex;
            }
        });
        Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        if ("process".equals(localName(root))) return root;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "process".equals(localName(element))) return element;
        }
        throw new IllegalArgumentException("BPMN process missing");
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName().replaceFirst("^.*:", "") : element.getLocalName();
    }

    private static ValidationError error(String code, String message, String nodeId, String flowId) {
        return new ValidationError(code, message, nodeId, flowId);
    }

    private static ProcessDefinitionValidationResult invalid(String code, String message) {
        return new ProcessDefinitionValidationResult(false, List.of(error(code, message, null, null)));
    }
}
