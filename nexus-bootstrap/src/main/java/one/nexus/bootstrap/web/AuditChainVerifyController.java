package one.nexus.bootstrap.web;

import one.nexus.audit.AuditPort;
import one.nexus.audit.HashChainVerification;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform duty check of the audit hash chain. The body is the verification, never an event payload. */
@RestController
@RequestMapping("/api/v1/audit/chain")
public class AuditChainVerifyController {
    public record ChainVerification(boolean intact, long checked, String failure) {}

    private final IsolationGuard isolation;
    private final AuditPort audits;

    public AuditChainVerifyController(IsolationGuard isolation, AuditPort audits) {
        this.isolation = isolation;
        this.audits = audits;
    }

    @GetMapping("/verify")
    @PostMapping("/verify")
    public ChainVerification verify() {
        isolation.requirePlatformContext("audit.chain.verify", PlatformDuty.AUDIT_ADMIN);
        HashChainVerification result = audits.verifyChain();
        return new ChainVerification(result.intact(), result.checked(), result.failure());
    }
}
