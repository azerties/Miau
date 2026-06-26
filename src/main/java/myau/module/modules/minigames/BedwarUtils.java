package myau.module.modules.minigames;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.impl.LoadWorldEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.Render2DEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.client.ChatUtil;
import myau.util.client.SoundUtil;
import myau.util.player.TeamUtil;
import myau.util.render.ColorUtil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class BedwarUtils extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty hud = new BooleanProperty("hud", true);
  public final IntProperty hudX = new IntProperty("hud-x", 4, 0, 500, this.hud::getValue);
  public final IntProperty hudY = new IntProperty("hud-y", 66, 0, 500, this.hud::getValue);
  public final FloatProperty hudScale =
      new FloatProperty("hud-scale", 1.0F, 0.5F, 2.0F, this.hud::getValue);
  public final BooleanProperty hudShadow =
      new BooleanProperty("hud-shadow", true, this.hud::getValue);
  public final BooleanProperty diamondUpgrades = new BooleanProperty("diamond-upgrades", true);
  public final BooleanProperty itemTracker = new BooleanProperty("item-tracker", true);
  public final BooleanProperty itemAlerts = new BooleanProperty("item-alerts", true);
  public final BooleanProperty chatAlerts =
      new BooleanProperty("chat-alerts", true, this.itemAlerts::getValue);
  public final BooleanProperty hudAlerts =
      new BooleanProperty("hud-alerts", true, this.itemAlerts::getValue);
  public final FloatProperty hudAlertDuration =
      new FloatProperty("hud-duration", 5.0F, 0.0F, 10.0F, this.hudAlerts::getValue);
  public final IntProperty alertDelay =
      new IntProperty("alert-delay", 15, 0, 60, this.itemAlerts::getValue);
  public final BooleanProperty trackTeammates =
      new BooleanProperty("track-teammates", false, this.itemAlerts::getValue);
  public final BooleanProperty trackBow =
      new BooleanProperty("track-bow", true, this.itemAlerts::getValue);
  public final BooleanProperty trackPotions =
      new BooleanProperty("track-potions", true, this.itemAlerts::getValue);
  public final BooleanProperty trackSpecials =
      new BooleanProperty("track-specials", true, this.itemAlerts::getValue);
  public final BooleanProperty trackUpgrades =
      new BooleanProperty("track-upgrades", true, this.itemAlerts::getValue);
  public final BooleanProperty bedTracker = new BooleanProperty("bedtracker", true);
  public final BooleanProperty invisAlert = new BooleanProperty("invis-alert", true);
  public final BooleanProperty diamondArmor = new BooleanProperty("diamond-armor", true);
  public final BooleanProperty fireballAlert = new BooleanProperty("fireball-alert", false);
  public final BooleanProperty enderPearlAlert = new BooleanProperty("ender-pearl-alert", true);
  public final BooleanProperty obsidianAlert = new BooleanProperty("obsidian-alert", true);
  public final BooleanProperty alertSound = new BooleanProperty("alert-sound", true);
  public final BooleanProperty alertDistance = new BooleanProperty("alert-distance", true);

  public enum TeamBedColor {
    RED('c', 14, "RED"),
    GREEN('a', 13, "GREEN"),
    BLUE('9', 11, "BLUE"),
    YELLOW('e', 4, "YELLOW"),
    AQUA('b', 3, "AQUA"),
    WHITE('f', 0, "WHITE"),
    PINK('d', 6, "PINK"),
    GRAY('7', 8, "GRAY");

    public final char colorCode;

    /** Wool metadata color, also used for bed identification in 1.8.9 */
    public final int woolMeta;

    public final String name;

    TeamBedColor(char colorCode, int woolMeta, String name) {
      this.colorCode = colorCode;
      this.woolMeta = woolMeta;
      this.name = name;
    }

    public static TeamBedColor fromColorCode(char code) {
      for (TeamBedColor c : values()) {
        if (c.colorCode == code) return c;
      }
      return null;
    }
  }

  // Detect own team bed color from scoreboard teams on Hypixel
  public static TeamBedColor detectOwnTeamColor() {
    if (mc.thePlayer == null || mc.getNetHandler() == null) return null;
    NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
    if (selfInfo == null) return null;
    net.minecraft.scoreboard.ScorePlayerTeam team = selfInfo.getPlayerTeam();
    if (team == null) return null;
    String prefix = team.getColorPrefix();
    if (prefix == null || prefix.length() < 2) return null;
    char code = prefix.charAt(1);
    return TeamBedColor.fromColorCode(code);
  }

  // Get the own bed position from the BedTracker (if available)
  // Populated when BedwarUtils is enabled and bedTracker is on
  private static BlockPos trackedOwnBed = null;

  public static BlockPos getTrackedOwnBed() {
    return trackedOwnBed;
  }

  private void updateTrackedOwnBed() {
    if (this.bedTracker.getValue() && this.isEnabled()) {
      BlockPos pos = this.bedTrackerDelegate.getBedPos();
      if (this.bedTrackerDelegate.isBed(pos)) {
        trackedOwnBed = pos;
        return;
      }
    }
    trackedOwnBed = null;
  }

  public final BooleanProperty bedTrackerAlerts;
  public final IntProperty bedTrackerAlertRange;
  public final BooleanProperty bedTrackerAlertOnPearl;
  public final ModeProperty bedTrackerAlertSound;
  public final IntProperty bedTrackerAlertFrequency;
  public final BooleanProperty bedTrackerAutoInc;
  public final BooleanProperty bedTrackerMacro;
  public final IntProperty bedTrackerMacroRange;
  public final BooleanProperty bedTrackerMacroOnPearl;
  public final TextProperty bedTrackerMacroText;
  public final IntProperty bedTrackerMacroDelay;
  public final BooleanProperty bedTrackerHud;
  public final ModeProperty bedTrackerHudPosX;
  public final ModeProperty bedTrackerHudPosY;
  public final IntProperty bedTrackerHudOffX;
  public final IntProperty bedTrackerHudOffY;
  public final FloatProperty bedTrackerHudScale;
  public final BooleanProperty bedTrackerHudShadow;

  private static final Pattern ITEM_TRACKER_PATTERN =
      Pattern.compile("(.+?)\\s+has\\s+(?:an?\\s+)?(.+?)(?:[.!])?$", Pattern.CASE_INSENSITIVE);
  private final Set<String> trackedItemMessages = new HashSet<>();
  private final BedTracker bedTrackerDelegate;

  private boolean trap;
  private String trapType = "";
  private boolean sharp;
  private int protLevel;
  private final LinkedHashMap<String, Long> invisAlertCooldowns = new LinkedHashMap<>();
  private final Set<String> armoredPlayers = new HashSet<>();
  private final Map<String, String> lastHeldMap = new ConcurrentHashMap<>();
  private final Map<BlockPos, Long> trackedObsidian = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Long>> alertCooldowns = new ConcurrentHashMap<>();
  private final Set<String> trackedTeamUpgrades = new HashSet<>();
  private final Map<String, String> itemDisplayColors = new HashMap<>();

  public BedwarUtils() {
    super("BedwarUtils", false, false);
    this.bedTrackerDelegate = new BedTracker();
    this.bedTrackerAlerts = this.bedTrackerDelegate.alerts;
    this.bedTrackerAlertRange = this.bedTrackerDelegate.alertRange;
    this.bedTrackerAlertOnPearl = this.bedTrackerDelegate.alertOnPearl;
    this.bedTrackerAlertSound = this.bedTrackerDelegate.alertSound;
    this.bedTrackerAlertFrequency = this.bedTrackerDelegate.alertFrequency;
    this.bedTrackerAutoInc = this.bedTrackerDelegate.autoInc;
    this.bedTrackerMacro = this.bedTrackerDelegate.marco;
    this.bedTrackerMacroRange = this.bedTrackerDelegate.marcoRange;
    this.bedTrackerMacroOnPearl = this.bedTrackerDelegate.marcoOnPreal;
    this.bedTrackerMacroText = this.bedTrackerDelegate.marcoText;
    this.bedTrackerMacroDelay = this.bedTrackerDelegate.marcoDelay;
    this.bedTrackerHud = this.bedTrackerDelegate.hud;
    this.bedTrackerHudPosX = this.bedTrackerDelegate.hudPosX;
    this.bedTrackerHudPosY = this.bedTrackerDelegate.hudPosY;
    this.bedTrackerHudOffX = this.bedTrackerDelegate.hudOffX;
    this.bedTrackerHudOffY = this.bedTrackerDelegate.hudOffY;
    this.bedTrackerHudScale = this.bedTrackerDelegate.hudScale;
    this.bedTrackerHudShadow = this.bedTrackerDelegate.hudShadow;
    this.bedTrackerDelegate.setEnabled(true);
    this.initItemDisplayColors();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
      return;
    }
    if (event.getPacket() instanceof S02PacketChat) {
      String text = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
      String formattedText =
          ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
      this.scanMessage(text, formattedText);
      if (this.bedTracker.getValue()) {
        this.bedTrackerDelegate.onPacket(event);
      }
    }
    if (this.obsidianAlert.getValue() && event.getPacket() instanceof S23PacketBlockChange) {
      S23PacketBlockChange p = (S23PacketBlockChange) event.getPacket();
      if (p.getBlockState() != null
          && p.getBlockState().getBlock() instanceof BlockObsidian
          && isNextToBed(p.getBlockPosition())) {
        this.trackedObsidian.put(p.getBlockPosition(), System.currentTimeMillis());
        if (mc.thePlayer != null) {
          ChatUtil.display("&7Obsidian placed near bed!");
          if (this.alertSound.getValue()) {
            SoundUtil.playSound("note.pling");
          }
        }
      }
    }
    if (this.itemAlerts.getValue() && event.getPacket() instanceof S04PacketEntityEquipment) {
      this.processEquipmentAlert((S04PacketEntityEquipment) event.getPacket());
    }
  }

  @Override
  public void onEnabled() {
    this.alertCooldowns.clear();
    this.trackedTeamUpgrades.clear();
  }

  @Override
  public void onDisabled() {
    this.alertCooldowns.clear();
    this.trackedTeamUpgrades.clear();
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.reset(false);
    this.alertCooldowns.clear();
    this.trackedTeamUpgrades.clear();
    this.bedTrackerDelegate.onLoadWorld(event);
    trackedOwnBed = null;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.POST
        || mc.theWorld == null
        || mc.thePlayer == null) {
      return;
    }
    if (this.itemTracker.getValue()) {
      for (Object object : mc.theWorld.playerEntities) {
        if (!(object instanceof EntityPlayer)) {
          continue;
        }
        EntityPlayer player = (EntityPlayer) object;
        if (player == mc.thePlayer
            || player.isDead
            || player.getName() == null
            || player.getName().isEmpty()) {
          continue;
        }
        this.scanPlayerItem(player, "held", player.getHeldItem());
        for (int slot = 0; slot < 4; slot++) {
          this.scanPlayerItem(player, "armor-" + slot, player.getCurrentArmor(slot));
        }
      }
    }
    if (this.bedTracker.getValue()) {
      this.bedTrackerDelegate.onTick(event);
    }
    this.updateTrackedOwnBed();
    if (this.invisAlert.getValue()) {
      this.scanInvisiblePlayers();
    }
    this.scanPlayerAlerts();
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || !this.hud.getValue()) {
      return;
    }
    float scale = this.hudScale.getValue();
    float x = this.hudX.getValue() / scale;
    float y = this.hudY.getValue() / scale;
    GlStateManager.pushMatrix();
    GlStateManager.scale(scale, scale, 1.0F);
    float rowY = y;
    if (this.diamondUpgrades.getValue()) {
      this.drawTrapLine(x, rowY);
      rowY += 10.0F;
      this.drawLine("Sharp", this.sharp, -1, x, rowY);
      rowY += 10.0F;
      this.drawLine("Prot", this.protLevel > 0, this.protLevel, x, rowY);
      rowY += 10.0F;
    }
    if (this.bedTracker.getValue()) {
      rowY += this.diamondUpgrades.getValue() ? 20.0F : 0.0F;
      this.drawBedLine(x, rowY);
    }
    GlStateManager.popMatrix();
  }

  private void scanMessage(String text, String formattedText) {
    if (text == null) {
      return;
    }
    String lower = text.toLowerCase();
    if (this.isNewGameMessage(lower)) {
      this.reset(true);
      return;
    }
    if (this.diamondUpgrades.getValue()) {
      if (lower.contains("trap")
          || lower.contains("it's a trap")
          || lower.contains("alarm trap")
          || lower.contains("miner fatigue")) {
        this.trap = true;
        this.trapType = this.parseTrapType(lower);
      }
      if (lower.contains("sharpened swords")
          || lower.contains("sharpness")
          || lower.contains("sharp")) {
        this.sharp = true;
      }
      if (lower.contains("reinforced armor")
          || lower.contains("protection")
          || lower.contains("prot")) {
        int level = this.parseProtLevel(lower);
        this.protLevel = Math.max(this.protLevel, level <= 0 ? 1 : level);
      }
    }
    this.scanItemTracker(text, formattedText);
  }

  private boolean isNewGameMessage(String lower) {
    return lower.contains("protect your bed")
        || lower.contains("you are playing on")
        || lower.contains("the game starts in 1 second")
        || lower.contains("the game has started")
        || lower.contains("bed wars") && lower.contains("protect your bed");
  }

  private void scanPlayerItem(EntityPlayer player, String slot, ItemStack stack) {
    if (stack == null || stack.getItem() == null) {
      return;
    }
    String item =
        this.normalizeItemName(
            EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()));
    if (!this.isTrackedItem(item)) {
      return;
    }
    String key = player.getName().toLowerCase() + ":" + slot + ":" + item.toLowerCase();
    if (!this.trackedItemMessages.add(key)) {
      return;
    }
    this.sendItemTrackerMessage(player.getDisplayName().getFormattedText(), item);
  }

  private void scanItemTracker(String text, String formattedText) {
    if (!this.itemTracker.getValue()) {
      return;
    }
    Matcher matcher = ITEM_TRACKER_PATTERN.matcher(text);
    if (!matcher.find()) {
      return;
    }
    String item = this.normalizeItemName(matcher.group(2).trim());
    if (!this.isTrackedItem(item)) {
      return;
    }
    String key = (matcher.group(1).trim() + " has " + item).toLowerCase();
    if (!this.trackedItemMessages.add(key)) {
      return;
    }
    this.sendItemTrackerMessage(
        this.extractFormattedPlayer(formattedText, matcher.group(1).trim()), item);
  }

  private boolean isTrackedItem(String item) {
    String lower = item.toLowerCase();
    boolean tieredGear =
        (lower.contains("stone") || lower.contains("iron") || lower.contains("diamond"))
            && (lower.contains("sword")
                || lower.contains("armor")
                || lower.contains("chestplate")
                || lower.contains("leggings")
                || lower.contains("boots")
                || lower.contains("helmet")
                || lower.contains("pickaxe")
                || lower.contains("axe"));
    boolean utilityItem =
        lower.contains("bow")
            || lower.contains("shears")
            || lower.contains("fireball")
            || lower.contains("ender pearl")
            || lower.contains("pearl")
            || lower.contains("invisibility")
            || lower.contains("invis")
            || lower.contains("jump")
            || lower.contains("speed");
    return tieredGear || utilityItem;
  }

  private String normalizeItemName(String item) {
    String normalized = item.replaceAll("(?i)^an?\\s+", "").trim();
    if (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1).trim();
    }
    return normalized;
  }

  private String extractFormattedPlayer(String formattedText, String fallback) {
    if (formattedText == null) {
      return fallback;
    }
    String marker = " has ";
    String lowerFormatted = formattedText.toLowerCase();
    int index = lowerFormatted.indexOf(marker);
    if (index < 0) {
      index = lowerFormatted.indexOf(" has an ");
    }
    if (index < 0) {
      index = lowerFormatted.indexOf(" has a ");
    }
    return index > 0 ? formattedText.substring(0, index) : fallback;
  }

  private int parseProtLevel(String text) {
    if (text.contains(" iv")
        || text.contains(" 4")
        || text.contains("level iv")
        || text.contains("level 4")) return 4;
    if (text.contains(" iii")
        || text.contains(" 3")
        || text.contains("level iii")
        || text.contains("level 3")) return 3;
    if (text.contains(" ii")
        || text.contains(" 2")
        || text.contains("level ii")
        || text.contains("level 2")) return 2;
    if (text.contains(" i")
        || text.contains(" 1")
        || text.contains("level i")
        || text.contains("level 1")) return 1;
    return 0;
  }

  private void drawLine(String name, boolean value, int level, float x, float y) {
    int white = 0xFFFFFFFF;
    int green = 0xFF55FF55;
    int red = 0xFFFF5555;
    boolean shadow = this.hudShadow.getValue();
    String prefix = "- " + name + ": ";
    mc.fontRendererObj.drawString(prefix, x, y, white, shadow);
    float valueX = x + mc.fontRendererObj.getStringWidth(prefix);
    mc.fontRendererObj.drawString(value ? "true" : "false", valueX, y, value ? green : red, shadow);
    if (level > 0) {
      String suffix = " [" + this.toRoman(level) + "]";
      mc.fontRendererObj.drawString(
          suffix, valueX + mc.fontRendererObj.getStringWidth("true"), y, white, shadow);
    }
  }

  private void drawBedLine(float x, float y) {
    int white = 0xFFFFFFFF;
    int green = 0xFF55FF55;
    int red = 0xFFFF5555;
    boolean shadow = this.hudShadow.getValue();
    boolean hasBed = this.bedTrackerDelegate.isBed(this.bedTrackerDelegate.getBedPos());
    String prefix = "Bed: ";
    mc.fontRendererObj.drawString(prefix, x, y, white, shadow);
    float valueX = x + mc.fontRendererObj.getStringWidth(prefix);
    mc.fontRendererObj.drawString(
        hasBed ? "true" : "false", valueX, y, hasBed ? green : red, shadow);
  }

  private void scanInvisiblePlayers() {
    BlockPos bed = this.bedTrackerDelegate.getBedPos();
    if (bed == null || mc.theWorld == null || mc.thePlayer == null) return;
    long now = System.currentTimeMillis();
    for (Object object : mc.theWorld.playerEntities) {
      if (!(object instanceof EntityPlayer)) continue;
      EntityPlayer player = (EntityPlayer) object;
      if (player == mc.thePlayer
          || player.isDead
          || player.getName() == null
          || TeamUtil.isSameTeam(player)) continue;
      double distance = player.getDistance(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D);
      if (distance > 18.0D) continue;
      int armorPieces = 0;
      for (int slot = 0; slot < 4; slot++) {
        if (player.getCurrentArmor(slot) != null) armorPieces++;
      }
      boolean suspicious = player.isInvisible() || armorPieces <= 1;
      String key = player.getName().toLowerCase();
      long last = this.invisAlertCooldowns.getOrDefault(key, 0L);
      if (suspicious && now - last > 5000L) {
        this.invisAlertCooldowns.put(key, now);
        ChatUtil.display(
            this.getMyauPrefix()
                + " &cSuspicious/invis player near bed: &f"
                + player.getDisplayName().getFormattedText());
        SoundUtil.playSound("note.pling");
      }
    }
  }

  private void drawTrapLine(float x, float y) {
    int white = 0xFFFFFFFF;
    int green = 0xFF55FF55;
    int red = 0xFFFF5555;
    boolean shadow = this.hudShadow.getValue();
    String prefix = "- Trap: ";
    mc.fontRendererObj.drawString(prefix, x, y, white, shadow);
    float valueX = x + mc.fontRendererObj.getStringWidth(prefix);
    String value = this.trap ? (this.trapType.isEmpty() ? "Unknown" : this.trapType) : "false";
    mc.fontRendererObj.drawString(value, valueX, y, this.trap ? green : red, shadow);
  }

  private String parseTrapType(String lower) {
    if (lower.contains("alarm")) return "Alarm";
    if (lower.contains("miner fatigue") || lower.contains("miner")) return "Miner Fatigue";
    if (lower.contains("counter-offensive")
        || lower.contains("counter offensive")
        || lower.contains("counter")) return "Counter-Offensive";
    if (lower.contains("it's a trap") || lower.contains("its a trap")) return "It's a Trap";
    return "Unknown";
  }

  private String toRoman(int level) {
    switch (level) {
      case 1:
        return "I";
      case 2:
        return "II";
      case 3:
        return "III";
      case 4:
        return "IV";
      default:
        return String.valueOf(level);
    }
  }

  private void reset(boolean resetDiamondUpgrades) {
    if (resetDiamondUpgrades) {
      this.trap = false;
      this.trapType = "";
      this.sharp = false;
      this.protLevel = 0;
    }
    this.trackedItemMessages.clear();
    this.invisAlertCooldowns.clear();
    this.armoredPlayers.clear();
    this.lastHeldMap.clear();
    this.trackedObsidian.clear();
    this.alertCooldowns.clear();
    this.trackedTeamUpgrades.clear();
  }

  private void sendItemTrackerMessage(String formattedPlayer, String item) {
    if (mc.thePlayer == null) {
      return;
    }
    mc.thePlayer.addChatMessage(
        new ChatComponentText(this.getMyauPrefix() + " §f" + formattedPlayer + " §fhas §a" + item));
  }

  private String getMyauPrefix() {
    return ChatColors.formatColor(Myau.clientName).trim();
  }

  private void scanPlayerAlerts() {
    if (!this.fireballAlert.getValue() && !this.enderPearlAlert.getValue()) {
      return;
    }
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == null || player == mc.thePlayer || player.isDead) continue;
      String name = player.getName();
      if (name == null) continue;

      ItemStack held = player.getHeldItem();
      String itemType = this.getAlertItemType(held);
      if (itemType != null) {
        if (!this.lastHeldMap.containsKey(name)) {
          this.lastHeldMap.put(name, itemType);
          this.sendAlert(player.getDisplayName().getFormattedText(), itemType, player);
        } else {
          String prevItemType = this.lastHeldMap.get(name);
          if (!prevItemType.equals(this.getAlertItemType(held))) {
            this.lastHeldMap.remove(name);
          }
        }
      }
    }
  }

  private String getAlertItemType(ItemStack item) {
    if (item == null || item.getItem() == null) return null;
    String unlocalizedName = item.getItem().getUnlocalizedName();
    if (item.getItem() instanceof ItemEnderPearl && this.enderPearlAlert.getValue()) {
      return "&7an &3Ender Pearl";
    } else if (unlocalizedName.contains("tile.obsidian") && false) {
      return null;
    } else if (item.getItem() instanceof ItemFireball && this.fireballAlert.getValue()) {
      return "&7a &6Fireball";
    }
    return null;
  }

  private void sendAlert(String formattedName, String itemType, EntityPlayer player) {
    String distance = "";
    if (this.alertDistance.getValue() && player != null && mc.thePlayer != null) {
      double dist = Math.round(mc.thePlayer.getDistanceToEntity(player));
      distance = " &7(" + "§d" + (int) dist + "m" + "&7)";
    }
    String alert = "&r" + formattedName + " &7is holding " + itemType + distance;
    ChatUtil.display(alert);

    if (this.alertSound.getValue()) {
      SoundUtil.playSound("note.pling");
    }
  }

  private boolean isNextToBed(BlockPos blockPos) {
    for (EnumFacing enumFacing : EnumFacing.values()) {
      BlockPos offset = blockPos.offset(enumFacing);
      if (mc.theWorld != null && mc.theWorld.getBlockState(offset).getBlock() instanceof BlockBed) {
        return true;
      }
    }
    return false;
  }

  private void initItemDisplayColors() {
    this.itemDisplayColors.put("&fChainmail Armor", "chainmail_armor");
    this.itemDisplayColors.put("&fIron Armor", "iron_armor");
    this.itemDisplayColors.put("&bDiamond Armor", "diamond_armor");
    this.itemDisplayColors.put("&fIron Sword", "iron_sword");
    this.itemDisplayColors.put("&bDiamond Sword", "diamond_sword");
    this.itemDisplayColors.put("&bDiamond Pickaxe", "diamond_pickaxe");
    this.itemDisplayColors.put("&3Ender Pearl", "ender_pearl");
    this.itemDisplayColors.put("&eBridge Egg", "egg");
    this.itemDisplayColors.put("&6Fireball", "fire_charge");
    this.itemDisplayColors.put("&2Bow", "Bow");
    this.itemDisplayColors.put("&5Obsidian", "obsidian");
    this.itemDisplayColors.put("&cT&fN&cT", "tnt");
    this.itemDisplayColors.put("&3Block Zapper", "prismarine_shard");
    this.itemDisplayColors.put("&bSpeed Potion", "Speed II Potion (45 seconds)");
    this.itemDisplayColors.put("&aJump Boost Potion", "Jump V Potion (45 seconds)");
    this.itemDisplayColors.put("&fInvisibility Potion", "Invisibility Potion (30 seconds)");
    this.itemDisplayColors.put("&fIron Golem", "&cDream Defender");
    this.itemDisplayColors.put("&4Machine Gun Bow", "Machine Gun Bow");
    this.itemDisplayColors.put("&dCharlie the Unicorn", "Charlie the Unicorn");
    this.itemDisplayColors.put("&bIce Bridge", "Ice Bridge");
    this.itemDisplayColors.put("&cSleeping Dust", "Sleeping Dust");
    this.itemDisplayColors.put("&eUnstable Teleportation Device", "Unstable Teleportation Device");
    this.itemDisplayColors.put("&2Devastator Bow", "Devastator Bow");
    this.itemDisplayColors.put("&eMiracle of the Stars", "Miracle of the Stars");
    this.itemDisplayColors.put("&dMystic Mirror", "Mystic Mirror");
  }

  private void processEquipmentAlert(S04PacketEntityEquipment packet) {
    Entity entity = mc.theWorld.getEntityByID(packet.getEntityID());
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (!(entity instanceof EntityPlayer)) return;
    EntityPlayer player = (EntityPlayer) entity;
    if (player == mc.thePlayer || player.isDead) return;

    // Check teammate filter
    if (!this.trackTeammates.getValue() && TeamUtil.isSameTeam(player)) return;

    ItemStack item = packet.getItemStack();
    int slot = packet.getEquipmentSlot();
    String playerName = player.getName();
    String displayName = player.getDisplayName().getFormattedText();
    String teamColor = this.extractTeamColor(player);
    if (teamColor == null) return;

    if (item == null) return;

    long now = System.currentTimeMillis();
    int delayMs = this.alertDelay.getValue() * 1000;

    // Track upgrades on equipment
    if (this.trackUpgrades.getValue()
        && item.hasTagCompound()
        && item.getTagCompound().hasKey("ench", 9)) {
      NBTTagList enchants = (NBTTagList) item.getTagCompound().getTag("ench");

      // Slot 0 (held) with sword = Sharpened Swords
      if (slot == 0
          && item.getItem() instanceof ItemSword
          && hasEnchantment(enchants, "sharpness")) {
        String upgradeKey = "sharpness_" + playerName;
        if (!this.trackedTeamUpgrades.contains(upgradeKey)) {
          this.trackedTeamUpgrades.add(upgradeKey);
          this.sendUpgradeAlert(
              displayName, teamColor + " Team", "&bSharpened Swords", "sharpness");
        }
      }

      // Slot 2 (leggings) with protection = Reinforced Armor
      if (slot == 2
          && item.getItem() instanceof ItemArmor
          && hasEnchantment(enchants, "protection")) {
        String upgradeKey = "protection_" + playerName;
        if (!this.trackedTeamUpgrades.contains(upgradeKey)) {
          this.trackedTeamUpgrades.add(upgradeKey);
          this.sendUpgradeAlert(
              displayName, teamColor + " Team", "&bReinforced Armor", "protection");
        }
      }
    }

    // Track equipment items
    String trackedItemKey = this.getTrackedItemKey(item, slot);
    if (trackedItemKey == null) return;

    // Handle armor upgrades (slot 2 = leggings)
    if (slot == 2 && this.isArmorTracked(trackedItemKey)) {
      Map<String, Long> playerCooldowns =
          this.alertCooldowns.getOrDefault(playerName, new ConcurrentHashMap<>());
      String armorKey = "armor_" + trackedItemKey;
      long lastTime = playerCooldowns.getOrDefault(armorKey, 0L);

      if (now > lastTime + delayMs) {
        playerCooldowns.put(armorKey, now);
        this.alertCooldowns.put(playerName, playerCooldowns);
        this.sendTrackedItemAlert(displayName, player, trackedItemKey, teamColor);
      }
      return;
    }

    // Handle held items (slot 0)
    if (slot == 0 && this.isHeldItemTracked(trackedItemKey)) {
      Map<String, Long> playerCooldowns =
          this.alertCooldowns.getOrDefault(playerName, new ConcurrentHashMap<>());
      long lastTime = playerCooldowns.getOrDefault(trackedItemKey, 0L);

      if (now > lastTime + delayMs) {
        playerCooldowns.put(trackedItemKey, now);
        this.alertCooldowns.put(playerName, playerCooldowns);
        this.sendTrackedItemAlert(displayName, player, trackedItemKey, teamColor);
      }
    }
  }

  private String extractTeamColor(EntityPlayer player) {
    String formatted = player.getDisplayName().getFormattedText();
    if (formatted.length() < 2) return null;
    String code = formatted.substring(1, 2);
    // Valid color codes for bedwars teams
    if ("c9aebfd8".contains(code)) return code;
    return null;
  }

  private boolean hasEnchantment(NBTTagList enchants, String name) {
    // Check if any enchantment has the given name (simplified)
    // In practice we'd check the enchantment IDs, but this is a simplified approach
    for (int i = 0; i < enchants.tagCount(); i++) {
      short id = enchants.getCompoundTagAt(i).getShort("id");
      // Sharpness = 16, Protection = 0
      if (name.equals("sharpness") && id == 16) return true;
      if (name.equals("protection") && id == 0) return true;
    }
    return false;
  }

  private String getTrackedItemKey(ItemStack item, int slot) {
    if (item == null || item.getItem() == null) return null;

    String rawName = item.getItem().getUnlocalizedName();
    String displayName = EnumChatFormatting.getTextWithoutFormattingCodes(item.getDisplayName());
    String lowerDisplay = displayName.toLowerCase();

    if (slot == 2) {
      if (rawName.contains("diamond_armor")) return "diamond_armor";
      if (rawName.contains("iron_armor")) return "iron_armor";
      if (rawName.contains("chainmail_armor")) return "chainmail_armor";
    }

    if (slot == 0) {
      if (rawName.contains("iron_sword")) return "iron_sword";
      if (rawName.contains("diamond_sword")) return "diamond_sword";
      if (rawName.contains("diamond_pickaxe")) return "diamond_pickaxe";
      if (rawName.contains("ender_pearl") || item.getItem() instanceof ItemEnderPearl)
        return "ender_pearl";
      if (rawName.contains("egg")) return "egg";
      if (rawName.contains("fire_charge")
          || rawName.contains("fireball")
          || item.getItem() instanceof ItemFireball) return "fire_charge";
      if (rawName.contains("tnt")) return "tnt";
      if (rawName.contains("prismarine_shard")) return "prismarine_shard";
      if (rawName.contains("obsidian")) return "obsidian";

      // By display name
      if (this.trackBow.getValue()) {
        if (item.getItem() instanceof ItemBow) return "Bow";
        if (lowerDisplay.contains("machine gun bow")) return "Machine Gun Bow";
        if (lowerDisplay.contains("devastator bow")) return "Devastator Bow";
        if (lowerDisplay.contains("bow")) return "Bow";
      }

      if (this.trackPotions.getValue()) {
        if (item.getItem() instanceof ItemPotion) {
          ItemPotion potion = (ItemPotion) item.getItem();
          if (potion.getEffects(item) != null) {
            boolean isSpeed = false;
            boolean isJump = false;
            boolean isInvis = false;
            for (Object effect : potion.getEffects(item)) {
              if (effect instanceof PotionEffect) {
                PotionEffect pe = (PotionEffect) effect;
                if (pe.getPotionID() == Potion.moveSpeed.getId()) isSpeed = true;
                if (pe.getPotionID() == Potion.jump.getId()) isJump = true;
                if (pe.getPotionID() == Potion.invisibility.getId()) isInvis = true;
              }
            }
            if (isSpeed && lowerDisplay.contains("speed")) return "Speed II Potion (45 seconds)";
            if (isJump) return "Jump V Potion (45 seconds)";
            if (isInvis) return "Invisibility Potion (30 seconds)";
          }
        }
      }

      if (this.trackSpecials.getValue()) {
        if (lowerDisplay.contains("dream defender")) return "&cDream Defender";
        if (lowerDisplay.contains("charlie the unicorn")) return "Charlie the Unicorn";
        if (lowerDisplay.contains("ice bridge")) return "Ice Bridge";
        if (lowerDisplay.contains("sleeping dust")) return "Sleeping Dust";
        if (lowerDisplay.contains("unstable teleportation")) return "Unstable Teleportation Device";
        if (lowerDisplay.contains("miracle of the stars")) return "Miracle of the Stars";
        if (lowerDisplay.contains("mystic mirror")) return "Mystic Mirror";
        if (lowerDisplay.contains("block zapper")) return "prismarine_shard";
      }
    }

    return null;
  }

  private boolean isArmorTracked(String key) {
    return key.equals("diamond_leggings")
        || key.equals("iron_leggings")
        || key.equals("chainmail_leggings");
  }

  private boolean isHeldItemTracked(String key) {
    if (key == null) return false;
    // These are always tracked when enabled
    if (key.equals("iron_sword")
        || key.equals("diamond_sword")
        || key.equals("diamond_pickaxe")
        || key.equals("ender_pearl")
        || key.equals("egg")
        || key.equals("fire_charge")
        || key.equals("obsidian")
        || key.equals("tnt")
        || key.equals("prismarine_shard")
        || key.equals("chainmail_leggings")
        || key.equals("iron_leggings")
        || key.equals("diamond_leggings")) {
      return true;
    }
    // Check categories
    if (key.equals("Bow") || key.equals("Machine Gun Bow") || key.equals("Devastator Bow")) {
      return this.trackBow.getValue();
    }
    if (key.equals("Speed II Potion (45 seconds)")
        || key.equals("Jump V Potion (45 seconds)")
        || key.equals("Invisibility Potion (30 seconds)")) {
      return this.trackPotions.getValue();
    }
    if (key.equals("&cDream Defender")
        || key.equals("Charlie the Unicorn")
        || key.equals("Ice Bridge")
        || key.equals("Sleeping Dust")
        || key.equals("Unstable Teleportation Device")
        || key.equals("Miracle of the Stars")
        || key.equals("Mystic Mirror")) {
      return this.trackSpecials.getValue();
    }
    return false;
  }

  private String getItemAlertDisplayColor(String itemKey) {
    // Build reverse lookup from the itemDisplayColors map
    for (Map.Entry<String, String> entry : this.itemDisplayColors.entrySet()) {
      if (entry.getValue().equals(itemKey)) {
        return entry.getKey();
      }
    }
    // Fallback: color by item type
    if (itemKey.contains("diamond")) return "&bDiamond";
    if (itemKey.contains("iron")) return "&fIron";
    if (itemKey.contains("chainmail")) return "&fChainmail";
    return "&f" + itemKey;
  }

  private void sendTrackedItemAlert(
      String formattedName, EntityPlayer player, String itemKey, String teamColor) {
    String displayColor = this.getItemAlertDisplayColor(itemKey);
    String coloredPlayer = ChatColors.formatColor("&" + teamColor + player.getName());
    String msg = coloredPlayer + " &7has " + displayColor + "&7";

    if (this.alertDistance.getValue()) {
      double dist = mc.thePlayer.getDistanceToEntity(player);
      msg += " &7(&d" + (int) dist + "m&7)";
    }

    if (this.chatAlerts.getValue()) {
      ChatUtil.display(msg);
    }

    if (this.alertSound.getValue()) {
      SoundUtil.playSound("note.pling");
    }
  }

  private void sendUpgradeAlert(
      String formattedName, String teamName, String upgradeName, String upgradeKey) {
    String msg = teamName + " &7purchased " + upgradeName;

    if (this.chatAlerts.getValue()) {
      ChatUtil.display(msg);
    }

    if (this.alertSound.getValue()) {
      SoundUtil.playSound("note.pling");
    }
  }

  private class BedTracker extends Module {
    private static final long BED_SCAN_DELAY_MS = 3000L;
    private static final long BED_RESCAN_DELAY_MS = 5000L;
    private final LinkedHashMap<String, Long> alertCooldowns;
    private final LinkedHashSet<EntityEnderPearl> trackedPearls;
    private final LinkedHashSet<String> whitelistedPlayers;
    private final LinkedHashSet<String> autoIncPlayers;
    private final Color wBed;
    private final Color rBed;
    private final Color yBed;
    private final Color gBed;
    private BlockPos bedPos;
    private long lastMarcoTime;
    private boolean waiting;
    private long bedScanAt;
    private boolean scannedThisGame;
    public final BooleanProperty alerts;
    public final IntProperty alertRange;
    public final BooleanProperty alertOnPearl;
    public final ModeProperty alertSound;
    public final IntProperty alertFrequency;
    public final BooleanProperty autoInc;
    public final BooleanProperty marco;
    public final IntProperty marcoRange;
    public final BooleanProperty marcoOnPreal;
    public final TextProperty marcoText;
    public final IntProperty marcoDelay;
    public final BooleanProperty hud;
    public final ModeProperty hudPosX;
    public final ModeProperty hudPosY;
    public final IntProperty hudOffX;
    public final IntProperty hudOffY;
    public final FloatProperty hudScale;
    public final BooleanProperty hudShadow;

    private void playAlertSound() {
      switch (this.alertSound.getValue()) {
        case 1:
          SoundUtil.playSound("mob.cat.meow");
          break;
        case 2:
          SoundUtil.playSound("random.anvil_land");
      }
    }

    private Color getHudColor(int distance) {
      if (distance < 0) {
        return this.wBed;
      } else if (distance <= 100) {
        return this.gBed;
      } else if (distance <= 114) {
        return ColorUtil.interpolate((float) (114 - distance) / 14.0F, this.yBed, this.gBed);
      } else {
        return distance <= 128
            ? ColorUtil.interpolate((float) (128 - distance) / 14.0F, this.rBed, this.yBed)
            : this.rBed;
      }
    }

    private boolean isBed(BlockPos blockPos) {
      return blockPos != null
          && mc.theWorld != null
          && mc.theWorld.getBlockState(blockPos).getBlock() == Blocks.bed;
    }

    public BedTracker() {
      super("BedTracker", false, true);
      this.alertCooldowns = new LinkedHashMap<>();
      this.trackedPearls = new LinkedHashSet<>();
      this.whitelistedPlayers = new LinkedHashSet<>();
      this.autoIncPlayers = new LinkedHashSet<>();
      this.wBed = new Color(ChatColors.WHITE.toAwtColor());
      this.rBed = new Color(ChatColors.RED.toAwtColor());
      this.yBed = new Color(ChatColors.YELLOW.toAwtColor());
      this.gBed = new Color(ChatColors.GREEN.toAwtColor());
      this.bedPos = null;
      this.lastMarcoTime = -1L;
      this.waiting = false;
      this.bedScanAt = -1L;
      this.scannedThisGame = false;
      this.alerts = new BooleanProperty("alerts", true, BedwarUtils.this.bedTracker::getValue);
      this.alertRange =
          new IntProperty(
              "alerts-range",
              48,
              8,
              128,
              () -> BedwarUtils.this.bedTracker.getValue() && this.alerts.getValue());
      this.alertOnPearl =
          new BooleanProperty("alerts-on-pearl", true, BedwarUtils.this.bedTracker::getValue);
      this.alertSound =
          new ModeProperty(
              "alerts-sound",
              1,
              new String[] {"NONE", "MEOW", "ANVIL"},
              () ->
                  BedwarUtils.this.bedTracker.getValue()
                      && (this.alerts.getValue() || this.alertOnPearl.getValue()));
      this.alertFrequency =
          new IntProperty(
              "alerts-frequency",
              5,
              1,
              30,
              () ->
                  BedwarUtils.this.bedTracker.getValue()
                      && (this.alerts.getValue() || this.alertOnPearl.getValue()));
      this.autoInc = new BooleanProperty("auto-inc", false, BedwarUtils.this.bedTracker::getValue);
      this.marco = new BooleanProperty("macro", false, BedwarUtils.this.bedTracker::getValue);
      this.marcoRange =
          new IntProperty(
              "macro-range",
              24,
              8,
              128,
              () -> BedwarUtils.this.bedTracker.getValue() && this.marco.getValue());
      this.marcoOnPreal =
          new BooleanProperty("macro-on-pearl", false, BedwarUtils.this.bedTracker::getValue);
      this.marcoText =
          new TextProperty(
              "macro-text",
              "/lobby",
              () ->
                  BedwarUtils.this.bedTracker.getValue()
                      && (this.marco.getValue() || this.marcoOnPreal.getValue()));
      this.marcoDelay =
          new IntProperty(
              "macro-delay",
              1,
              1,
              10,
              () ->
                  BedwarUtils.this.bedTracker.getValue()
                      && (this.marco.getValue() || this.marcoOnPreal.getValue()));
      this.hud = new BooleanProperty("hud", true, BedwarUtils.this.bedTracker::getValue);
      this.hudPosX =
          new ModeProperty(
              "hud-position-x",
              0,
              new String[] {"LEFT", "MIDDLE", "RIGHT"},
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
      this.hudPosY =
          new ModeProperty(
              "hud-position-y",
              0,
              new String[] {"TOP", "MIDDLE", "BOTTOM"},
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
      this.hudOffX =
          new IntProperty(
              "hud-offset-x",
              2,
              0,
              255,
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
      this.hudOffY =
          new IntProperty(
              "hud-offset-y",
              2,
              0,
              255,
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
      this.hudScale =
          new FloatProperty(
              "hud-scale",
              1.0F,
              0.5F,
              1.5F,
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
      this.hudShadow =
          new BooleanProperty(
              "hud-shadow",
              true,
              () -> BedwarUtils.this.bedTracker.getValue() && this.hud.getValue());
    }

    private void resetTracking() {
      this.alertCooldowns.clear();
      this.trackedPearls.clear();
      this.whitelistedPlayers.clear();
      this.autoIncPlayers.clear();
      this.bedPos = null;
      this.lastMarcoTime = -1L;
    }

    private BlockPos getBedPos() {
      return this.bedPos;
    }

    private void scheduleBedScan() {
      if (!this.scannedThisGame && this.bedScanAt == -1L) {
        this.bedScanAt = System.currentTimeMillis() + BED_SCAN_DELAY_MS;
      }
    }

    private void scheduleAutomaticBedScan() {
      if (this.scannedThisGame
          || mc.theWorld == null
          || mc.thePlayer == null
          || this.isBed(this.bedPos)) return;
      if (this.bedScanAt == -1L) {
        this.bedScanAt = System.currentTimeMillis() + BED_SCAN_DELAY_MS;
      }
    }

    private void runPendingBedScan() {
      if (this.bedScanAt == -1L || System.currentTimeMillis() < this.bedScanAt) {
        return;
      }
      this.bedScanAt = -1L;
      if (mc.theWorld == null || mc.thePlayer == null) {
        this.bedScanAt = System.currentTimeMillis() + BED_RESCAN_DELAY_MS;
        return;
      }
      int x = MathHelper.floor_double(mc.thePlayer.posX);
      int y = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
      int z = MathHelper.floor_double(mc.thePlayer.posZ);
      for (int i = x - 25; i <= x + 25; i++) {
        for (int j = y - 25; j <= y + 25; j++) {
          for (int k = z - 25; k <= z + 25; k++) {
            BlockPos blockPos = new BlockPos(i, j, k);
            if (this.isBed(blockPos)) {
              this.bedPos = blockPos;
              this.scannedThisGame = true;
              ChatUtil.display(
                  String.format(
                      "%s%s: &fWhitelisted your bed at (%d, %d, %d) &a&l\u2714&r",
                      Myau.clientName,
                      this.getName(),
                      this.bedPos.getX(),
                      this.bedPos.getY(),
                      this.bedPos.getZ()));
              SoundUtil.playSound("note.pling");
              return;
            }
          }
        }
      }
      this.bedScanAt = System.currentTimeMillis() + BED_RESCAN_DELAY_MS;
    }

    private void pruneTrackedPearls() {
      if (mc.theWorld == null) {
        this.trackedPearls.clear();
        return;
      }
      Iterator<EntityEnderPearl> iterator = this.trackedPearls.iterator();
      while (iterator.hasNext()) {
        EntityEnderPearl pearl = iterator.next();
        if (pearl.isDead || !mc.theWorld.loadedEntityList.contains(pearl)) {
          iterator.remove();
        }
      }
    }

    @EventTarget
    public void onTick(TickEvent event) {
      if (this.isEnabled() && event.getType() == EventType.POST) {
        this.scheduleAutomaticBedScan();
        this.runPendingBedScan();
        this.pruneTrackedPearls();
        if (!this.isBed(this.bedPos)) {
          return;
        }
        long millis = System.currentTimeMillis();
        boolean pearl = false;
        boolean marco = false;
        for (Entity entity : mc.theWorld.loadedEntityList) {
          if (entity instanceof EntityEnderPearl) {
            EntityEnderPearl enderPearl = (EntityEnderPearl) entity;
            if (!this.trackedPearls.contains(enderPearl)) {
              this.trackedPearls.add(enderPearl);
              if (this.alertOnPearl.getValue()) {
                ChatUtil.display(
                    "%s%s: &fDetected &5Ender Pearl&r &e&l⚠&r", Myau.clientName, this.getName());
                pearl = true;
              }
              if (this.marcoOnPreal.getValue()
                  && this.lastMarcoTime + (long) this.marcoDelay.getValue() * 1000L <= millis) {
                this.lastMarcoTime = millis;
                marco = true;
              }
            }
          }
        }
        for (EntityPlayer player :
            mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(
                    entityPlayer ->
                        !TeamUtil.isBot(entityPlayer)
                            && !this.whitelistedPlayers.contains(entityPlayer.getName()))
                .collect(Collectors.toList())) {
          if (TeamUtil.isSameTeam(player)) {
            this.whitelistedPlayers.add(player.getName());
          } else {
            double distance =
                player.getDistance(
                    (double) this.bedPos.getX() + 0.5,
                    (double) this.bedPos.getY() + 0.5,
                    (double) this.bedPos.getZ() + 0.5);
            String name = player.getName();
            String text = player.getDisplayName().getFormattedText();
            ItemStack item = player.getHeldItem();
            boolean isPearl = item != null && item.getItem() instanceof ItemEnderPearl;
            if (this.alerts.getValue() && distance < (double) this.alertRange.getValue()) {
              Long cooldown = this.alertCooldowns.get(name);
              if (cooldown == null
                  || cooldown + (long) this.alertFrequency.getValue() * 1000L <= millis) {
                this.alertCooldowns.put(name, millis);
                ChatUtil.display(
                    String.format(
                        "%s%s: %s&r &fis %d blocks away from your bed &e&l⚠&r",
                        Myau.clientName, this.getName(), text, (int) distance + 1));
                pearl = true;
              }
              if (this.autoInc.getValue() && this.autoIncPlayers.add(name.toLowerCase())) {
                ChatUtil.sendMessage(this.getIncMessage(player));
              }
            }
            if (this.alertOnPearl.getValue() && isPearl) {
              Long cooldown = this.alertCooldowns.get(name);
              if (cooldown == null
                  || cooldown + (long) this.alertFrequency.getValue() * 1000L <= millis) {
                this.alertCooldowns.put(name, millis);
                ChatUtil.display(
                    String.format(
                        "%s%s: %s&r &fhas &5Ender Pearl&r &e&l⚠&r", this.getName(), text));
                pearl = true;
              }
            }
            if ((this.marco.getValue() && distance < (double) this.marcoRange.getValue()
                    || this.marcoOnPreal.getValue() && isPearl)
                && this.lastMarcoTime + (long) this.marcoDelay.getValue() * 1000L <= millis) {
              this.lastMarcoTime = millis;
              marco = true;
            }
          }
        }
        if (pearl) {
          this.playAlertSound();
        }
        if (marco) {
          ChatUtil.sendRaw(
              String.format(
                  ChatColors.formatColor("%s%s: &fRunning &6%s&r"),
                  ChatColors.formatColor(Myau.clientName),
                  this.getName(),
                  this.marcoText.getValue()));
          ChatUtil.sendMessage(this.marcoText.getValue());
        }
      }
    }

    private String getIncMessage(EntityPlayer player) {
      String team = this.getTeamName(player);
      return team.isEmpty() ? "inc" : team + " inc";
    }

    private String getTeamName(EntityPlayer player) {
      String formatted = player.getDisplayName().getFormattedText().toLowerCase();
      if (formatted.contains("§c") || formatted.contains("red")) return "red";
      if (formatted.contains("§e") || formatted.contains("yellow")) return "yellow";
      if (formatted.contains("§a") || formatted.contains("green")) return "green";
      if (formatted.contains("§9") || formatted.contains("blue")) return "blue";
      if (formatted.contains("§b") || formatted.contains("aqua")) return "aqua";
      if (formatted.contains("§f") || formatted.contains("white")) return "white";
      if (formatted.contains("§d") || formatted.contains("pink")) return "pink";
      if (formatted.contains("§7") || formatted.contains("gray") || formatted.contains("grey"))
        return "gray";
      return "";
    }

    @EventTarget(Priority.LOW)
    public void onRender(Render2DEvent event) {
      if (this.isEnabled() && this.hud.getValue()) {
        if (mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.showDebugInfo) {
          GuiScreen currentScreen = mc.currentScreen;
          if (currentScreen == null || currentScreen instanceof GuiChat) {
            int distanceSq = 0;
            boolean hasBed = this.isBed(this.bedPos);
            if (hasBed) {
              double xDiff = mc.thePlayer.posX - (double) this.bedPos.getX();
              double zDiff = mc.thePlayer.posZ - (double) this.bedPos.getZ();
              distanceSq = (int) Math.sqrt(xDiff * xDiff + zDiff * zDiff) + 1;
            }
            String text =
                ChatColors.formatColor(
                    String.format(
                        "&fBed: %s%s",
                        !hasBed ? "&cfalse&r" : "&atrue&r",
                        !hasBed
                            ? ""
                            : String.format(
                                " &7| &fDistance: &r%d%s",
                                distanceSq, distanceSq >= 128 ? " &c&l⚠&r" : "")));
            ScaledResolution scaledResolution = new ScaledResolution(mc);
            float width = (float) mc.fontRendererObj.getStringWidth(text);
            float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float scale = (float) this.hudOffX.getValue() / this.hudScale.getValue();
            switch (this.hudPosX.getValue()) {
              case 0:
                scale++;
                break;
              case 1:
                scale +=
                    (float) scaledResolution.getScaledWidth() / this.hudScale.getValue() / 2.0F
                        - width / 2.0F;
                break;
              case 2:
                scale = (scale + 1.0F) * -1.0F;
                scale +=
                    (float) scaledResolution.getScaledWidth() / this.hudScale.getValue() - width;
            }
            float offset = (float) this.hudOffY.getValue() / this.hudScale.getValue();
            switch (this.hudPosY.getValue()) {
              case 0:
                offset++;
                break;
              case 1:
                offset +=
                    (float) scaledResolution.getScaledHeight() / this.hudScale.getValue() / 2.0F
                        - height / 2.0F;
                break;
              case 2:
                offset = (offset + 1.0F) * -1.0F;
                offset +=
                    (float) scaledResolution.getScaledHeight() / this.hudScale.getValue() - height;
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.hudScale.getValue(), this.hudScale.getValue(), 1.0F);
            GlStateManager.translate(scale, offset, 0.0F);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.fontRendererObj.drawString(
                text, 0.0F, 0.0F, this.getHudColor(distanceSq).getRGB(), this.hudShadow.getValue());
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
          }
        }
      }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
      this.waiting = false;
      this.bedScanAt = -1L;
      this.scannedThisGame = false;
      this.resetTracking();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
      if (this.isEnabled()) {
        if (event.getPacket() instanceof S02PacketChat) {
          String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
          if (msg.contains("§e§lProtect your bed and destroy the enemy bed")
              || msg.contains("§e§lDestroy the enemy bed and then eliminate them")) {
            this.bedScanAt = -1L;
            this.resetTracking();
            this.scannedThisGame = false;
            this.waiting = true;
          }
        }
        if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waiting) {
          this.waiting = false;
          this.scheduleBedScan();
        }
      }
    }

    @Override
    public void onDisabled() {
      this.waiting = false;
      this.bedScanAt = -1L;
      this.scannedThisGame = false;
      this.resetTracking();
    }
  }
}
