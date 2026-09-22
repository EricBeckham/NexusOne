package one.nexus.permission.engine;

/** Product DataScope kinds. CUSTOM holds a pre-compiled P0-02 Predicate. */
public enum DataScopeKind {
    SELF,
    DEPT,
    DEPT_AND_CHILDREN,
    SPECIFIED,
    ALL,
    CUSTOM
}
