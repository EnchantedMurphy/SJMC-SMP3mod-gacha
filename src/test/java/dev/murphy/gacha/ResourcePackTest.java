package dev.murphy.gacha;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackTest {
    private static final Path ROOT = Path.of("resourcepacks/smp3-resourcepack-3.0-alpha-1");

    @Test void metadataIsAcceptedByTheTargetMinecraftVersion() throws IOException {
        SharedConstants.tryDetectVersion();
        var metadata = PackMetadataSection.CLIENT_TYPE.codec()
                .parse(JsonOps.INSTANCE, read(ROOT.resolve("pack.mcmeta")).get("pack")).getOrThrow();
        assertTrue(metadata.supportedFormats().isValueInRange(
                SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)));
    }

    @Test void registeredTicketAssetsResolveAndPackMatchesBundledResources() throws IOException {
        for (String name : Set.of("ticket_single", "ticket_ten")) {
            String definitionPath = "assets/smp3/items/" + name + ".json";
            var selected = read(ROOT.resolve(definitionPath)).getAsJsonObject("model");
            assertEquals("minecraft:model", selected.get("type").getAsString());
            var modelPath = asset(selected.get("model").getAsString(), "models", ".json");
            var definition = read(modelPath);
            assertEquals("minecraft:item/generated", definition.get("parent").getAsString());
            var png = asset(definition.getAsJsonObject("textures").get("layer0").getAsString(), "textures", ".png");
            var image = ImageIO.read(png.toFile());
            assertNotNull(image); assertEquals(image.getWidth(), image.getHeight());
            assertEquals(1, Integer.bitCount(image.getWidth()));
            for (Path path : Set.of(ROOT.resolve(definitionPath), modelPath, png))
                assertArrayEquals(Files.readAllBytes(path), Files.readAllBytes(Path.of("src/main/resources").resolve(ROOT.relativize(path))));
        }
        assertFalse(Files.exists(ROOT.resolve("assets/minecraft/items/paper.json")));
        var translations = read(Path.of("src/main/resources/assets/smp3/lang/zh_cn.json"));
        assertEquals("SMP3", translations.get("itemGroup.smp3").getAsString());
        assertEquals("抽奖券", translations.get("item.smp3.ticket_single").getAsString());
        assertEquals("十连抽奖券", translations.get("item.smp3.ticket_ten").getAsString());
    }

    private static Path asset(String id, String directory, String extension) {
        var parts = id.split(":", 2);
        assertEquals(2, parts.length);
        return ROOT.resolve("assets").resolve(parts[0]).resolve(directory).resolve(parts[1] + extension);
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
