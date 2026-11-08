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

import net.elytrium.limbofilter.Settings;

public class PacketRateCheck {

  private long lastMoveTime;
  private int fastMoveCount;

  public CheckResult check() {
    long now = System.currentTimeMillis();
    if (this.lastMoveTime != 0
        && (now - this.lastMoveTime) < Settings.IMP.MAIN.MIN_MOVE_INTERVAL_MS) {
      ++this.fastMoveCount;
      if (this.fastMoveCount > Settings.IMP.MAIN.MAX_FAST_MOVES) {
        this.lastMoveTime = now;
        return CheckResult.fail("Too many fast movement packets");
      }
    } else {
      this.fastMoveCount = 0;
    }
    this.lastMoveTime = now;
    return CheckResult.ok();
  }

  public int getFastMoveCount() {
    return this.fastMoveCount;
  }
}
