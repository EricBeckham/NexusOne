package one.nexus.p5.connect;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages="one.nexus.p5.connect")
public class ConnectApiExceptionHandler {
    @ExceptionHandler(SecurityException.class) @ResponseStatus(HttpStatus.FORBIDDEN) Map<String,String> denied(SecurityException e){return Map.of("code","P5.AUTH.0001","message",e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) Map<String,String> bad(IllegalArgumentException e){return Map.of("code","P5.API.0001","message",e.getMessage());}
    @ExceptionHandler(RateLimitException.class) @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) Map<String,String> limit(RateLimitException e){return Map.of("code","P5.RATE.0001","message",e.getMessage());}
}
