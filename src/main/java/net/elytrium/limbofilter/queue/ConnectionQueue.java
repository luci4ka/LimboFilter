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

package net.elytrium.limbofilter.queue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.elytrium.limbofilter.Settings;

public class ConnectionQueue {

  private volatile Semaphore semaphore;

  public ConnectionQueue() {
    this.semaphore = new Semaphore(Settings.IMP.MAIN.MAX_CONCURRENT_CHECKS, true);
  }

  public void reload() {
    this.semaphore = new Semaphore(Settings.IMP.MAIN.MAX_CONCURRENT_CHECKS, true);
  }

  public boolean acquire() {
    try {
      return this.semaphore.tryAcquire(
          Settings.IMP.MAIN.QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  public void release() {
    this.semaphore.release();
  }
}
