package dev.decadence.campaign;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import decadence.core.Army;
import decadence.core.World;

/**
 * Kampanya haritası. Yalnızca çizer ve dokunuşu bildirir; durum World'den gelir.
 * Ordular gün içinde bir sonraki düğüme doğru görsel olarak kayar ({@code frac}); bu değer state'e girmez.
 */
public class MapView extends View {

    public interface OnNodeTapListener {
        void onNodeTap(int node);
    }

    private static final int COLOR_EDGE = 0x662A4A63;
    private static final int COLOR_NODE = 0xFF9FB8C8;
    private static final int COLOR_NPC = 0xFFB0B7BF;
    private static final int COLOR_P1 = 0xFF3EE6D2;  // fosforlu turkuaz
    private static final int COLOR_P2 = 0xFFFFC857;  // sıcak sarı
    private static final int COLOR_ROUTE = 0x993EE6D2;

    private final float density;
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private World world;
    private int player = 1;
    private double frac;
    private OnNodeTapListener listener;

    private float scale;
    private float offX;
    private float offY;

    public MapView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        edgePaint.setColor(COLOR_EDGE);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1.5f * density);

        nodePaint.setColor(COLOR_NODE);
        nodePaint.setStyle(Paint.Style.FILL);

        armyPaint.setStyle(Paint.Style.FILL);

        routePaint.setColor(COLOR_ROUTE);
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(2f * density);
        routePaint.setPathEffect(new DashPathEffect(new float[] {8 * density, 6 * density}, 0));
    }

    /** Her karede çağrılır. {@code world} null ise harita boş çizilir. */
    public void update(World world, int player, double frac) {
        this.world = world;
        this.player = player;
        this.frac = frac;
    }

    public void setOnNodeTapListener(OnNodeTapListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (world == null) return;
        fitTransform();

        for (int i = 0; i < World.NODES; i++) {
            for (int j : world.neighbors(i)) {
                if (j > i) canvas.drawLine(px(i), py(i), px(j), py(j), edgePaint);
            }
        }
        for (int i = 0; i < World.NODES; i++) {
            canvas.drawCircle(px(i), py(i), 3 * density, nodePaint);
        }

        int mine = World.PLAYER_ARMY_1 + (player - 1);
        Army own = world.army(mine);
        drawRoute(canvas, own);

        for (int a = 0; a < world.armyCount(); a++) {
            Army army = world.army(a);
            float[] p = armyPosition(army);
            armyPaint.setColor(colorFor(army.owner()));
            canvas.drawCircle(p[0], p[1], (a == mine ? 7 : 5) * density, armyPaint);
            if (a == mine) canvas.drawCircle(p[0], p[1], 13 * density, routePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && world != null && listener != null) {
            int node = nearestNode(event.getX(), event.getY());
            if (node >= 0) listener.onNodeTap(node);
        }
        return true;
    }

    private void fitTransform() {
        float sx = getWidth() / (float) World.MAP_WIDTH;
        float sy = getHeight() / (float) World.MAP_HEIGHT;
        scale = Math.min(sx, sy) * 0.92f;
        offX = (getWidth() - World.MAP_WIDTH * scale) / 2f;
        offY = (getHeight() - World.MAP_HEIGHT * scale) / 2f;
    }

    private float px(int node) {
        return offX + world.x(node) * scale;
    }

    private float py(int node) {
        return offY + world.y(node) * scale;
    }

    /** Ordu konumu: düğümdeyse düğüm, yoldaysa bir sonraki düğüme doğru {@code frac} kadar ilerlemiş. */
    private float[] armyPosition(Army army) {
        float x = px(army.at());
        float y = py(army.at());
        if (army.dest() >= 0) {
            int next = world.nextHop(army.at(), army.dest());
            x += (px(next) - x) * frac;
            y += (py(next) - y) * frac;
        }
        return new float[] {x, y};
    }

    private void drawRoute(Canvas canvas, Army army) {
        if (army.dest() < 0) return;
        Path path = new Path();
        path.moveTo(px(army.at()), py(army.at()));
        int cur = army.at();
        // Izgara bağlı olduğu için döngü en fazla NODES adımda biter; yine de bir üst sınır koyuyoruz.
        for (int steps = 0; cur != army.dest() && steps < World.NODES; steps++) {
            cur = world.nextHop(cur, army.dest());
            path.lineTo(px(cur), py(cur));
        }
        canvas.drawPath(path, routePaint);
    }

    private int nearestNode(float tx, float ty) {
        fitTransform();
        float best = 20 * density;
        int bestNode = -1;
        for (int i = 0; i < World.NODES; i++) {
            float dx = px(i) - tx;
            float dy = py(i) - ty;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < best) {
                best = d;
                bestNode = i;
            }
        }
        return bestNode;
    }

    private static int colorFor(int owner) {
        if (owner == 1) return COLOR_P1;
        if (owner == 2) return COLOR_P2;
        return COLOR_NPC;
    }
}
