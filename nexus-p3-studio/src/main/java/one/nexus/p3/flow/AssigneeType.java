package one.nexus.p3.flow;

public enum AssigneeType {
    PERSON,
    POSITION,
    ROLE;

    public static AssigneeType parse(String value) {
        if (value == null || value.isBlank()) {
            throw FlowException.badRequest("assignee_type is required");
        }
        try {
            return AssigneeType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw FlowException.badRequest("assignee_type must be PERSON, POSITION or ROLE");
        }
    }
}
