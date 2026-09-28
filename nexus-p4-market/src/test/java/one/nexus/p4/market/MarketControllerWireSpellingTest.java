package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * The product mapper is snake_case, while some market clients send camelCase. Both spellings must reach the
 * controller request records instead of silently becoming null.
 */
class MarketControllerWireSpellingTest {
    private static final UUID APP_ID = UUID.fromString("01900000-0000-7000-8000-000000000401");
    private static final UUID VERSION_ID = UUID.fromString("01900000-0000-7000-8000-000000000402");
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void createAppSingleWordFieldsAreUnaffectedByNamingStrategy() throws Exception {
        var request = mapper.readValue(
                "{\"code\":\"office\",\"name\":\"办公\",\"summary\":\"说明\"}",
                MarketController.CreateApp.class);

        assertEquals("office", request.code());
        assertEquals("办公", request.name());
        assertEquals("说明", request.summary());
    }

    @ParameterizedTest
    @ValueSource(strings = {"release_notes", "releaseNotes"})
    void createVersionAcceptsEitherSpelling(String key) throws Exception {
        var request = mapper.readValue(
                "{\"version\":\"1.0.1\",\"" + key + "\":\"验收发布说明\"}",
                MarketController.CreateVersion.class);

        assertEquals("1.0.1", request.version());
        assertEquals("验收发布说明", request.releaseNotes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"app_id", "appId"})
    void createSkuAcceptsEitherSpelling(String key) throws Exception {
        var request = mapper.readValue(
                "{\"code\":\"office\",\"" + key + "\":\"" + APP_ID
                        + "\",\"name\":\"办公\",\"entitlements\":{}}",
                MarketController.CreateSku.class);

        assertEquals(APP_ID, request.appId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"sku_code\":\"office\",\"version_id\":\"01900000-0000-7000-8000-000000000402\"}",
        "{\"skuCode\":\"office\",\"versionId\":\"01900000-0000-7000-8000-000000000402\"}"
    })
    void installAcceptsEitherSpelling(String json) throws Exception {
        var request = mapper.readValue(json, MarketController.Install.class);

        assertEquals("office", request.skuCode());
        assertEquals(VERSION_ID, request.versionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"version_id", "versionId"})
    void upgradeAcceptsEitherSpelling(String key) throws Exception {
        var request = mapper.readValue(
                "{\"" + key + "\":\"" + VERSION_ID + "\"}",
                MarketController.Upgrade.class);

        assertEquals(VERSION_ID, request.versionId());
    }

    @Test
    void reviewSingleWordFieldIsUnaffectedByNamingStrategy() throws Exception {
        var request = mapper.readValue("{\"note\":\"批准\"}", MarketController.Review.class);

        assertEquals("批准", request.note());
    }
}
