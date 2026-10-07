package decadence.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CoreTest {
    static final long SEED = 42L;
    static final long EPOCH = 1_700_000_000_000L;
    static final long DAY_MS = 3_600_000L; // 1 oyun günü = 1 gerçek saat

    public static void main(String[] args) {
        dayClock();
        mergeOrderDoesNotMatter();
        lateCommandConvergesToFreshReplay();
        foreignArmyCommandIsRejected();
        musterPeriodKeepsKingdomsStill();
        playerArmyArrivesOrIsDefeatedVisibly();
        economyStaysInBounds();
        codeRoundTripAndTwoDeviceSync();
        System.out.println("OK: 8 test geçti");
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    static Campaign newCampaign() {
        return new Campaign(SEED, EPOCH, DAY_MS);
    }

    /** Gün 10: oyuncu 1 ordusunu 150'ye yollar. Gün 25: oyuncu 2 ordusunu 30'a. Gün 40: oyuncu 2 yabancı orduya komut (reddedilir). */
    static List<Command> sampleCommands() {
        return new ArrayList<>(Arrays.asList(
                new Command(10, 1, 1, Command.MOVE, World.PLAYER_ARMY_1, 150),
                new Command(25, 2, 1, Command.MOVE, World.PLAYER_ARMY_2, 30),
                new Command(40, 2, 2, Command.MOVE, World.PLAYER_ARMY_1, 5)));
    }

    static void dayClock() {
        Campaign c = newCampaign();
        check(c.dayAt(EPOCH) == 0, "gün 0 olmalı");
        check(c.dayAt(EPOCH + 3 * DAY_MS + 5) == 3, "3 saat sonra gün 3 olmalı");
        check(c.dayAt(EPOCH - 1000) == 0, "epoch öncesi 0'a kıstırılmalı");
        check(Math.abs(c.dayFraction(EPOCH + DAY_MS / 2) - 0.5) < 1e-9, "gün içi ilerleme yarım olmalı");
    }

    static void mergeOrderDoesNotMatter() {
        List<Command> cmds = sampleCommands();
        List<Command> reversed = new ArrayList<>(cmds);
        Collections.reverse(reversed);

        Campaign a = newCampaign();
        a.merge(cmds);
        Campaign b = newCampaign();
        b.merge(reversed);

        check(a.stateAt(200).hash() == b.stateAt(200).hash(), "birleştirme sırası sonucu değiştirdi");
        check(a.stateAt(200).hash() == a.stateAt(200).hash(), "aynı replay iki kez farklı çıktı");
    }

    static void lateCommandConvergesToFreshReplay() {
        Campaign a = newCampaign();
        a.merge(sampleCommands());
        long before = a.stateAt(5).hash();

        // Oyuncu 1, offline iken gün 5'te vermiş; A bunu şimdi aldı.
        Command late = new Command(5, 1, 2, Command.MOVE, World.PLAYER_ARMY_1, 20);
        a.merge(Collections.singletonList(late));
        long after = a.stateAt(5).hash();
        check(before != after, "geç gelen komut geçmişi değiştirmeli");

        List<Command> all = sampleCommands();
        all.add(late);
        Campaign fresh = newCampaign();
        fresh.merge(all);
        check(a.stateAt(100).hash() == fresh.stateAt(100).hash(),
                "geç komut sonrası durum taze replay ile eşleşmeli");
    }

    static void foreignArmyCommandIsRejected() {
        Campaign c = newCampaign();
        c.merge(sampleCommands());
        World w = c.stateAt(50);
        check(w.rejected().size() == 1, "yabancı ordu komutu reddedilmeli, sayı=" + w.rejected().size());
        check(w.rejected().get(0).player() == 2, "reddedilen komut oyuncu 2'ye ait olmalı");
    }

    static void musterPeriodKeepsKingdomsStill() {
        Campaign c = newCampaign();
        c.merge(sampleCommands());
        World early = c.stateAt(World.MUSTER_DAYS - 1);
        World before = c.stateAt(0);
        for (int i = 0; i < World.PLAYER_ARMY_1; i++) {
            check(before.army(i).at() == early.army(i).at(), "hazırlık döneminde krallık ordusu hareket etti");
        }
        World later = c.stateAt(120);
        boolean anyMoved = false;
        for (int i = 0; i < World.PLAYER_ARMY_1; i++) {
            if (later.army(i).at() != early.army(i).at()) anyMoved = true;
        }
        check(anyMoved, "hazırlık sonrası hiçbir krallık ordusu hareket etmedi");
    }

    /** Oyuncu ordusu hedefe varır ya da yolda yok olur; kayıtta ya da ordu durumunda görünmeli. */
    static void playerArmyArrivesOrIsDefeatedVisibly() {
        Campaign c = newCampaign();
        c.merge(sampleCommands());
        World w = c.stateAt(300);
        Army army = w.army(World.PLAYER_ARMY_1);
        boolean arrived = army.alive() && army.at() == 150 && army.dest() == -1;
        boolean defeated = false;
        for (String e : w.events()) {
            if (e.contains("Oyuncu 1")) defeated = true;
        }
        check(arrived || defeated || !army.alive(), "oyuncu ordusu ne vardı ne de kayıt düştü");
    }

    static void economyStaysInBounds() {
        Campaign c = newCampaign();
        c.merge(sampleCommands());
        World w = c.stateAt(200);
        for (int n = 0; n < World.NODES; n++) {
            Settlement s = w.settlement(n);
            check(s.pop() >= 20 && s.pop() <= World.MAX_POP, "nüfus sınır dışı: " + s.pop());
            check(s.food() >= 0 && s.food() <= s.pop() / 2 + 100, "yiyecek sınır dışı: " + s.food());
            check(s.garrison() >= 0, "negatif garnizon");
        }
        check(!w.events().isEmpty(), "200 günde hiçbir olay olmadı");
    }

    static void codeRoundTripAndTwoDeviceSync() {
        Campaign a = newCampaign();
        a.merge(sampleCommands());
        String code = a.exportCode();

        Campaign b = Campaign.fromCode(code);
        check(b.stateAt(120).hash() == a.stateAt(120).hash(), "kod ile aktarım sonucu değiştirdi");

        // B, A'dan bağımsız olarak gün 60'ta komut verir; sonra A'ya kodu gönderir.
        b.issueMove(EPOCH + 60 * DAY_MS, 2, World.PLAYER_ARMY_2, 100);
        check(a.importCode(b.exportCode()) == 1, "A yalnızca 1 yeni komut almalıydı");
        check(a.stateAt(120).hash() == b.stateAt(120).hash(), "iki cihaz senkron olmadı");

        Campaign other = new Campaign(SEED + 1, EPOCH, DAY_MS);
        try {
            other.importCode(code);
            throw new AssertionError("yanlış oda kabul edildi");
        } catch (IllegalArgumentException expected) {
            // beklenen
        }
    }
}
