/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.handler;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.CaptchaHolder;
import net.elytrium.limbofilter.check.CheckResult;
import net.elytrium.limbofilter.check.ClientSettingsCheck;
import net.elytrium.limbofilter.check.FallingCheck;
import net.elytrium.limbofilter.check.HoverCheck;
import net.elytrium.limbofilter.check.PacketRateCheck;
import net.elytrium.limbofilter.listener.TcpListener;
import net.elytrium.limbofilter.protocol.data.EntityMetadata;
import net.elytrium.limbofilter.protocol.data.ItemFrame;
import net.elytrium.limbofilter.protocol.packets.Interact;
import net.elytrium.limbofilter.protocol.packets.SetEntityMetadata;
import net.elytrium.limbofilter.stats.Statistics;

public class BotFilterSessionHandler implements LimboSessionHandler {

  private static long FALLING_CHECK_TOTAL_TIME;

  private final Map<Integer, Integer> frameRotation = new HashMap<>();
  private final Player proxyPlayer;
  private final ProtocolVersion version;
  private final LimboFilter plugin;
  private final Statistics statistics;
  private final int validX;
  private final int validY;
  private final int validZ;
  private final int validTeleportId;

  private int waitingTeleportId;
  private boolean onGround;

  private long joinTime;
  private ScheduledFuture<?> filterMainTask;

  private final FallingCheck fallingCheck;
  private final HoverCheck hoverCheck;
  private final PacketRateCheck packetRateCheck;
  private final ClientSettingsCheck clientSettingsCheck;

  private CheckState state;
  private LimboPlayer player;
  private Limbo server;
  private String captchaAnswer;
  private int attempts = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS;
  private boolean checkedByBrand;

  public BotFilterSessionHandler(Player proxyPlayer, LimboFilter plugin) {
    this.proxyPlayer = proxyPlayer;
    this.version = this.proxyPlayer.getProtocolVersion();
    this.plugin = plugin;

    this.statistics = this.plugin.getStatistics();

    Settings.MAIN.FALLING_COORDS fallingCoords = Settings.IMP.MAIN.FALLING_COORDS;
    this.validX = fallingCoords.X;
    this.validY = fallingCoords.Y;
    this.validZ = fallingCoords.Z;
    this.validTeleportId = fallingCoords.TELEPORT_ID;

    this.fallingCheck = new FallingCheck(this.version, Settings.IMP.MAIN.FALLING_CHECK_TICKS);
    this.hoverCheck = new HoverCheck();
    this.packetRateCheck = new PacketRateCheck();
    this.clientSettingsCheck = new ClientSettingsCheck();

    if (proxyPlayer.getRemoteAddress().getPort() == 0) {
      this.state =
          plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
              ? Settings.IMP.MAIN.GEYSER_CHECK_STATE
              : Settings.IMP.MAIN.GEYSER_CHECK_STATE_NON_TOGGLED;
    } else {
      this.state =
          plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
              ? Settings.IMP.MAIN.CHECK_STATE
              : Settings.IMP.MAIN.CHECK_STATE_NON_TOGGLED;
    }
  }

  // =====================================================================
  //  Жизненный цикл сессии
  // =====================================================================

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.server = server;
    this.player = player;

    this.joinTime = System.currentTimeMillis();
    if (this.state == CheckState.ONLY_CAPTCHA) {
      this.changeStateToCaptcha();
    } else if (this.state == CheckState.ONLY_POSITION
        || this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.sendFallingCheckPackets();
      this.sendFallingCheckTitleAndChat();
    } else if (this.state == CheckState.CAPTCHA_POSITION) {
      this.sendFallingCheckPackets();

      if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
        this.sendFallingCheckTitleAndChat();
      }
    }

    this.player.flushPackets();

    this.filterMainTask =
        player
            .getScheduledExecutor()
            .schedule(
                () -> this.disconnect(this.plugin.getPackets().getTimesUp(), true),
                this.getTimeout(),
                TimeUnit.MILLISECONDS);
  }

  @Override
  public void onDisconnect() {
    this.filterMainTask.cancel(true);

    TcpListener tcpListener = this.plugin.getTcpListener();
    if (tcpListener != null) {
      tcpListener.removeAddress(this.proxyPlayer.getRemoteAddress().getAddress());
    }
  }

  // =====================================================================
  //  Движение
  // =====================================================================

  private void sendFallingCheckPackets() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckPackets());
  }

  private void sendFallingCheckTitleAndChat() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckTitleAndChat());
  }

  @Override
  public void onMove(double x, double y, double z) {
    CheckResult rateResult = this.packetRateCheck.check();
    if (rateResult.isFailed()) {
      this.fallingCheckFailed(rateResult.getReason());
      return;
    }

    if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0
        && x == this.validX
        && y == this.validY
        && z == this.validZ
        && this.waitingTeleportId == this.validTeleportId) {
      this.fallingCheck.resetTicks();
      this.waitingTeleportId = -1;
    }

    this.fallingCheck.updatePosition(x, y, z);

    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      this.logPosition();
    }

    if (!this.fallingCheck.isStarted() && this.state != CheckState.ONLY_CAPTCHA) {
      CheckResult result = this.fallingCheck.check(this.onGround, this.validX, this.validZ);
      if (result.isFailed()) {
        this.fallingCheckFailed(result.getReason());
        return;
      }
      if (this.fallingCheck.isStarted()
          && this.state == CheckState.CAPTCHA_POSITION
          && !Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
        this.sendCaptcha();
      }
    }

    if (this.fallingCheck.isStarted()
        && this.state != CheckState.SUCCESSFUL
        && this.state != CheckState.ONLY_CAPTCHA
        && !this.onGround) {
      double currentDeltaY = this.fallingCheck.getLastY() - this.fallingCheck.getPosY();
      CheckResult hoverResult = this.hoverCheck.check(this.onGround, currentDeltaY);
      if (hoverResult.isFailed()) {
        this.fallingCheckFailed(hoverResult.getReason());
        return;
      }

      CheckResult result = this.fallingCheck.check(this.onGround, this.validX, this.validZ);
      if (result.isFailed()) {
        this.fallingCheckFailed(result.getReason());
        return;
      }

      if (this.fallingCheck.getTicks() >= Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        if (this.state == CheckState.CAPTCHA_POSITION) {
          this.changeStateToCaptcha();
        } else {
          this.finishCheck();
        }
        return;
      }

      PreparedPacket experience =
          this.plugin.getPackets().getExperience(this.fallingCheck.getTicks());
      if (experience != null) {
        this.player.writePacketAndFlush(experience);
      }
    }
  }

  private void fallingCheckFailed(String reason) {
    this.statistics.addCheckFailure(reason);

    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      this.logPosition();
    }
    LimboFilter.getLogger()
        .info("[filter] {} failed falling check: {}", this.proxyPlayer.getUsername(), reason);

    if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.player.writePacketAndFlush(this.plugin.getPackets().getLastExperience());
      this.changeStateToCaptcha();
    } else {
      this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
    }
  }

  private void logPosition() {
    LimboFilter.getLogger()
        .info(
            "lastY="
                + this.fallingCheck.getLastY()
                + "; y="
                + this.fallingCheck.getPosY()
                + "; delta="
                + (this.fallingCheck.getLastY() - this.fallingCheck.getPosY())
                + "; need="
                + this.fallingCheck.getLoadedChunkSpeed(this.fallingCheck.getTicks())
                + "; x="
                + this.fallingCheck.getPosX()
                + "; z="
                + this.fallingCheck.getPosZ()
                + "; validX="
                + this.validX
                + "; validY="
                + this.validY
                + "; validZ="
                + this.validZ
                + "; ticks="
                + this.fallingCheck.getTicks()
                + "; ignoredTicks="
                + this.fallingCheck.getIgnoredTicks()
                + "; hoverTicks="
                + this.hoverCheck.getHoverTicks()
                + "; fastMoves="
                + this.packetRateCheck.getFastMoveCount()
                + "; state="
                + this.state
                + "; diff="
                + (this.fallingCheck.getLastY()
                    - this.fallingCheck.getPosY()
                    - this.fallingCheck.getLoadedChunkSpeed(this.fallingCheck.getTicks())));
  }

  @Override
  public void onGround(boolean onGround) {
    this.onGround = onGround;
  }

  @Override
  public void onTeleport(int teleportId) {
    if (teleportId == this.waitingTeleportId) {
      this.fallingCheck.resetTicks();
      this.waitingTeleportId = -1;
    }
  }

  // =====================================================================
  //  Капча
  // =====================================================================

  @Override
  public void onChat(String message) {
    if (this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA) {
      if (this.equalsCaptchaAnswer(message)
          || (message.startsWith("/") && this.equalsCaptchaAnswer(message.substring(1)))) {
        this.player.writePacketAndFlush(this.plugin.getPackets().getResetSlot());
        this.finishCheck();
      } else if (--this.attempts != 0) {
        this.sendCaptcha();
      } else {
        this.statistics.addCaptchaFailure();
        this.disconnect(this.plugin.getPackets().getCaptchaFailed(), true);
      }
    }
  }

  private boolean equalsCaptchaAnswer(String message) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.IGNORE_CASE) {
      return message.equalsIgnoreCase(this.captchaAnswer);
    } else {
      return message.equals(this.captchaAnswer);
    }
  }

  private void changeStateToCaptcha() {
    if (this.state != CheckState.ONLY_CAPTCHA
        && this.version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.player.writePacket(this.plugin.getPackets().getFallingCheckChunkUnload());
    }

    this.state = CheckState.ONLY_CAPTCHA;
    this.server.respawnPlayer(this.proxyPlayer);
    if (Settings.IMP.MAIN.DISABLE_FALLING_ON_CAPTCHA) {
      this.player.writePacketAndFlush(this.plugin.getPackets().getNoAbilities());
    }

    this.waitingTeleportId = this.validTeleportId;
    if (this.captchaAnswer == null) {
      this.sendCaptcha();
    }
  }

  private void sendCaptcha() {
    CaptchaHolder captchaHolder = this.plugin.getNextCaptcha();

    if (captchaHolder == null) {
      this.player.closeWith(this.plugin.getPackets().getCaptchaNotReadyYet());
      return;
    }

    this.captchaAnswer = captchaHolder.getAnswer();

    PreparedPacket framedCaptchaPacket = this.plugin.getPackets().getFramedCaptchaPackets();
    if (framedCaptchaPacket != null) {
      this.player.writePacket(framedCaptchaPacket);
    }

    this.player.writePacket(this.plugin.getPackets().getCaptchaAttemptsPacket(this.attempts));
    for (Object packet : captchaHolder.getMapPacket(this.version)) {
      this.player.writePacket(packet);
    }

    this.player.flushPackets();
  }

  // =====================================================================
  //  Пакеты клиента (brand, settings, interact)
  // =====================================================================

  @Override
  public void onGeneric(Object packet) {
    if (packet instanceof PluginMessagePacket) {
      PluginMessagePacket pluginMessage = (PluginMessagePacket) packet;
      if (PluginMessageUtil.isMcBrand(pluginMessage) && !this.checkedByBrand) {
        String brand = PluginMessageUtil.readBrandMessage(pluginMessage.content());
        LimboFilter.getLogger().info("{} has client brand {}", this.proxyPlayer, brand);
        if (!Settings.IMP.MAIN.BLOCKED_CLIENT_BRANDS.contains(brand)) {
          this.checkedByBrand = true;
        }
      }
    } else if (packet instanceof ClientSettingsPacket) {
      if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS) {
        this.clientSettingsCheck.onPacket((ClientSettingsPacket) packet);
      }
    } else if (packet instanceof Interact) {
      Interact interact = (Interact) packet;
      if (interact.getType() == 0 || interact.getType() == 1) {
        int rotation =
            this.frameRotation.compute(interact.getEntityId(), (k, v) -> (v != null ? v : 0) + 1);
        EntityMetadata metadata = ItemFrame.createRotationMetadata(this.version, rotation);
        this.player.writePacketAndFlush(new SetEntityMetadata(interact.getEntityId(), metadata));
      }
    }
  }

  // =====================================================================
  //  Финал проверки
  // =====================================================================

  private void finishCheck() {
    if (System.currentTimeMillis() - this.joinTime < FALLING_CHECK_TOTAL_TIME
        && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.state == CheckState.CAPTCHA_POSITION
          && this.fallingCheck.getTicks() < Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        this.state = CheckState.ONLY_POSITION;
      } else {
        if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
          this.changeStateToCaptcha();
        } else {
          this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
        }
      }
      return;
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS) {
      CheckResult settingsResult = this.clientSettingsCheck.check();
      if (settingsResult.isFailed()) {
        this.statistics.addCheckFailure(settingsResult.getReason());
        LimboFilter.getLogger()
            .info(
                "[filter] {} failed ClientSettings check: {}",
                this.proxyPlayer.getUsername(),
                settingsResult.getReason());
        this.disconnect(this.plugin.getPackets().getKickClientCheckSettings(), true);
        return;
      }
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_BRAND && !this.checkedByBrand) {
      this.statistics.addBrandFailure();
      this.disconnect(this.plugin.getPackets().getKickClientCheckBrand(), true);
      return;
    }

    if (this.checkPing()) {
      return;
    }

    this.state = CheckState.SUCCESSFUL;
    this.plugin.cacheFilterUser(this.proxyPlayer);

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.NEED_TO_RECONNECT)) {
      this.disconnect(this.plugin.getPackets().getSuccessfulBotFilterDisconnect(), false);
    } else {
      this.player.writePacketAndFlush(this.plugin.getPackets().getSuccessfulBotFilterChat());
      this.player.disconnect();
    }
  }

  private boolean checkPing() {
    int l7Ping = this.player.getPing();
    int l4Ping = this.statistics.getPing(this.proxyPlayer.getRemoteAddress().getAddress());

    if (Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_ENABLED
        && (l7Ping - l4Ping) > Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_DIFFERENCE) {
      this.statistics.addProxyCheckFailure();
      this.disconnect(this.plugin.getPackets().getKickProxyCheck(), true);

      if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_FAIL) {
        LimboFilter.getLogger()
            .info(
                "{} failed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
      }

      return true;
    }

    if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_SUCCESS) {
      LimboFilter.getLogger()
          .info("{} passed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
    }

    return false;
  }

  // =====================================================================
  //  Утилиты
  // =====================================================================

  private void disconnect(PreparedPacket reason, boolean blocked) {
    this.player.closeWith(reason);
    if (blocked) {
      this.statistics.addBlockedConnection();
    }
  }

  private int getTimeout() {
    if (this.proxyPlayer.getRemoteAddress().getPort() == 0) {
      return Settings.IMP.MAIN.GEYSER_TIME_OUT;
    } else {
      return Settings.IMP.MAIN.TIME_OUT;
    }
  }

  public static void setFallingCheckTotalTime(long time) {
    FALLING_CHECK_TOTAL_TIME = time;
  }

  public enum CheckState {
    ONLY_POSITION,
    ONLY_CAPTCHA,
    CAPTCHA_POSITION,
    CAPTCHA_ON_POSITION_FAILED,
    SUCCESSFUL
  }
}
