package leroidesafk.mccinfo.client.spots;

import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.Regions;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import leroidesafk.mccinfo.client.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class SpotPath {
	private static final double HIDE_DISTANCE = 10.0;
	private static final double GOAL_DISTANCE = 1.0;
	private static final double GOAL_HEIGHT = 3.0;

	// Search limits
	private static final int NODES_PER_TICK = 3000;
	private static final int MAX_NODES = 60_000;
	private static final int MAX_NODES_LOADED_TARGET = 300_000;
	private static final int REFRESH_TICKS = 10;
	private static final int RETRY_TICKS = 40;
	private static final int ON_PATH_REFRESH_TICKS = 40;
	private static final int HEAVY_PATH_REFRESH_TICKS = 200;
	private static final long STEP_NANOS = 2_000_000L;
	private static final int TIME_CHECK_NODES = 64;
	private static final double OFF_PATH_DISTANCE = 3.0;
	private static final int BLOCK_CACHE_TICKS = 100;
	private static final int MAX_CACHED_BLOCKS = 400_000;
	private static final Long2ByteOpenHashMap BLOCK_KINDS = new Long2ByteOpenHashMap();
	private static final Long2DoubleOpenHashMap BLOCK_TOPS = new Long2DoubleOpenHashMap();
	private static ClientLevel blockCacheLevel;
	private static int blockCacheTicks;

	// Move costs
	private static final double HEURISTIC_WEIGHT = 1.5;
	private static final double DIAGONAL_COST = Math.sqrt(2.0);
	private static final double JUMP_COST = 1.0;
	private static final double MAX_RISE = 1.25;
	private static final double DROP_COST = 0.2;
	private static final double WATER_COST = 0.7;
	private static final double CLIMB_COST = 1.5;
	private static final double SWIM_UP_COST = 1.5;
	private static final double CURRENT_COST = 0.4;
	private static final double SINK_COST = 0.8;
	private static final int MAX_DROP = 48;
	private static final int MAX_START_DROP = 4;
	private static final int MAX_WATER_DEPTH = 8;

	// Ribbon look (changing soon)
	private static final float RIBBON_HALF_WIDTH = 0.15F;
	private static final int SMOOTHING_PASSES = 3;
	private static final double RIBBON_LIFT = 0.05;
	private static final double EDGE_INSET = 0.02;
	private static final int CURVE_STEPS = 4;
	private static final int RIBBON_ALPHA = 0xD0;
	private static final int SEE_THROUGH_ALPHA = 0x40;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float FADE_SECONDS = 0.4F;
	private static final float MAX_FRAME_SECONDS = 0.05F;

	// Path seen through blocks
	private static final RenderPipeline SEE_THROUGH_PIPELINE = RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation(Mccinfo.id("pipeline/path_see_through"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(Optional.empty())
			.withCull(false)
			.build();
	private static final RenderType SEE_THROUGH = RenderTypeAccessor.mccinfo$create("mccinfo_path_see_through",
			RenderSetup.builder(SEE_THROUGH_PIPELINE).sortOnUpload().createRenderSetup());

	// Block kinds
	private static final byte SOLID = 1;
	private static final byte AIR = 2;
	private static final byte WATER = 3;
	private static final byte HAZARD = 4;
	private static final byte CLIMB = 5;
	private static final byte CURRENT_UP = 6;
	private static final byte CURRENT_DOWN = 7;

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	// Path target and its icon
	record Target(Vec3 position, int color, Identifier icon, ItemStack item) {
		Target(Vec3 position, int color) {
			this(position, color, null, null);
		}

		Target(Vec3 position, int color, Identifier icon) {
			this(position, color, icon, null);
		}

		boolean hasIcon() {
			return this.icon != null || this.item != null && !this.item.isEmpty();
		}
	}

	// Waypoint and hover
	private static final int WAYPOINT_ICON_SIZE = 10;
	private static final int WAYPOINT_ICON_TEXTURE = 16;
	private static final double WAYPOINT_HEIGHT = 2.5;
	private static final float HOVER_SCALE = 1.6F;
	private static final float HOVER_SECONDS = 0.15F;
	private static final float HOVER_MARGIN = 2.0F;
	private static final float SPOT_HALF_BOX = 5.0F;
	private static final float ICON_HALF_WIDTH = 6.0F;
	private static final float LABEL_BOTTOM = 4.0F;
	private static final float LOOK_MIN_ALPHA = 0.5F;
	private static final long LOOK_TIMEOUT_NANOS = 150_000_000L;
	private static final int LOOK_CLICK_DELAY = 4;

	private record Node(long position, double cost, double estimate) {
	}

	// Targets and the focused one
	private static final List<Function<Vec3, Target>> TARGETS = new CopyOnWriteArrayList<>();
	private static Target focus;
	private static ClientLevel focusLevel;

	// Current path
	private static Search search;
	private static List<Vec3> path = List.of();
	private static Vec3 pathTarget;
	private static long pathStart;
	private static boolean heavyPath;
	private static int pathColor;
	private static int ticksSincePath;
	private static boolean visible;
	private static float opacity;
	private static long lastFrameNanos;
	// Hovered markers and text displays
	private static final long HOVER_FORGET_NANOS = 1_000_000_000L;
	private static final Map<Vec3, Hover> HOVERS = new HashMap<>();
	private static final float DISPLAY_LINE_HEIGHT = 10.0F;
	private static final float DISPLAY_PIXEL = 0.025F;
	private static final Map<BlockPos, DisplayBox> DISPLAY_BOXES = new HashMap<>();
	private static final Map<BlockPos, Hover> DISPLAY_HOVERS = new HashMap<>();
	// Marker looked at
	private static Target lookedTarget;
	private static Vec3 lookedPosition;
	private static long lookedAtNanos;
	private static Target frameTarget;
	private static Vec3 framePosition;
	private static float frameDistance;
	private static long frame;

	record DisplayBox(Vec3 anchor, float bottom, float height, float width, long nanos) {
	}

	private static final class Hover {
		private float progress;
		private long frameNanos;
		private long drivenFrame;
		private long decayFrame;
	}

	private SpotPath() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpotPath::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SpotPath::collectSubmits);
		HudElementRegistry.addFirst(Mccinfo.id("path_waypoint"), SpotPath::extractWaypoint);
	}

	private static void extractWaypoint(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Target target = focus;
		if (target == null || !target.hasIcon() || !canFocus() || Minecraft.getInstance().level != focusLevel) {
			return;
		}
		SpotMarkers.Projection projection = SpotMarkers.projection();
		if (projection == null) {
			return;
		}
		SpotMarkers.extract(graphics, projection, target.position().add(0.0, WAYPOINT_HEIGHT, 0.0), (g, alpha) -> {
			g.pose().pushMatrix();
			g.pose().translate(-WAYPOINT_ICON_SIZE / 2.0F, -WAYPOINT_ICON_SIZE - 1.0F);
			g.pose().scale(WAYPOINT_ICON_SIZE / (float) WAYPOINT_ICON_TEXTURE, WAYPOINT_ICON_SIZE / (float) WAYPOINT_ICON_TEXTURE);
			if (target.icon() != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, target.icon(), 0, 0, 0.0F, 0.0F, WAYPOINT_ICON_TEXTURE, WAYPOINT_ICON_TEXTURE, WAYPOINT_ICON_TEXTURE,
						WAYPOINT_ICON_TEXTURE, OverlayStyle.withAlpha(0xFFFFFFFF, alpha));
			} else {
				g.item(target.item(), 0, 0);
			}
			g.pose().popMatrix();
		});
	}

	static void addTarget(Function<Vec3, Target> source) {
		TARGETS.add(source);
	}

	public static boolean canFocus() {
		return Permissions.has(Permissions.Perm.UTILS);
	}

	public static boolean isFocused(Vec3 position) {
		return canFocus() && focus != null && focus.position().equals(position);
	}

	// Path focus
	public static void focus(Vec3 position, int color, Identifier icon) {
		focus = new Target(position, color, icon, null);
		focusLevel = Minecraft.getInstance().level;
		resetLook();
	}

	public static void focus(Vec3 position, int color, ItemStack item) {
		focus = new Target(position, color, null, item);
		focusLevel = Minecraft.getInstance().level;
		resetLook();
	}

	public static boolean canPathTo(Vec3 position) {
		Minecraft client = Minecraft.getInstance();
		return canFocus() && client.player != null && Regions.same(position, client.player.position());
	}

	public static void toggleFocus(Vec3 position, int color) {
		if (!isFocused(position) && !canPathTo(position)) {
			return;
		}
		focus = isFocused(position) ? null : new Target(position, color);
		focusLevel = Minecraft.getInstance().level;
		resetLook();
	}

	private static void resetLook() {
		lookedTarget = null;
		lookedPosition = null;
		lookedAtNanos = 0L;
	}

	// Text displays seen this frame
	public static void recordDisplay(Vec3 anchor, int textWidth, int lines, float scaleX, float scaleY, float offsetY) {
		if (!tracksDisplays()) {
			return;
		}
		DISPLAY_BOXES.put(BlockPos.containing(anchor), new DisplayBox(anchor, offsetY, lines * DISPLAY_LINE_HEIGHT * DISPLAY_PIXEL * scaleY,
				(textWidth + 2) * DISPLAY_PIXEL * scaleX, net.minecraft.util.Util.getNanos()));
	}

	static DisplayBox displayBox(Vec3 position) {
		DisplayBox box = DISPLAY_BOXES.get(BlockPos.containing(position));
		return box != null && net.minecraft.util.Util.getNanos() - box.nanos() <= LOOK_TIMEOUT_NANOS ? box : null;
	}

	public static boolean tracksDisplays() {
		return canFocus() && MccServer.onFishing();
	}

	public static float displayScale(Vec3 anchor) {
		if (DISPLAY_HOVERS.isEmpty()) {
			return 1.0F;
		}
		BlockPos key = BlockPos.containing(anchor);
		Hover hover = DISPLAY_HOVERS.get(key);
		if (hover == null) {
			return 1.0F;
		}
		if (frame - hover.drivenFrame > 1 && hover.decayFrame != frame) {
			hover.decayFrame = frame;
			long now = net.minecraft.util.Util.getNanos();
			float seconds = Math.min((now - hover.frameNanos) / 1.0E9F, MAX_FRAME_SECONDS);
			hover.frameNanos = now;
			hover.progress = net.minecraft.util.Mth.clamp(hover.progress - seconds / HOVER_SECONDS, 0.0F, 1.0F);
			if (hover.progress <= 0.0F) {
				DISPLAY_HOVERS.remove(key);
				return 1.0F;
			}
		}
		return scale(hover.progress);
	}

	public static float displayCenter(int lines) {
		return lines * DISPLAY_LINE_HEIGHT * DISPLAY_PIXEL / 2.0F;
	}

	private static void nextFrame() {
		frame++;
		lookedTarget = frameTarget;
		lookedPosition = framePosition;
		if (frameTarget != null) {
			lookedAtNanos = net.minecraft.util.Util.getNanos();
		}
		frameTarget = null;
		framePosition = null;
	}

	private static Vec3 markerPosition(Target target) {
		return target.hasIcon() ? target.position().add(0.0, WAYPOINT_HEIGHT, 0.0) : target.position();
	}

	// Marker size with hover and distance
	static float markerScale(Vec3 position, Target clickTarget, float alpha, double distance, float offsetX, float offsetY, float[] display) {
		Target target = focus;
		boolean waypoint = target != null && target.hasIcon() && position.equals(markerPosition(target));
		Minecraft client = Minecraft.getInstance();
		if (!canFocus() || client.player == null || !waypoint && (clickTarget == null || clickTarget.position().distanceTo(client.player.position()) <= HIDE_DISTANCE
				|| !Regions.same(clickTarget.position(), client.player.position()))) {
			return 1.0F;
		}
		Hover hover = HOVERS.computeIfAbsent(position, key -> new Hover());
		float scale = scale(hover.progress);
		boolean hit;
		float centerX = offsetX;
		float centerY = offsetY;
		if (display != null) {
			centerX = display[0];
			centerY = display[1];
			hit = distance >= SpotMarkers.FADE_END && Math.abs(centerX) <= display[2] * scale + HOVER_MARGIN && Math.abs(centerY) <= display[3] * scale + HOVER_MARGIN;
		} else {
			float x = offsetX / scale;
			float y = offsetY / scale;
			hit = alpha >= LOOK_MIN_ALPHA && (waypoint
					? Math.abs(x) <= ICON_HALF_WIDTH + HOVER_MARGIN && y >= -WAYPOINT_ICON_SIZE - 1.0F - HOVER_MARGIN && y <= LABEL_BOTTOM + HOVER_MARGIN
					: Math.abs(x) <= SPOT_HALF_BOX + HOVER_MARGIN && Math.abs(y) <= SPOT_HALF_BOX + HOVER_MARGIN);
		}
		if (hit && client.gui.screen() == null) {
			float centerDistance = centerX * centerX + centerY * centerY;
			if (framePosition == null || centerDistance < frameDistance) {
				framePosition = position;
				frameTarget = waypoint ? target : clickTarget;
				frameDistance = centerDistance;
			}
		}
		boolean looked = position.equals(lookedPosition);
		long now = net.minecraft.util.Util.getNanos();
		float seconds = hover.frameNanos == 0L ? 0.0F : Math.min((now - hover.frameNanos) / 1.0E9F, MAX_FRAME_SECONDS);
		hover.frameNanos = now;
		hover.progress = net.minecraft.util.Mth.clamp(hover.progress + (looked ? seconds : -seconds) / HOVER_SECONDS, 0.0F, 1.0F);
		if (display != null) {
			hover.drivenFrame = frame;
			DISPLAY_HOVERS.put(BlockPos.containing(position), hover);
		}
		return scale(hover.progress);
	}

	private static float scale(float progress) {
		return 1.0F + (HOVER_SCALE - 1.0F) * eased(progress);
	}

	private static float eased(float progress) {
		return progress * progress * (3.0F - 2.0F * progress);
	}

	// Right click on the looked marker
	public static int clickLookedAt() {
		Minecraft client = Minecraft.getInstance();
		Target looked = lookedTarget;
		if (!canFocus() || looked == null || client.gui.screen() != null || client.player == null
				|| net.minecraft.util.Util.getNanos() - lookedAtNanos > LOOK_TIMEOUT_NANOS) {
			return 0;
		}
		if (focus != null && focus.position().equals(looked.position())) {
			focus = null;
		} else {
			focus = new Target(looked.position(), looked.color());
			focusLevel = client.level;
		}
		resetLook();
		return LOOK_CLICK_DELAY;
	}

	static void unfocus(Vec3 position) {
		if (focus != null && focus.position().equals(position)) {
			focus = null;
		}
	}

	private static void clear() {
		search = null;
		path = List.of();
		pathTarget = null;
		opacity = 0.0F;
	}

	// Path updates each tick
	private static void tick(Minecraft client) {
		long now = net.minecraft.util.Util.getNanos();
		HOVERS.values().removeIf(hover -> now - hover.frameNanos > HOVER_FORGET_NANOS);
		DISPLAY_HOVERS.values().removeIf(hover -> now - hover.frameNanos > HOVER_FORGET_NANOS);
		DISPLAY_BOXES.values().removeIf(box -> now - box.nanos() > HOVER_FORGET_NANOS);
		if (!canFocus() || client.player == null || client.level == null) {
			focus = null;
			clear();
			return;
		}
		// Block cache reset
		if (client.level != blockCacheLevel || ++blockCacheTicks >= BLOCK_CACHE_TICKS || BLOCK_KINDS.size() > MAX_CACHED_BLOCKS) {
			blockCacheLevel = client.level;
			blockCacheTicks = 0;
			BLOCK_KINDS.clear();
			BLOCK_TOPS.clear();
		}
		Vec3 playerPosition = client.player.position();
		if (focus != null && (focusLevel != client.level || focus.position().distanceTo(playerPosition) <= HIDE_DISTANCE
				|| !Regions.same(focus.position(), playerPosition))) {
			focus = null;
		}
		// Focused spot, else nearest
		Target target = focus;
		if (target == null) {
			for (Function<Vec3, Target> source : TARGETS) {
				target = nearer(playerPosition, target, source.apply(playerPosition));
			}
		}
		if (target == null || target.position().distanceTo(playerPosition) <= HIDE_DISTANCE) {
			visible = false;
			search = null;
			ticksSincePath = RETRY_TICKS;
			if (target == null && (opacity <= 0.0F || path.isEmpty())) {
				clear();
			}
			return;
		}
		visible = true;
		pathColor = target.color();
		if (!target.position().equals(pathTarget)) {
			clear();
			pathTarget = target.position();
			ticksSincePath = RETRY_TICKS;
		}

		// Running search
		if (search != null) {
			if (search.level != client.level) {
				search = null;
			} else if (search.step(NODES_PER_TICK)) {
				path = search.result();
				pathStart = search.start;
				heavyPath = search.expanded > MAX_NODES;
				search = null;
				ticksSincePath = 0;
			}
			return;
		}
		ticksSincePath++;
		long start = standingPosition(client.level, client.player);
		if (start == Long.MIN_VALUE) {
			return;
		}
		// New search when needed
		if (path.isEmpty() ? ticksSincePath >= RETRY_TICKS : ticksSincePath >= REFRESH_TICKS && start != pathStart
				&& (ticksSincePath >= (heavyPath ? HEAVY_PATH_REFRESH_TICKS : ON_PATH_REFRESH_TICKS) || offPath(start))) {
			search = new Search(client.level, start, target.position());
		}
	}

	private static boolean offPath(long start) {
		double x = BlockPos.getX(start) + 0.5;
		double y = BlockPos.getY(start);
		double z = BlockPos.getZ(start) + 0.5;
		double limit = OFF_PATH_DISTANCE * OFF_PATH_DISTANCE;
		for (Vec3 point : path) {
			double dx = point.x - x;
			double dy = point.y - y;
			double dz = point.z - z;
			if (dx * dx + dy * dy + dz * dz <= limit) {
				return false;
			}
		}
		return true;
	}

	private static Target nearer(Vec3 from, Target first, Target second) {
		if (first == null || second == null) {
			return first == null ? second : first;
		}
		return first.position().distanceToSqr(from) <= second.position().distanceToSqr(from) ? first : second;
	}

	// Block the player stands on
	private static long standingPosition(ClientLevel level, Player player) {
		Search probe = new Search(level, 0L, Vec3.ZERO);
		if (player.onGround()) {
			BlockPos on = player.getOnPos().above();
			if (probe.standable(on.getX(), on.getY(), on.getZ())) {
				return on.asLong();
			}
		}
		BlockPos feet = player.blockPosition();
		if (probe.kind(feet.getX(), feet.getY(), feet.getZ()) == SOLID && probe.standable(feet.getX(), feet.getY() + 1, feet.getZ())) {
			return BlockPos.asLong(feet.getX(), feet.getY() + 1, feet.getZ());
		}
		for (int drop = 0; drop <= MAX_START_DROP; drop++) {
			if (probe.standable(feet.getX(), feet.getY() - drop, feet.getZ())) {
				return BlockPos.asLong(feet.getX(), feet.getY() - drop, feet.getZ());
			}
		}
		return Long.MIN_VALUE;
	}

	private static final class Search {
		private final ClientLevel level;
		private final long start;
		private final Vec3 target;
		private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::estimate).thenComparingDouble(node -> -node.cost()));
		private final Long2DoubleOpenHashMap costs = new Long2DoubleOpenHashMap();
		private final Long2LongOpenHashMap parents = new Long2LongOpenHashMap();
		private final LongOpenHashSet closed = new LongOpenHashSet();
		private final Long2ByteOpenHashMap kinds = BLOCK_KINDS;
		private final Long2DoubleOpenHashMap tops = BLOCK_TOPS;
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private long best;
		private double bestDistance = Double.MAX_VALUE;
		private int expanded;
		private boolean reached;
		private boolean exhausted;
		private final boolean targetLoaded;

		private Search(ClientLevel level, long start, Vec3 target) {
			this.level = level;
			this.start = start;
			this.target = target;
			this.targetLoaded = level.hasChunkAt(BlockPos.containing(target));
			this.best = start;
			this.costs.defaultReturnValue(Double.MAX_VALUE);
			this.tops.defaultReturnValue(1.0);
			this.costs.put(start, 0.0);
			this.open.add(new Node(start, 0.0, 0.0));
		}

		private boolean step(int budget) {
			long deadline = System.nanoTime() + STEP_NANOS;
			int checked = 0;
			while (budget > 0) {
				if (++checked % TIME_CHECK_NODES == 0 && System.nanoTime() > deadline) {
					return false;
				}
				Node node = this.open.poll();
				if (node == null) {
					this.exhausted = true;
					return true;
				}
				if (!this.closed.add(node.position)) {
					continue;
				}
				budget--;
				int x = BlockPos.getX(node.position);
				int y = BlockPos.getY(node.position);
				int z = BlockPos.getZ(node.position);
				double distance = distanceToTarget(x, y, z);
				if (distance < this.bestDistance) {
					this.bestDistance = distance;
					this.best = node.position;
				}
				if (atGoal(x, y, z)) {
					this.reached = true;
					return true;
				}
				if (++this.expanded >= (this.targetLoaded ? MAX_NODES_LOADED_TARGET : MAX_NODES)) {
					return true;
				}
				for (int[] direction : DIRECTIONS) {
					expand(node, x, y, z, direction[0], direction[1]);
				}
				vertical(node, x, y, z);
			}
			return false;
		}

		private void expand(Node node, int x, int y, int z, int dx, int dz) {
			boolean diagonal = dx != 0 && dz != 0;
			if (diagonal && !(clear(x + dx, y, z) && clear(x, y, z + dz))) {
				return;
			}
			int targetX = x + dx;
			int targetZ = z + dz;
			double cost = diagonal ? DIAGONAL_COST : 1.0;
			int targetY = y;
			// Walk, drop or jump, Inspired by NEU thx Moulberry
			if (standable(targetX, y, targetZ)) {
			} else if (clear(targetX, y, targetZ)) {
				targetY = Integer.MIN_VALUE;
				for (int drop = 1; drop <= MAX_DROP && passable(kind(targetX, y - drop, targetZ)); drop++) {
					if (standable(targetX, y - drop, targetZ)) {
						targetY = y - drop;
						cost += DROP_COST * drop;
						break;
					}
				}
				if (targetY == Integer.MIN_VALUE) {
					return;
				}
			} else if (!diagonal && passable(kind(x, y + 2, z)) && standable(targetX, y + 1, targetZ)) {
				targetY = y + 1;
				cost += JUMP_COST;
			} else {
				return;
			}
			if (floor(targetX, targetY, targetZ) - floor(x, y, z) > MAX_RISE) {
				return;
			}
			if (isWater(kind(targetX, targetY, targetZ))) {
				cost *= WATER_COST;
			}
			push(node, targetX, targetY, targetZ, cost);
		}

		private void vertical(Node node, int x, int y, int z) {
			byte feet = kind(x, y, z);
			if ((feet == CLIMB || feet == WATER || feet == CURRENT_UP) && standable(x, y + 1, z)) {
				push(node, x, y + 1, z, feet == CURRENT_UP ? CURRENT_COST : feet == CLIMB ? CLIMB_COST : SWIM_UP_COST);
			}
			if ((feet == CLIMB || isWater(feet)) && kind(x, y - 1, z) != SOLID && standable(x, y - 1, z)) {
				push(node, x, y - 1, z, feet == CURRENT_DOWN ? CURRENT_COST : SINK_COST);
			}
		}

		private void push(Node node, int targetX, int targetY, int targetZ, double cost) {
			long position = BlockPos.asLong(targetX, targetY, targetZ);
			double total = node.cost + cost;
			if (this.closed.contains(position) || total >= this.costs.get(position)) {
				return;
			}
			this.costs.put(position, total);
			this.parents.put(position, node.position);
			this.open.add(new Node(position, total, total + heuristic(targetX, targetY, targetZ) * HEURISTIC_WEIGHT));
		}

		// Octile distance, height at its cheapest
		private double heuristic(int x, int y, int z) {
			double dx = Math.abs(x + 0.5 - this.target.x);
			double dz = Math.abs(z + 0.5 - this.target.z);
			double dy = y - this.target.y;
			double flat = Math.max(dx, dz) + (DIAGONAL_COST - 1.0) * Math.min(dx, dz);
			return flat + (dy > 0 ? dy * DROP_COST : -dy * CURRENT_COST);
		}

		private double distanceToTarget(int x, int y, int z) {
			double dx = x + 0.5 - this.target.x;
			double dy = y - this.target.y;
			double dz = z + 0.5 - this.target.z;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		private boolean atGoal(int x, int y, int z) {
			double dx = x + 0.5 - this.target.x;
			double dz = z + 0.5 - this.target.z;
			return dx * dx + dz * dz <= GOAL_DISTANCE * GOAL_DISTANCE && Math.abs(y - this.target.y) <= GOAL_HEIGHT;
		}

		private byte kind(int x, int y, int z) {
			long position = BlockPos.asLong(x, y, z);
			byte kind = this.kinds.get(position);
			if (kind == 0) {
				BlockState state = this.level.getBlockState(this.cursor.set(x, y, z));
				FluidState fluid = state.getFluidState();
				VoxelShape shape = state.getCollisionShape(this.level, this.cursor);
				if (state.is(BlockTags.CLIMBABLE)) {
					kind = CLIMB;
				} else if (!shape.isEmpty()) {
					kind = SOLID;
					double top = shape.max(Direction.Axis.Y);
					if (top != 1.0) {
						this.tops.put(position, top);
					}
				} else if (fluid.isEmpty()) {
					kind = AIR;
				} else {
					kind = !fluid.is(FluidTags.WATER) ? HAZARD : !state.is(Blocks.BUBBLE_COLUMN) ? WATER
							: state.getValue(BubbleColumnBlock.DRAG_DOWN) ? CURRENT_DOWN : CURRENT_UP;
				}
				this.kinds.put(position, kind);
			}
			return kind;
		}

		private double floor(int x, int y, int z) {
			byte kind = kind(x, y, z);
			if (isWater(kind) || kind == CLIMB) {
				return y;
			}
			kind(x, y - 1, z);
			return y - 1 + this.tops.get(BlockPos.asLong(x, y - 1, z));
		}

		private static boolean isWater(byte kind) {
			return kind == WATER || kind == CURRENT_UP || kind == CURRENT_DOWN;
		}

		private static boolean passable(byte kind) {
			return kind == AIR || kind == CLIMB || isWater(kind);
		}

		private boolean clear(int x, int y, int z) {
			return passable(kind(x, y, z)) && passable(kind(x, y + 1, z));
		}

		private boolean standable(int x, int y, int z) {
			if (!clear(x, y, z)) {
				return false;
			}
			byte feet = kind(x, y, z);
			return isWater(feet) || feet == CLIMB || kind(x, y - 1, z) == SOLID;
		}

		private List<Vec3> result() {
			if (!this.reached && (this.exhausted || this.targetLoaded)) {
				return List.of();
			}
			List<Vec3> nodes = new ArrayList<>();
			for (long position = this.best; ; position = this.parents.get(position)) {
				nodes.add(groundPoint(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position)));
				if (position == this.start) {
					break;
				}
			}
			Collections.reverse(nodes);
			if (nodes.size() < 2) {
				return List.of();
			}
			// Curves on height changes
			List<Vec3> points = new ArrayList<>();
			List<Vec3> run = new ArrayList<>();
			run.add(nodes.getFirst());
			for (int i = 1; i < nodes.size(); i++) {
				Vec3 from = nodes.get(i - 1);
				Vec3 to = nodes.get(i);
				if (Math.abs(to.y - from.y) > 1.0E-3) {
					boolean climb = to.y > from.y;
					Vec3 lower = climb ? from : to;
					Vec3 higher = climb ? to : from;
					Vec3 edge = new Vec3(
						(from.x + to.x) / 2 + (lower.x - higher.x) * EDGE_INSET,
						higher.y,
						(from.z + to.z) / 2 + (lower.z - higher.z) * EDGE_INSET
					);
					Vec3 control = new Vec3(lower.x, higher.y, lower.z);
					if (climb) {
						for (int step = 1; step < CURVE_STEPS; step++) {
							run.add(curvePoint(lower, control, edge, (double) step / CURVE_STEPS));
						}
						run = corner(points, run, edge);
					} else {
						run = corner(points, run, edge);
						for (int step = 1; step < CURVE_STEPS; step++) {
							run.add(curvePoint(edge, control, lower, (double) step / CURVE_STEPS));
						}
					}
				}
				run.add(to);
			}
			appendSmoothed(points, run);
			return points;
		}

		private static Vec3 curvePoint(Vec3 from, Vec3 control, Vec3 to, double t) {
			return from.lerp(control, t).lerp(control.lerp(to, t), t);
		}

		private static List<Vec3> corner(List<Vec3> points, List<Vec3> run, Vec3 point) {
			run.add(point);
			appendSmoothed(points, run);
			List<Vec3> next = new ArrayList<>();
			next.add(point);
			return next;
		}

		private static void appendSmoothed(List<Vec3> points, List<Vec3> run) {
			List<Vec3> smoothed = smooth(run);
			points.addAll(points.isEmpty() ? smoothed : smoothed.subList(1, smoothed.size()));
		}

		private Vec3 groundPoint(int x, int y, int z) {
			double groundY;
			byte kind = kind(x, y, z);
			if (isWater(kind)) {
				int surface = y;
				while (surface - y < MAX_WATER_DEPTH && isWater(kind(x, surface + 1, z))) {
					surface++;
				}
				groundY = surface + 1.0;
			} else if (kind == CLIMB) {
				groundY = y;
			} else {
				this.cursor.set(x, y - 1, z);
				groundY = y - 1 + this.level.getBlockState(this.cursor).getCollisionShape(this.level, this.cursor).max(Direction.Axis.Y);
			}
			return new Vec3(x + 0.5, groundY + RIBBON_LIFT, z + 0.5);
		}
	}

	// Smoothed path points
	private static List<Vec3> smooth(List<Vec3> points) {
		for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
			List<Vec3> smoothed = new ArrayList<>(points.size() * 2);
			smoothed.add(points.getFirst());
			for (int i = 0; i < points.size() - 1; i++) {
				Vec3 a = points.get(i);
				Vec3 b = points.get(i + 1);
				smoothed.add(a.lerp(b, 0.25));
				smoothed.add(a.lerp(b, 0.75));
			}
			smoothed.add(points.getLast());
			points = smoothed;
		}
		return points;
	}

	// Path ribbon in the world
	private static void collectSubmits(LevelRenderContext context) {
		nextFrame();
		List<Vec3> points = path;
		Vec3 target = pathTarget;
		if (points.isEmpty() || target == null || !canFocus()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || !camera.initialized) {
			return;
		}
		long now = System.nanoTime();
		float elapsed = Math.min((now - lastFrameNanos) / 1.0E9F, MAX_FRAME_SECONDS);
		lastFrameNanos = now;
		opacity = Math.clamp(opacity + (visible ? elapsed : -elapsed) / FADE_SECONDS, 0.0F, 1.0F);
		if (opacity <= 0.0F) {
			return;
		}
		// Start at the closest point
		int first = 0;
		for (int i = 1; i < points.size(); i++) {
			if (points.get(i).distanceToSqr(camera.pos) < points.get(first).distanceToSqr(camera.pos)) {
				first = i;
			}
		}
		int count = points.size() - first;
		if (count < 2) {
			return;
		}
		float[] left = new float[count * 3];
		float[] right = new float[count * 3];
		float[] fade = new float[count];
		double endDistance = points.getLast().distanceTo(target);
		float sideX = 0.0F;
		float sideZ = 0.0F;
		// Ribbon edges and fade
		for (int i = 0; i < count; i++) {
			Vec3 point = points.get(first + i);
			Vec3 previous = points.get(first + Math.max(i - 1, 0));
			Vec3 next = points.get(first + Math.min(i + 1, count - 1));
			double dx = next.x - previous.x;
			double dz = next.z - previous.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length > 1.0E-4) {
				sideX = (float) (-dz / length) * RIBBON_HALF_WIDTH;
				sideZ = (float) (dx / length) * RIBBON_HALF_WIDTH;
			}
			float x = (float) (point.x - camera.pos.x);
			float y = (float) (point.y - camera.pos.y);
			float z = (float) (point.z - camera.pos.z);
			left[i * 3] = x - sideX;
			left[i * 3 + 1] = y;
			left[i * 3 + 2] = z - sideZ;
			right[i * 3] = x + sideX;
			right[i * 3 + 1] = y;
			right[i * 3 + 2] = z + sideZ;
			fade[i] = opacity * (endDistance >= HIDE_DISTANCE ? 1.0F
					: (float) Math.clamp((point.distanceTo(target) - endDistance) / (HIDE_DISTANCE - endDistance), 0.0, 1.0));
		}
		submitRibbon(context, RenderTypes.debugQuads(), left, right, fade, RIBBON_ALPHA, pathColor);
		submitRibbon(context, SEE_THROUGH, left, right, fade, SEE_THROUGH_ALPHA, pathColor);
	}

	private static void submitRibbon(LevelRenderContext context, RenderType renderType, float[] left, float[] right, float[] fade, int alpha, int rgb) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), renderType, (pose, buffer) -> {
			for (int a = 0; a + 3 < left.length; a += 3) {
				int b = a + 3;
				int colorA = Math.round(alpha * fade[a / 3]) << 24 | rgb;
				int colorB = Math.round(alpha * fade[b / 3]) << 24 | rgb;
				vertex(buffer, pose, left, a, colorA);
				vertex(buffer, pose, right, a, colorA);
				vertex(buffer, pose, right, b, colorB);
				vertex(buffer, pose, left, b, colorB);
				vertex(buffer, pose, left, b, colorB);
				vertex(buffer, pose, right, b, colorB);
				vertex(buffer, pose, right, a, colorA);
				vertex(buffer, pose, left, a, colorA);
			}
		});
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] points, int index, int color) {
		buffer.addVertex(pose, points[index], points[index + 1], points[index + 2]).setColor(color).setLight(FULL_BRIGHT);
	}
}
