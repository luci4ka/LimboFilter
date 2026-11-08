package net.elytrium.limbofilter.check;

import net.elytrium.limbofilter.Settings;

public class HoverCheck {

    private int hoverTicks;

    public CheckResult check(boolean onGround, double currentDeltaY) {
        if (currentDeltaY == 0 && !onGround) {
            ++this.hoverTicks;
            if (this.hoverTicks > Settings.IMP.MAIN.MAX_HOVER_TICKS) {
                return CheckResult.fail("Hovering in air without falling");
            }
        } else {
            this.hoverTicks = 0;
        }
        return CheckResult.ok();
    }

    public int getHoverTicks() {
        return this.hoverTicks;
    }
}