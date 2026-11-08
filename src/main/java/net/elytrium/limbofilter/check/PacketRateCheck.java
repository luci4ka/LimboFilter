package net.elytrium.limbofilter.check;

import net.elytrium.limbofilter.Settings;

public class PacketRateCheck {

    private long lastMoveTime;
    private int fastMoveCount;

    public CheckResult check() {
        long now = System.currentTimeMillis();
        if (this.lastMoveTime != 0 && (now - this.lastMoveTime) < Settings.IMP.MAIN.MIN_MOVE_INTERVAL_MS) {
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