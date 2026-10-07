package dev.decadence.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.lang.reflect.Method;

import decadence.core.Campaign;
import decadence.core.World;

/**
 * Robolectric ile JVM içinde çalışan uçtan uca akış testi. Emülatör gerektirmez.
 * Harita dönüşümü MapView.fitTransform ile aynı sabitlerle hesaplanır (800x1200 görünüm, 0.92 ölçek).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = "src/main/AndroidManifest.xml")
public class MainActivityTest {
    private static final int VIEW_W = 800;
    private static final int VIEW_H = 1200;
    private static final float SCALE = Math.min(VIEW_W / (float) World.MAP_WIDTH, VIEW_H / (float) World.MAP_HEIGHT) * 0.92f;
    private static final float OFF_X = (VIEW_W - World.MAP_WIDTH * SCALE) / 2f;
    private static final float OFF_Y = (VIEW_H - World.MAP_HEIGHT * SCALE) / 2f;

    private SharedPreferences prefs;

    @Before
    public void resetStorage() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("decadence", 0);
        prefs.edit().clear().commit();
    }

    @Test
    public void createRoomPersistsAndShowsStatus() {
        MainActivity activity = start();
        createRoom(activity);

        assertNotNull(prefs.getString("code", null));
        assertEquals(1, prefs.getInt("player", -1));
        assertTrue(status(activity).contains("Oyuncu 1"));
        assertTrue(status(activity).contains("Gün 0"));
    }

    @Test
    public void tappingNodeRoutesOwnArmy() {
        MainActivity activity = createRoomActivity();
        tapNode(activity, 19);

        Campaign campaign = currentCampaign();
        World later = campaign.stateAt(campaign.dayAt(System.currentTimeMillis()) + 30);
        assertEquals(19, later.army(World.PLAYER_ARMY_1).at());
        assertEquals(-1, later.army(World.PLAYER_ARMY_1).dest());
    }

    @Test
    public void roomSurvivesActivityRecreation() {
        MainActivity first = createRoomActivity();
        tapNode(first, 19);
        String code = prefs.getString("code", null);

        MainActivity second = start();
        assertEquals(code, prefs.getString("code", null));
        assertTrue(status(second).contains("Oyuncu 1"));
    }

    @Test
    public void twoDevicesExchangeCodesAndConverge() throws Exception {
        // Cihaz A: odayı kurar, ordusunu düğüm 19'a yollar.
        MainActivity deviceA = createRoomActivity();
        tapNode(deviceA, 19);
        String codeA = prefs.getString("code", null);

        // Cihaz B: A'nın kodunu girer (Oyuncu 2), ordusunu düğüm 180'e yollar.
        prefs.edit().clear().commit();
        MainActivity deviceB = start();
        invokeApplyCode(deviceB, codeA);
        assertEquals(2, prefs.getInt("player", -1));
        tapNode(deviceB, 180);
        String codeB = prefs.getString("code", null);

        // Cihaz A: B'nin güncel kodunu alır.
        prefs.edit().clear().commit();
        prefs.edit().putString("code", codeA).putInt("player", 1).commit();
        MainActivity deviceA2 = start();
        invokeApplyCode(deviceA2, codeB);
        String merged = prefs.getString("code", null);

        Campaign fromA = Campaign.fromCode(merged);
        Campaign fromB = Campaign.fromCode(codeB);
        long later = fromA.dayAt(System.currentTimeMillis()) + 30;
        assertEquals(fromA.stateAt(later).hash(), fromB.stateAt(later).hash());
        assertEquals(19, fromA.stateAt(later).army(World.PLAYER_ARMY_1).at());
        assertEquals(180, fromA.stateAt(later).army(World.PLAYER_ARMY_2).at());
    }

    @Test
    public void foreignRoomCodeIsRejected() throws Exception {
        MainActivity activity = createRoomActivity();
        String mine = prefs.getString("code", null);
        Campaign other = new Campaign(12345L, System.currentTimeMillis(), 3_600_000L);
        String foreign = other.exportCode();

        invokeApplyCode(activity, foreign);
        assertEquals(mine, prefs.getString("code", null));
    }

    private MainActivity start() {
        return Robolectric.buildActivity(MainActivity.class).create().resume().get();
    }

    private MainActivity createRoomActivity() {
        MainActivity activity = start();
        createRoom(activity);
        return activity;
    }

    private void createRoom(MainActivity activity) {
        clickButton(activity, "Yeni oda");
        ShadowAlertDialog.getLatestAlertDialog()
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .performClick();
    }

    private void tapNode(MainActivity activity, int node) {
        MapView map = findMap(activity.findViewById(android.R.id.content));
        map.layout(0, 0, VIEW_W, VIEW_H);
        World world = currentCampaign().stateAt(0);
        float x = OFF_X + world.x(node) * SCALE;
        float y = OFF_Y + world.y(node) * SCALE;
        map.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, x, y, 0));
    }

    private void invokeApplyCode(MainActivity activity, String code) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod("applyCode", String.class);
        m.setAccessible(true);
        m.invoke(activity, code);
    }

    private Campaign currentCampaign() {
        return Campaign.fromCode(prefs.getString("code", null));
    }

    private String status(MainActivity activity) {
        return findText(activity.findViewById(android.R.id.content));
    }

    private static void clickButton(View root, String text) {
        Button b = findButton(root, text);
        assertNotNull("düğme yok: " + text, b);
        b.performClick();
    }

    private static void clickButton(MainActivity activity, String text) {
        clickButton(activity.findViewById(android.R.id.content), text);
    }

    private static Button findButton(View v, String text) {
        if (v instanceof Button && text.equals(((Button) v).getText().toString())) return (Button) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button b = findButton(g.getChildAt(i), text);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static MapView findMap(View v) {
        if (v instanceof MapView) return (MapView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                MapView m = findMap(g.getChildAt(i));
                if (m != null) return m;
            }
        }
        return null;
    }

    private static String findText(View v) {
        if (v instanceof TextView && !(v instanceof Button)) return ((TextView) v).getText().toString();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                String s = findText(g.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }
}
