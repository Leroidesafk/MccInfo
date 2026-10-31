package leroidesafk.mccinfo.client.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.ClientMannequinAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class NeuNpcs {
	// Entity part of an NPC
	record Part(String type, String nbt, boolean invisible, Vec3 offset, float body, float head) {
	}

	// NPC: name, position and parts
	record Npc(String id, String name, Vec3 position, List<Part> parts) {
	}

	// Entity built for a part
	private record Model(Entity entity, Part part) {
	}

	private record Placed(Model model, Vector3f offset) {
	}

	// Preview drawing
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float MAX_SCALE = 30.0F;
	private static final float MARGIN = 6.0F;
	private static final float Y_OFFSET = 0.0625F;
	private static final float DISPLAY_HALF_SIZE = 0.5F;

	// NPCs and their built entities
	private static final Map<String, Npc> NPCS = new HashMap<>();
	private static final Map<String, List<Model>> MODELS = new HashMap<>();
	private static Level modelsLevel;
	private static int nextId = -1000;

	private NeuNpcs() {
	}

	static void clear() {
		NPCS.clear();
		MODELS.clear();
	}

	// NPCs from the relay
	public static void receive(JsonObject object) {
		if (object.has("reset") && object.get("reset").getAsBoolean()) {
			NPCS.clear();
		}
		MODELS.clear();
		if (!object.has("npcs") || !object.get("npcs").isJsonObject()) {
			return;
		}
		for (Entry<String, JsonElement> entry : object.getAsJsonObject("npcs").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject npc = entry.getValue().getAsJsonObject();
			Vec3 position = vector(npc.get("position"));
			if (position == null || !npc.has("parts") || !npc.get("parts").isJsonArray()) {
				continue;
			}
			List<Part> parts = new ArrayList<>();
			for (JsonElement element : npc.getAsJsonArray("parts")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject part = element.getAsJsonObject();
				Vec3 offset = vector(part.get("offset"));
				if (!part.has("type") || !part.has("nbt") || offset == null) {
					continue;
				}
				parts.add(new Part(part.get("type").getAsString(), part.get("nbt").getAsString(),
						part.has("invisible") && part.get("invisible").getAsBoolean(), offset,
						part.has("body") ? part.get("body").getAsFloat() : 0.0F, part.has("head") ? part.get("head").getAsFloat() : 0.0F));
			}
			if (!parts.isEmpty()) {
				String name = npc.has("name") ? npc.get("name").getAsString() : entry.getKey();
				NPCS.put(entry.getKey(), new Npc(entry.getKey(), name, position, List.copyOf(parts)));
			}
		}
	}

	private static Vec3 vector(JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		return array.size() == 3 ? new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()) : null;
	}

	static Npc get(String id) {
		return id == null ? null : NPCS.get(id);
	}

	// Entities built once per world
	private static List<Model> models(Npc npc) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return List.of();
		}
		if (level != modelsLevel) {
			MODELS.clear();
			modelsLevel = level;
		}
		return MODELS.computeIfAbsent(npc.id(), id -> {
			List<Model> models = new ArrayList<>();
			for (Part part : npc.parts()) {
				Entity entity = create(level, part, id);
				if (entity != null) {
					models.add(new Model(entity, part));
				}
			}
			return models;
		});
	}

	// Entity from its saved data
	private static Entity create(Level level, Part part, String id) {
		try {
			CompoundTag tag = TagParser.parseCompoundFully(part.nbt());
			tag.remove("Passengers");
			tag.remove("CustomName");
			tag.remove("CustomNameVisible");
			tag.remove("UUID");
			Identifier typeId = Identifier.tryParse(part.type());
			EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
			if (type == null) {
				return null;
			}
			Entity entity = EntityType.create(type, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level,
					EntitySpawnReason.LOAD).orElse(null);
			if (entity == null) {
				return null;
			}
			entity.setId(nextId--);
			entity.setInvisible(part.invisible());
			if (entity instanceof net.minecraft.world.entity.Display display) {
				display.tick();
			}
			return entity;
		} catch (CommandSyntaxException | RuntimeException e) {
			Mccinfo.LOGGER.warn("Could not rebuild a part of NPC {}", id, e);
			return null;
		}
	}

	// Mannequin skin once loaded
	private static void applySkin(Entity entity) {
		if (!(entity instanceof ClientMannequin mannequin)) {
			return;
		}
		ClientMannequinAccessor accessor = (ClientMannequinAccessor) mannequin;
		CompletableFuture<Optional<PlayerSkin>> lookup = accessor.mccinfo$getSkinLookup();
		if (lookup == null || !lookup.isDone()) {
			return;
		}
		accessor.mccinfo$setSkinLookup(null);
		if (!lookup.isCompletedExceptionally()) {
			lookup.join().ifPresent(accessor::mccinfo$setSkin);
		}
	}

	// NPC fitted in the box, turned towards the mouse
	static boolean extract(GuiGraphicsExtractor graphics, Npc npc, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
		List<Model> models = models(npc);
		if (models.isEmpty()) {
			return false;
		}
		float lookX = (float) Math.atan(((x0 + x1) / 2.0F - mouseX) / 40.0F);
		float lookY = (float) Math.atan(((y0 + y1) / 2.0F - mouseY) / 40.0F);
		Quaternionf camera = new Quaternionf().rotateX(lookY * 20.0F * Mth.DEG_TO_RAD);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
		float turn = 180.0F + lookX * 20.0F - models.getFirst().part().body();
		float sin = Mth.sin(turn * Mth.DEG_TO_RAD);
		float cos = Mth.cos(turn * Mth.DEG_TO_RAD);

		List<Placed> placed = new ArrayList<>();
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (Model model : models) {
			Vec3 offset = model.part().offset();
			Vector3f turned = new Vector3f((float) (offset.x * cos - offset.z * sin), (float) offset.y, (float) (offset.x * sin + offset.z * cos));
			boolean display = model.entity() instanceof net.minecraft.world.entity.Display;
			float halfWidth = display ? DISPLAY_HALF_SIZE : model.entity().getBbWidth() / 2.0F;
			minX = Math.min(minX, turned.x - halfWidth);
			maxX = Math.max(maxX, turned.x + halfWidth);
			minY = Math.min(minY, display ? turned.y - DISPLAY_HALF_SIZE : turned.y);
			maxY = Math.max(maxY, display ? turned.y + DISPLAY_HALF_SIZE : turned.y + model.entity().getBbHeight());
			placed.add(new Placed(model, turned));
		}
		float scale = Math.min(MAX_SCALE, Math.min((x1 - x0 - MARGIN) / Math.max(maxX - minX, 0.1F), (y1 - y0 - MARGIN) / Math.max(maxY - minY, 0.1F)));
		float centerX = (minX + maxX) / 2.0F;
		float centerY = (minY + maxY) / 2.0F;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		List<NpcGroupRenderer.Entry> entries = new ArrayList<>();
		for (Placed entry : placed) {
			Entity entity = entry.model().entity();
			applySkin(entity);
			Part part = entry.model().part();
			if (!(entity instanceof LivingEntity)) {
				entity.setYRot(part.body() + turn);
				entity.yRotO = entity.getYRot();
			}
			EntityRenderState state = dispatcher.getRenderer(entity).createRenderState(entity, 1.0F);
			state.shadowPieces.clear();
			state.outlineColor = 0;
			state.lightCoords = FULL_BRIGHT;
			if (state instanceof LivingEntityRenderState living) {
				living.bodyRot = part.body() + turn;
				living.yRot = lookX * 20.0F;
				living.xRot = living.pose == Pose.FALL_FLYING ? 0.0F : -lookY * 20.0F;
			}
			Vector3f shift = rotation.transform(new Vector3f(entry.offset().x - centerX, entry.offset().y, entry.offset().z));
			Vector3f translation = new Vector3f(0.0F, centerY + Y_OFFSET, 0.0F).add(shift);
			entries.add(new NpcGroupRenderer.Entry(state, translation));
		}
		((GuiGraphicsExtractorAccessor) graphics).mccinfo$getGuiRenderState()
				.addPicturesInPictureState(new NpcGroupRenderer.State(List.copyOf(entries), rotation, camera, x0, y0, x1, y1, scale));
		return true;
	}
}
