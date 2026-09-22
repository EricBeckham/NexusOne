package one.nexus.p3.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Intentional fixture: P3 records surface with no SurfaceGuard token. */
@RestController
@RequestMapping("/api/v1/p3/ledgers/{ledgerId}/records")
public class DumpController {
    @GetMapping
    public String list() {
        return "ungarded-dump";
    }
}
