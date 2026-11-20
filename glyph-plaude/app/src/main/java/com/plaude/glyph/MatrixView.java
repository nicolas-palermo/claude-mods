package com.plaude.glyph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.plaude.glyph.core.Grid;

/** Dibuja la matriz redonda en pantalla: sirve de vista previa y de espejo del juego. */
public class MatrixView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] levels = new int[Grid.SIZE * Grid.SIZE];

    public MatrixView(Context context) {
        super(context);
    }

    /** Niveles 0..255, 169 valores. */
    public void setLevels(int[] levels) {
        this.levels = levels;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cell = size / Grid.SIZE;
        paint.setColor(Color.rgb(8, 8, 9));
        canvas.drawCircle(size / 2, size / 2, size / 2, paint);
        for (int y = 0; y < Grid.SIZE; y++) {
            for (int x = 0; x < Grid.SIZE; x++) {
                if (!Grid.exists(x, y)) continue;
                int v = levels[y * Grid.SIZE + x];
                int c = 36 + v * (246 - 36) / 255;
                paint.setColor(Color.rgb(c, c, Math.min(255, c + 2)));
                canvas.drawCircle(x * cell + cell / 2, y * cell + cell / 2, cell * 0.38f, paint);
            }
        }
    }
}
