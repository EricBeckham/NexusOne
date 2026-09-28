package one.nexus.p4.market;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages="one.nexus.p4.market")
public class MarketApiExceptionHandler {
    @ExceptionHandler(AppDesignService.ManifestValidationException.class) @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String,Object> invalidManifest(AppDesignService.ManifestValidationException e){
        return Map.of("code","P4.MANIFEST.0001","message",e.getMessage(),"problems",e.problems());
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String,String> badRequest(IllegalArgumentException e){return Map.of("code","P4.MARKET.0001","message",e.getMessage());}
    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
    Map<String,String> conflict(IllegalStateException e){return Map.of("code","P4.MARKET.0002","message",e.getMessage());}
}
