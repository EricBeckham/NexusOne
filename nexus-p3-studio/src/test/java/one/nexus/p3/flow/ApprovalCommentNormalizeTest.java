package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApprovalCommentNormalizeTest {
    @Test
    void blankIsAbsentAndOverlongIsRejected() {
        assertNull(SequentialApprovalService.normalizeComment(null));
        assertNull(SequentialApprovalService.normalizeComment(" \n\t "));
        assertEquals("同意", SequentialApprovalService.normalizeComment("  同意  "));
        assertEquals(SequentialApprovalService.MAX_COMMENT_LENGTH,
                SequentialApprovalService.normalizeComment("a".repeat(SequentialApprovalService.MAX_COMMENT_LENGTH)).length());
        FlowException rejected = assertThrows(FlowException.class,
                () -> SequentialApprovalService.normalizeComment("a".repeat(SequentialApprovalService.MAX_COMMENT_LENGTH + 1)));
        assertEquals(400, rejected.httpStatus());
    }
}
