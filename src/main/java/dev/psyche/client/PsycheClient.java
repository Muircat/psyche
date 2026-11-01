package dev.psyche.client;

import dev.psyche.Psyche;
import dev.psyche.core.Content;
import dev.psyche.core.Content.Challenge;
import dev.psyche.core.Profile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Client side: owns the content and the learner profile, and opens questions as gameplay triggers them. */
public final class PsycheClient implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger(Psyche.MOD);
    static final Content CONTENT = new Content();
    static final Random RND = new Random();
    static final Deque<Pending> QUEUE = new ArrayDeque<>();
    static Profile profile;
    static KeyBinding openKey, notebookKey;
    private static long lastQueued;

    record Pending(Challenge c, String source, long at) {}

    @Override
    public void onInitializeClient() {
        try (Reader r = new InputStreamReader(PsycheClient.class.getResourceAsStream("/assets/psyche/content.json"), StandardCharsets.UTF_8)) {
            CONTENT.load(r);
        } catch (Exception e) { LOG.error("Could not load built-in course content", e); }

        // Extra hand-written packs: config/psyche/packs/*.json (same format as the built-in content.json).
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(Psyche.MOD);
        try {
            Files.createDirectories(dir.resolve("packs"));
            try (var packs = Files.newDirectoryStream(dir.resolve("packs"), "*.json")) {
                for (Path p : packs) try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) { CONTENT.load(r); }
                catch (Exception e) { LOG.warn("Skipping broken pack {}", p, e); }
            }
        } catch (Exception e) { LOG.warn("Could not read packs folder", e); }
        profile = Profile.load(dir.resolve("profile.json"));
        LOG.info("Psyche loaded {} questions in {} topics", CONTENT.challenges.size(), CONTENT.topics.size());

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.psyche.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.psyche"));
        notebookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.psyche.notebook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.psyche"));

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient || !(entity instanceof VillagerEntity v) || v.isBaby()) return ActionResult.PASS;
            String job = Registries.VILLAGER_PROFESSION.getId(v.getVillagerData().getProfession()).getPath();
            if (job.equals("none") || job.equals("nitwit") || System.currentTimeMillis() - UNLOCKED.getOrDefault(v.getUuid(), 0L) < 300_000)
                return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (hand == Hand.MAIN_HAND && !(mc.currentScreen instanceof ChallengeScreen)) {
                if (!talk(mc, v, job)) return ActionResult.PASS; // no question for this villager: trade as normal
            }
            return ActionResult.FAIL; // no trading until the question is answered
        });

        ClientPlayNetworking.registerGlobalReceiver(Psyche.Trigger.ID, (t, ctx) -> ctx.client().execute(() -> onTrigger(t.key())));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long now = System.currentTimeMillis();
            QUEUE.removeIf(p -> now - p.at > 180_000); // "you just did X" goes stale after 3 minutes in a menu
            // Gameplay questions are not optional: they open as soon as no other screen is in the way.
            if (!QUEUE.isEmpty() && mc.currentScreen == null && mc.player != null && mc.player.isAlive()) openNext(mc);
            while (openKey.wasPressed()) openNext(mc);
            while (notebookKey.wasPressed()) mc.setScreen(new NotebookScreen());
        });
    }

    private static void onTrigger(String key) {
        long now = System.currentTimeMillis();
        if (QUEUE.size() >= 3 || now - lastQueued < profile.cooldown * 1000L) return;
        Challenge c = CONTENT.pick(key, null, profile, RND);
        if (c == null || QUEUE.stream().anyMatch(p -> p.c == c)) return;
        QUEUE.add(new Pending(c, key, now));
        lastQueued = now;
    }

    /** Opens the oldest waiting question, or a review question if nothing is waiting. */
    static void openNext(MinecraftClient mc) {
        Pending p = QUEUE.poll();
        Challenge c = p != null ? p.c : CONTENT.pick(null, null, profile, RND);
        if (c != null) mc.setScreen(new ChallengeScreen(c, p != null ? p.source : "review"));
    }

    // Each villager gets a name and voice pitch from its UUID.
    private static final String[] NAMES = {"Ada", "Bram", "Cora", "Dev", "Esme", "Finn", "Gwen", "Hal",
            "Isla", "Jude", "Kit", "Lena", "Milo", "Nell", "Otis", "Priya"};
    private static final Map<UUID, Long> UNLOCKED = new HashMap<>();

    /** A villager (often a new parent) asks for advice; a good answer opens the trade screen. */
    private static boolean talk(MinecraftClient mc, VillagerEntity v, String job) {
        Challenge ask = CONTENT.pick("trade:" + job, null, profile, RND);
        if (ask == null) ask = CONTENT.pick("trade:any", null, profile, RND);
        if (ask == null) return false;
        int h = v.getUuid().hashCode();
        String name = v.hasCustomName() ? v.getCustomName().getString() : NAMES[Math.floorMod(h, NAMES.length)];
        String who = name + ", the " + job.replace('_', ' ');
        float pitch = 0.75f + Math.floorMod(h >> 4, 11) * 0.06f;
        Runnable open = () -> {
            UNLOCKED.put(v.getUuid(), System.currentTimeMillis());
            mc.interactionManager.interactEntity(mc.player, v, Hand.MAIN_HAND);
        };
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_AMBIENT, pitch, 1f));
        mc.setScreen(new ChallengeScreen(ask, "trade:" + job, who, pitch, open));
        return true;
    }

    static String label(String source) {
        return switch (source.substring(0, source.indexOf(':') < 0 ? source.length() : source.indexOf(':'))) {
            case "kill" -> "Combat"; case "mine" -> "Mining"; case "sleep" -> "Bedtime";
            case "death" -> "Respawn"; case "biome" -> "Exploring"; case "dim" -> "New world";
            case "lowhp" -> "Danger"; case "dawn" -> "Morning"; case "trade" -> "Villager"; case "suite" -> "Follow-up"; default -> "Review";
        };
    }
}
