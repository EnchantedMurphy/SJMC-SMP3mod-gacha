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

    @Test void ticketSelectorsResolveModelsAndTexturesAndPreservePaperFallback() throws IOException {
        var model = read(ROOT.resolve("assets/minecraft/items/paper.json")).getAsJsonObject("model");
        assertEquals("minecraft:select", model.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", model.get("property").getAsString());
        assertEquals(0, model.get("index").getAsInt());
        var fallback = model.getAsJsonObject("fallback");
        assertEquals("minecraft:model", fallback.get("type").getAsString());
        assertEquals("minecraft:item/paper", fallback.get("model").getAsString());
        Set<String> markers = new HashSet<>();
        Set<Path> textures = new HashSet<>();
        for (var entry : model.getAsJsonArray("cases")) {
            var branch = entry.getAsJsonObject();
            assertTrue(markers.add(branch.get("when").getAsString()));
            var selected = branch.getAsJsonObject("model");
            assertEquals("minecraft:model", selected.get("type").getAsString());
            var definition = read(asset(selected.get("model").getAsString(), "models", ".json"));
            assertEquals("minecraft:item/generated", definition.get("parent").getAsString());
            var png = asset(definition.getAsJsonObject("textures").get("layer0").getAsString(), "textures", ".png");
            assertTrue(textures.add(png));
            var image = ImageIO.read(png.toFile());
            assertNotNull(image, png.toString());
            assertEquals(image.getWidth(), image.getHeight());
            assertEquals(1, Integer.bitCount(image.getWidth()), "Texture size must be a power of two");
            assertTrue(image.getColorModel().hasAlpha());
        }
        assertEquals(Set.of(Tickets.SINGLE_MODEL, Tickets.TEN_MODEL), markers);
        assertEquals(2, textures.size());
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
