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

import org.checkerframework.checker.nullness.qual.Nullable;

/** Результат проверки: либо OK, либо причина провала. */
public final class CheckResult {

  private static final CheckResult OK = new CheckResult(null);

  @Nullable private final String reason;

  private CheckResult(@Nullable String reason) {
    this.reason = reason;
  }

  public static CheckResult ok() {
    return OK;
  }

  public static CheckResult fail(String reason) {
    return new CheckResult(reason);
  }

  public boolean isFailed() {
    return this.reason != null;
  }

  @Nullable
  public String getReason() {
    return this.reason;
  }
}
