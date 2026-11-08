package net.elytrium.limbofilter.check;

import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import net.elytrium.limbofilter.Settings;

/**
 * Проверка содержимого ClientSettingsPacket:
 * viewDistance и mainHand должны быть в разумных пределах.
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