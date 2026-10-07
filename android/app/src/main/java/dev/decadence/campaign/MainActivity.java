package dev.decadence.campaign;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

import decadence.core.Army;
import decadence.core.Campaign;
import decadence.core.World;

/**
 * İskelet: oda kur / koda katıl / kodu paylaş, harita üzerinde ordu yönlendir.
 * Oyun durumu decadence.core'dan gelir; bu sınıf yalnızca UI ve kalıcılık yapar.
 * Not: dx 1.7 ile derlenebilmesi için lambda ve method ref kullanılmıyor.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "decadence";
    private static final String KEY_CODE = "code";
    private static final String KEY_PLAYER = "player";
    /** 1 oyun günü = 1 gerçek saat. Oda kodunda taşınır, iki cihaz aynı süreyi kullanır. */
    private static final long DAY_MS = 60L * 60L * 1000L;

    private static final int BG = 0xFF0B1B2B;     // lacivert gece
    private static final int TEXT = 0xFFE8DCC4;  // sıcak krem

    private SharedPreferences prefs;
    private Campaign campaign;   // null: henüz oda yok
    private int player = 1;      // 1 = odayı kuran, 2 = koda katılan
    private World world;         // önbellek: gün değişince ya da log değişince yenilenir
    private long worldDay = -2;
    private boolean dirty = true;

    private TextView status;
    private TextView events;
    private MapView map;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        player = prefs.getInt(KEY_PLAYER, 1);
        String code = prefs.getString(KEY_CODE, null);
        if (code != null) {
            try {
                campaign = Campaign.fromCode(code);
            } catch (IllegalArgumentException e) {
                toast("Kayıtlı oda okunamadı");
            }
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        status = new TextView(this);
        status.setTextColor(TEXT);
        status.setTextSize(14);
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        events = new TextView(this);
        events.setTextColor(0xFFB8A98A);
        events.setTextSize(12);
        events.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(events, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        map = new MapView(this);
        map.setOnNodeTapListener(new MapView.OnNodeTapListener() {
            @Override
            public void onNodeTap(int node) {
                onNodeTapped(node);
            }
        });
        root.addView(map, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.addView(button("Yeni oda", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmNewRoom();
            }
        }), weighted());
        bar.addView(button("Kodu gir", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJoinDialog();
            }
        }), weighted());
        bar.addView(button("Kodu paylaş", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCode();
            }
        }), weighted());
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        getWindow().setStatusBarColor(BG);
        setContentView(root);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    /** Dünyayı zamana göre günceller ve haritayı yeniden çizdirir. Her saniye çağrılır. */
    private void refresh() {
        long now = System.currentTimeMillis();
        if (campaign == null) {
            world = null;
            status.setText("Oda yok. \"Yeni oda\" ile kur ya da \"Kodu gir\" ile katıl.");
            map.update(null, player, 0);
            events.setText("");
        } else {
            long day = campaign.dayAt(now);
            if (dirty || day != worldDay) {
                world = campaign.stateAt(day);
                worldDay = day;
                dirty = false;
            }
            Army mine = world.army(World.PLAYER_ARMY_1 + (player - 1));
            String army = mine.alive()
                    ? "Ordun: " + mine.men() + " asker, " + mine.food() + " iaşe"
                    : "Ordun yok oldu";
            status.setText("Gün " + day + " · Oyuncu " + player + " · " + army);
            events.setText(recentEvents());
            map.update(world, player, campaign.dayFraction(now));
        }
        map.invalidate();
    }

    /** Son üç olay; en yeni en altta. */
    private String recentEvents() {
        java.util.List<String> all = world.events();
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, all.size() - 3); i < all.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(all.get(i));
        }
        return sb.toString();
    }

    private void onNodeTapped(int node) {
        if (campaign == null || world == null) {
            toast("Önce bir oda kur ya da koda katıl");
            return;
        }
        int armyIndex = World.PLAYER_ARMY_1 + (player - 1);
        if (world.army(armyIndex).at() == node) {
            toast("Ordun zaten bu düğümde");
            return;
        }
        campaign.issueMove(System.currentTimeMillis(), player, armyIndex, node);
        onCampaignChanged();
    }

    private void confirmNewRoom() {
        new AlertDialog.Builder(this)
                .setTitle("Yeni oda")
                .setMessage("Mevcut oda ve komutların silinecek. Emin misin?")
                .setPositiveButton("Evet, sıfırla", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        createRoom();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void createRoom() {
        long now = System.currentTimeMillis();
        long seed = new SecureRandom().nextLong();
        campaign = new Campaign(seed, now, DAY_MS);
        player = 1;
        onCampaignChanged();
        toast("Oda kuruldu. \"Kodu paylaş\" ile arkadaşına gönder.");
    }

    private void showJoinDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Arkadaşının oda kodu");
        new AlertDialog.Builder(this)
                .setTitle("Kodu gir")
                .setView(input)
                .setPositiveButton("Uygula", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        applyCode(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    /** Boş odaysa koda katılır (Oyuncu 2). Odadaysa komutları birleştirir. */
    private void applyCode(String code) {
        if (code.isEmpty()) return;
        try {
            if (campaign == null) {
                campaign = Campaign.fromCode(code);
                player = 2;
                onCampaignChanged();
                toast("Odaya katıldın (Oyuncu 2). Güncel kodu arkadaşına geri gönder.");
            } else {
                int added = campaign.importCode(code);
                onCampaignChanged();
                toast(added + " yeni komut alındı"
                        + (added > 0 ? ". Güncel kodu arkadaşına geri gönder." : ""));
            }
        } catch (IllegalArgumentException e) {
            toast(e.getMessage() != null ? e.getMessage() : "Kod okunamadı");
        }
    }

    private void shareCode() {
        if (campaign == null) {
            toast("Paylaşılacak oda yok");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, campaign.exportCode());
        startActivity(Intent.createChooser(send, "Oda kodunu paylaş"));
    }

    private void onCampaignChanged() {
        prefs.edit()
                .putString(KEY_CODE, campaign.exportCode())
                .putInt(KEY_PLAYER, player)
                .apply();
        dirty = true;
        refresh();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
