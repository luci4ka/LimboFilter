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

package net.elytrium.limbofilter.check;

import com.velocitypowered.api.network.ProtocolVersion;
import net.elytrium.limbofilter.Settings;

/**
 * Проверка падения: скорость, ускорение, XZ. Состояние (ticks, lastY и т.д.) хранится в самом
 * чекере.
 */
public class FallingCheck {

  private final double[] loadedChunkSpeedCache;
  private final ProtocolVersion version;

  private double posX;
  private double posY;
  private double lastY;
  private double posZ;
  private double lastDeltaY;
  private int ticks = 1;
  private int ignoredTicks;
  private int nonValidPacketsSize;
  private boolean startedListening;

  public FallingCheck(ProtocolVersion version, int fallingCheckTicks) {
    this.version = version;
    this.loadedChunkSpeedCache = new double[fallingCheckTicks];
    for (int i = 0; i < fallingCheckTicks; ++i) {
      this.loadedChunkSpeedCache[i] = -((Math.pow(0.98, i) - 1) * 3.92);
    }
  }

  public void resetTicks() {
    this.ticks = 1;
    this.posY = -1;
    this.lastY = -1;
  }

  public void updatePosition(double x, double y, double z) {
    this.posX = x;
    this.lastY = this.posY;
    this.posY = y;
    this.posZ = z;
  }

  public CheckResult check(boolean onGround, int validX, int validZ) {
    // Фаза 1: ждём, пока игрок окажется в валидных X/Z
    if (!this.startedListening) {
      if (this.posX == validX && this.posZ == validZ) {
        this.startedListening = true;
      }
      if (this.nonValidPacketsSize > Settings.IMP.MAIN.NON_VALID_POSITION_XZ_ATTEMPTS) {
        return CheckResult.fail("A lot of non-valid XZ attempts");
      }
      this.lastY = Settings.IMP.MAIN.FALLING_COORDS.Y;
      ++this.nonValidPacketsSize;
      return CheckResult.ok();
    }

    // Фаза 2: сама проверка падения
    if (this.ticks >= Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
      return CheckResult.ok(); // сигнал «проверка завершена»
    }

    double currentDeltaY = this.lastY - this.posY;

    if (onGround && Math.abs(currentDeltaY) > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE) {
      return CheckResult.fail("Lied about onGround while falling");
    }

    if (currentDeltaY == 0) {
      ++this.ignoredTicks;
      if (this.ignoredTicks > Settings.IMP.MAIN.NON_VALID_POSITION_Y_ATTEMPTS) {
        return CheckResult.fail("A lot of non-valid Y attempts");
      }
      return CheckResult.ok();
    }

    if (this.ticks > 1) {
      double expectedAcceleration =
          this.getLoadedChunkSpeed(this.ticks) - this.getLoadedChunkSpeed(this.ticks - 1);
      double actualAcceleration = currentDeltaY - this.lastDeltaY;
      if (Math.abs(actualAcceleration - expectedAcceleration)
          > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE * 2) {
        return CheckResult.fail("Non-valid acceleration");
      }
    }
    this.lastDeltaY = currentDeltaY;

    if (this.checkY()) {
      return CheckResult.fail("Non-valid X, Z or Velocity");
    }

    ++this.ticks;
    return CheckResult.ok();
  }

  private boolean checkY() {
    while (this.ticks < this.loadedChunkSpeedCache.length
        && Math.abs(this.lastY - this.posY - this.getLoadedChunkSpeed(this.ticks))
            > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE) {
      ++this.ticks;
      ++this.ignoredTicks;
    }
    return this.ticks >= this.loadedChunkSpeedCache.length;
  }

  public double getLoadedChunkSpeed(int ticks) {
    if (ticks == -1) {
      return 0;
    }
    return this.loadedChunkSpeedCache[ticks];
  }

  public int getTicks() {
    return this.ticks;
  }

  public boolean isStarted() {
    return this.startedListening;
  }

  public double getPosX() {
    return this.posX;
  }

  public double getPosY() {
    return this.posY;
  }

  public double getPosZ() {
    return this.posZ;
  }

  public double getLastY() {
    return this.lastY;
  }

  public int getIgnoredTicks() {
    return this.ignoredTicks;
  }

  public int getNonValidPacketsSize() {
    return this.nonValidPacketsSize;
  }
}
