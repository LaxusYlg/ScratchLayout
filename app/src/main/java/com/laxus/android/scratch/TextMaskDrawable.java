package com.laxus.android.scratch;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.Log;

public class TextMaskDrawable extends Drawable {
    private String mText = "TextMaskDrawable";
    private Paint mPaint = new Paint();


    public TextMaskDrawable(String text, int textSize) {
        mText = text;
        init(textSize);
    }

    private void init(int textSize) {
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(textSize);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawColor(Color.GRAY);

        Rect bounds = getBounds();
        Paint.FontMetricsInt fontMetricsInt = mPaint.getFontMetricsInt();
        int baseline = (bounds.bottom + bounds.top - fontMetricsInt.bottom - fontMetricsInt.top) / 2;
        float left = (bounds.width() - (mText.length() * mPaint.getTextSize())) / 2;
        float top = (bounds.height() + (mPaint.getTextSize())) / 2;
        canvas.drawText(mText,bounds.centerX(), baseline, mPaint);
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
