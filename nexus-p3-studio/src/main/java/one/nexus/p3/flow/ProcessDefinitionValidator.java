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
            boolean parallel = "parallelGateway".equals(localName(gateway));
            if (!parallel && !"exclusiveGateway".equals(localName(gateway))) continue;
            String id = entry.getKey();
            List<Element> exits = outgoingFlows.getOrDefault(id, List.of());
            if (incomingCounts.getOrDefault(id, 0) == 0 || exits.isEmpty()) {
                errors.add(error("GATEWAY_CONNECTION_REQUIRED", "网关必须有进入和离开的连线", id, null));
            }
            String defaultFlow = gateway.getAttribute("default");
            if (parallel) {
                if (!defaultFlow.isBlank()) {
                    errors.add(error("PARALLEL_DEFAULT_FLOW_UNSUPPORTED", "并行网关不使用默认连线", id, defaultFlow));
                }
                if (incomingCounts.getOrDefault(id, 0) == 1 && exits.size() == 1) {
                    errors.add(error("PARALLEL_BRANCH_REQUIRED", "并行网关至少需要两条进入或离开的连线", id, null));
                }
                for (Element exit : exits) {
                    for (Node child = exit.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (child instanceof Element condition && "conditionExpression".equals(localName(condition))) {
                            errors.add(error("PARALLEL_FLOW_CONDITION_UNSUPPORTED", "并行网关的出线不能带条件", id, exit.getAttribute("id")));
                        }
                    }
                }
                continue;
            }
            if (!defaultFlow.isBlank() && exits.stream().noneMatch(flow -> defaultFlow.equals(flow.getAttribute("id")))) {
                errors.add(error("GATEWAY_DEFAULT_FLOW_INVALID", "默认连线必须是当前网关的出线", id, defaultFlow));
            }
        }

        checkParallelPairing(nodes, outgoingFlows, incomingCounts, errors);

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

    /**
     * ZA11 thin fork/join pairing. Supported: a fork (one in, several out) whose every branch is a straight
     * chain of single-exit nodes (possibly empty) ending at the same join (several in, one out), and nothing
     * else feeding that join. Everything else is reported with a node/flow locator instead of guessed at:
     * mixed gateways, branches that end or merge early, nested splits and loops, mismatched or unpaired joins.
     * Flowable execution is untouched.
     */
    private static void checkParallelPairing(Map<String, Element> nodes, Map<String, List<Element>> outgoingFlows,
                                             Map<String, Integer> incomingCounts, List<ValidationError> errors) {
        Set<String> reported = new HashSet<>();
        java.util.function.Consumer<ValidationError> report = error -> {
            if (reported.add(error.code() + "|" + error.nodeId() + "|" + error.flowId())) errors.add(error);
        };
        Set<String> forks = new java.util.LinkedHashSet<>();
        Set<String> joins = new java.util.LinkedHashSet<>();
        for (var entry : nodes.entrySet()) {
            if (!"parallelGateway".equals(localName(entry.getValue()))) continue;
            String id = entry.getKey();
            int in = incomingCounts.getOrDefault(id, 0);
            int out = outgoingFlows.getOrDefault(id, List.of()).size();
            if (in > 1 && out > 1) {
                report.accept(error("PARALLEL_GATEWAY_MIXED", "并行网关不能同时汇合和分出；请拆成一个并行汇合和一个并行开始", id, null));
            } else if (out > 1) {
                forks.add(id);
            } else if (in > 1) {
                joins.add(id);
            }
        }

        Set<String> pairedJoins = new HashSet<>();
        for (String fork : forks) {
            String join = null;
            boolean broken = false;
            for (Element exit : outgoingFlows.getOrDefault(fork, List.of())) {
                String cursor = exit.getAttribute("targetRef");
                Set<String> seen = new HashSet<>();
                String reached = null;
                while (cursor != null && nodes.containsKey(cursor)) {
                    if (!seen.add(cursor) || cursor.equals(fork)) {
                        report.accept(error("PARALLEL_STRUCTURE_UNSUPPORTED", "并行分支里出现了回环，当前只支持并行开始到并行汇合之间的顺序分支", cursor, null));
                        break;
                    }
                    boolean parallelJoin = "parallelGateway".equals(localName(nodes.get(cursor))) && incomingCounts.getOrDefault(cursor, 0) > 1;
                    if (parallelJoin) {
                        reached = cursor;
                        break;
                    }
                    if (incomingCounts.getOrDefault(cursor, 0) > 1) {
                        report.accept(error("PARALLEL_EARLY_MERGE", "并行分支在到达并行汇合之前就与其他连线合流了", cursor, null));
                        break;
                    }
                    List<Element> next = outgoingFlows.getOrDefault(cursor, List.of());
                    if (next.isEmpty()) {
                        report.accept(error("PARALLEL_BRANCH_NOT_JOINED", "并行分支没有汇合就结束了；每条并行分支都要连到同一个并行汇合", fork, exit.getAttribute("id")));
                        break;
                    }
                    if (next.size() > 1) {
                        report.accept(error("PARALLEL_STRUCTURE_UNSUPPORTED", "并行分支里又出现了分支，当前只支持并行开始到并行汇合之间的顺序分支", cursor, null));
                        break;
                    }
                    cursor = next.getFirst().getAttribute("targetRef");
                }
                if (reached == null) {
                    broken = true;
                } else if (join == null) {
                    join = reached;
                } else if (!join.equals(reached)) {
                    report.accept(error("PARALLEL_JOIN_MISMATCH", "同一个并行开始的分支必须汇合到同一个并行汇合", fork, exit.getAttribute("id")));
                    broken = true;
                }
            }
            if (join == null) continue;
            pairedJoins.add(join);
            if (!broken && incomingCounts.getOrDefault(join, 0) != outgoingFlows.getOrDefault(fork, List.of()).size()) {
                report.accept(error("PARALLEL_JOIN_MISMATCH", "并行汇合还收到了并行分支以外的连线", join, null));
            }
        }
        for (String join : joins) {
            if (!pairedJoins.contains(join)) {
                report.accept(error("PARALLEL_JOIN_UNPAIRED", "并行汇合没有对应的并行开始", join, null));
            }
        }
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
