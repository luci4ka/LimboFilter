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

import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import net.elytrium.limbofilter.Settings;

/**
 * Проверка содержимого ClientSettingsPacket: viewDistance и mainHand должны быть в разумных
 * пределах.
 */
public class ClientSettingsCheck {

  private boolean received;
  private int viewDistance = -1;
  private int mainHand = -1;

  public void onPacket(ClientSettingsPacket packet) {
    if (this.received) {
      return;
    }
    this.viewDistance = packet.getViewDistance();
    this.mainHand = packet.getMainHand();
    this.received = true;
  }

  public CheckResult check() {
    if (!this.received) {
      return CheckResult.fail("ClientSettings packet was not received");
    }

    if (this.viewDistance < Settings.IMP.MAIN.MIN_CLIENT_VIEW_DISTANCE
        || this.viewDistance > Settings.IMP.MAIN.MAX_CLIENT_VIEW_DISTANCE) {
      return CheckResult.fail("Invalid viewDistance: " + this.viewDistance);
    }

    if (this.mainHand < 0 || this.mainHand > 1) {
      return CheckResult.fail("Invalid mainHand: " + this.mainHand);
    }

    return CheckResult.ok();
  }

  public boolean isReceived() {
    return this.received;
  }

  public int getViewDistance() {
    return this.viewDistance;
  }

  public int getMainHand() {
    return this.mainHand;
  }
}
