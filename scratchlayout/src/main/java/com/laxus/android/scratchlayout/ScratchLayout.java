package com.laxus.android.scratchlayout;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.RestrictTo;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.support.annotation.RestrictTo.Scope.GROUP_ID;

public class ScratchLayout extends FrameLayout {

    public interface OnRevealListener {
        /**
         * called when ScratchLayout has been revealed
         *
         * @param scratch self
         */
        void onRevealed(ScratchLayout scratch);
    }

    @RestrictTo(GROUP_ID)
    @IntDef({
            MASK_MODE_ENLARGE,
            MASK_MODE_FIT,
            MASK_MODE_REPEAT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MaskMode {
    }

    public static final int MASK_MODE_ENLARGE = 0;
    public static final int MASK_MODE_FIT = 1;
    public static final int MASK_MODE_REPEAT = 2;


    private static final float TOUCH_TOLERANCE = 4;

    private static final int DEFAULT_PERCENT = 86;
    private static final int DEFAULT_STROKE_WIDTH = 20;//dp

    //track touch path
    private float mLastMotionX;
    private float mLastMotionY;

    private boolean mIsBeingScratching;

    /**
     * Bitmap holding the scratch region.
     */
    private Bitmap mScratchBitmap;

    /**
     * Drawable canvas area through which the scratch-able area is drawn.
     */
    private Canvas mCanvas;

    /**
     * Used to change mask drawable draw behavior
     */
    private Matrix mMatrix;

    /**
     * Path holding the erasing path done by the user.
     */
    private Path mErasePath = new Path();

    /**
     * Paint used to draw ScratchBitmap,no need to set any other attribute
     */
    private Paint mPaint = new Paint(Paint.DITHER_FLAG);

    /**
     * Paint for erasing the scratch region.
     */
    private Paint mErasePaint = new Paint();

    private List<OnRevealListener> mOnRevealListeners;

    /**
     * Whether ScratchLayout has revealed
     */
    private boolean mIsRevealed = false;

    /**
     * Whether ScratchLayout auto reveal when scratched pixels has reached the percent
     */
    private boolean mAutoReveal;

    /**
     * ScratchBitmap pixels scratch edge
     */
    private int mRevealPercent;

    /**
     * Whether ScratchLayout could be scratched
     */
    private boolean mScratchEnable;

    /**
     * mask drawable
     */
    private Drawable mMask;
    /**
     * define how the drawable would been drawn as a mask,
     * should be
     * {@link #MASK_MODE_ENLARGE},
     * {@link #MASK_MODE_FIT},
     * {@link #MASK_MODE_REPEAT}
     */
    private int mMaskMode;

    private int mEraseDuration = 300;

    private ValueAnimator mCurClearAnimation;
    private Thread mCurCheckingThread;
    private RevealHandler mRevealHandler;
    private Runnable mCheckRevealRunnable = new Runnable() {
        @Override
        public void run() {
            int w = mScratchBitmap.getWidth();
            int h = mScratchBitmap.getHeight();

            float wipeArea = 0;
            float totalArea = w * h;
            int[] pixels = new int[w * h];

            mScratchBitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int index = i + j * w;
                    if (pixels[index] == 0) {
                        wipeArea++;
                    }
                }
            }
            if (wipeArea > 0 && totalArea > 0) {
                int percent = (int) (wipeArea * 100 / totalArea);
                if (percent > mRevealPercent) {
                    Message msg = Message.obtain();
                    msg.what = 1;
                    mRevealHandler.sendMessage(msg);
                }
            }
        }
    };

    public ScratchLayout(Context context) {
        this(context, null);
    }

    public ScratchLayout(Context context, AttributeSet set) {
        this(context, set, 0);
    }

    public ScratchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScratchLayout);
        mRevealPercent = a.getInt(R.styleable.ScratchLayout_revealPercent, DEFAULT_PERCENT);
        mAutoReveal = a.getBoolean(R.styleable.ScratchLayout_autoReveal, true);

        mMaskMode = a.getInt(R.styleable.ScratchLayout_maskMode, MASK_MODE_ENLARGE);
        mMask = a.getDrawable(R.styleable.ScratchLayout_mask);

        int strokeWidth = a.getDimensionPixelSize(R.styleable.ScratchLayout_strokeWidth,
                (int) (DEFAULT_STROKE_WIDTH * getResources().getDisplayMetrics().density));
        a.recycle();


        mErasePaint.setAntiAlias(true);
        mErasePaint.setDither(true);
        mErasePaint.setStyle(Paint.Style.STROKE);
        mErasePaint.setStrokeJoin(Paint.Join.BEVEL);
        mErasePaint.setStrokeCap(Paint.Cap.ROUND);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        setStrokeWidth(strokeWidth);

        mRevealHandler = new RevealHandler(Looper.getMainLooper(), this);
        setWillNotDraw(false);
        setScratchEnable(true);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createScratchBitmap(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCurCheckingThread != null && mCurCheckingThread.isAlive()) {
            try {
                mCurCheckingThread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mRevealHandler.removeMessages(1);
        if (mCurClearAnimation != null && mCurClearAnimation.isRunning()) {
            mCurClearAnimation.cancel();
        }
    }

    private void createScratchBitmap(int vWidth, int vHeight) {
        //create only if there is a visible area and mask drawable has been set
        if (vWidth > 0 && vHeight > 0 && mMask != null) {
            if (mScratchBitmap != null && !mScratchBitmap.isRecycled()) {
                mScratchBitmap.recycle();
            }
            mScratchBitmap = Bitmap.createBitmap(vWidth, vHeight, Bitmap.Config.ARGB_8888);
            if (mCanvas == null) {
                mCanvas = new Canvas(mScratchBitmap);
            } else {
                mCanvas.setBitmap(mScratchBitmap);
                //reset matrix
                mCanvas.setMatrix(null);
            }

            int dWidth = mMask.getIntrinsicWidth();
            int dHeight = mMask.getIntrinsicHeight();
            if (dWidth <= 0 || dHeight <= 0 || mMaskMode == MASK_MODE_FIT) {
                //if mask has no intrinsicSize or a fit mode has been applied
                //then just fill view size
                mMask.setBounds(0, 0, vWidth, vHeight);

                mMask.draw(mCanvas);
            } else if (mMaskMode == MASK_MODE_ENLARGE) {
                //do scale with mask drawable's own bounds
                mMask.setBounds(0, 0, dWidth, dHeight);
                if (mMatrix == null) {
                    mMatrix = new Matrix();
                } else {
                    mMatrix.reset();
                }

                float scale;
                float transX = 0, transY = 0;
                if (dWidth * vHeight > dHeight * vWidth) {
                    scale = (float) vHeight / (float) dHeight;
                    transX = (vWidth - dWidth * scale) * .5f;
                } else {
                    scale = (float) vWidth / (float) dWidth;
                    transY = (vHeight - dHeight * scale) * .5f;
                }
                mMatrix.setScale(scale, scale);
                mMatrix.postTranslate(Math.round(transX), Math.round(transY));

                mCanvas.concat(mMatrix);
                mMask.draw(mCanvas);
                //reset canvas after drawable mask
                mCanvas.setMatrix(null);
            } else {
                Bitmap renderBitmap;
                if (mMask instanceof BitmapDrawable) {
                    renderBitmap = ((BitmapDrawable) mMask).getBitmap();
                } else {
                    //if mask drawable is not a BitmapDrawable(eg.custom drawable),then create a temp render bitmap
                    mMask.setBounds(0, 0, dWidth, dHeight);
                    renderBitmap = createRenderBitmap(mMask);
                }

                BitmapShader shader = new BitmapShader(renderBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                Paint paint = new Paint();
                paint.setShader(shader);
                mCanvas.drawPaint(paint);

            }
        } else {
            if (mScratchBitmap != null && !mScratchBitmap.isRecycled()) {
                mScratchBitmap.recycle();
            }
            mScratchBitmap = null;
            mCanvas = null;
        }
    }

    private Bitmap createRenderBitmap(Drawable source) {
        Bitmap temp = Bitmap.createBitmap(source.getIntrinsicWidth(), source.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(temp);
        source.draw(canvas);
        return temp;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mScratchBitmap != null) {
            canvas.drawBitmap(mScratchBitmap, 0, 0, mPaint);
        }
    }

    /**
     * reveal hidden,it will cause mask been erased
     */
    public void reveal() {
        onRevealed();
    }

    private void clear(boolean animated) {
        if (animated) {
            if (mCurClearAnimation != null && mCurClearAnimation.isRunning()) {
                mCurClearAnimation.cancel();
            }
            mCurClearAnimation = createClearAnimation();
            mCurClearAnimation.start();
        } else {
            eraseMask();
        }

        dispatchOnRevealEvent();
    }

    private void eraseMask() {
        //just set scratch drawable null
        if (mScratchBitmap != null && !mScratchBitmap.isRecycled()) {
            mScratchBitmap.recycle();
        }
        mScratchBitmap = null;
        invalidate();
    }

    private ValueAnimator createClearAnimation() {
        final int oriAlpha = mPaint.getAlpha();
        ValueAnimator animator = ValueAnimator.ofInt(oriAlpha, 0);
        animator.setDuration(mEraseDuration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mPaint.setAlpha((Integer) valueAnimator.getAnimatedValue());
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                mPaint.setAlpha(oriAlpha);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                eraseMask();
                mPaint.setAlpha(oriAlpha);
            }
        });

        return animator;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //if ScratchLayout has no mask,do not intercept touch event
        if (!hasMask()) {
            return false;
        }
        //if ScratchLayout has not been revealed,intercept touch event no matter what
        if (!isRevealed()) {
            return true;
        }
        int action = ev.getAction();
        float x = ev.getX();
        float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionX = x;
                mLastMotionY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaX = x - mLastMotionX;
                float deltaY = y - mLastMotionY;
                if ((Math.abs(deltaX) > TOUCH_TOLERANCE ||
                        Math.abs(deltaY) > TOUCH_TOLERANCE) && !mIsBeingScratching) {
                    mIsBeingScratching = true;

                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                mLastMotionX = x;
                mLastMotionY = y;
                break;
        }
        return mIsBeingScratching;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mScratchEnable || isRevealed() || !hasMask()) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mErasePath.reset();
                mErasePath.moveTo(x, y);
                mLastMotionX = x;
                mLastMotionY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaX = x - mLastMotionX;
                float deltaY = y - mLastMotionY;
                if ((Math.abs(deltaX) > TOUCH_TOLERANCE ||
                        Math.abs(deltaY) > TOUCH_TOLERANCE) && !mIsBeingScratching) {
                    mIsBeingScratching = true;

                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (mIsBeingScratching) {
                    mErasePath.quadTo(mLastMotionX, mLastMotionY, (x + mLastMotionX) / 2, (y + mLastMotionY) / 2);
                    mErasePath.lineTo(x, y);
                    drawPath();
                }
                mLastMotionX = x;
                mLastMotionY = y;
                break;
            case MotionEvent.ACTION_UP:
                checkIsRevealed();
                endScratching();
                break;
        }
        return true;
    }

    private void drawPath() {
        mCanvas.drawPath(mErasePath, mErasePaint);
        // kill this so we don't double draw
        mErasePath.reset();
        mErasePath.moveTo(mLastMotionX, mLastMotionY);

        invalidate();
    }

    private void endScratching() {
        mIsBeingScratching = false;
    }

    private void checkIsRevealed() {
        mCurCheckingThread = new Thread(mCheckRevealRunnable);
        mCurCheckingThread.start();
    }

    private void onRevealed() {
        mIsRevealed = true;
        //if should autoReveal, then clear pixels and dispatchRevealEvent when clear,
        //otherwise just dispatchRevealEvent
        if (mAutoReveal) {
            clear(true);
        } else {
            dispatchOnRevealEvent();
        }
    }

    private void dispatchOnRevealEvent() {
        if (mOnRevealListeners != null) {
            for (int i = 0; i < mOnRevealListeners.size(); ++i) {
                mOnRevealListeners.get(i).onRevealed(this);
            }
        }
    }

    /**
     * reset scratch drawable and state
     */
    public void reset() {
        mIsRevealed = false;
        mErasePath.reset();
        createScratchBitmap(getMeasuredWidth(), getMeasuredHeight());
        invalidate();
    }

    /**
     * @return true if ScratchLayout has been revealed
     */
    public boolean isRevealed() {
        return mIsRevealed;
    }

    /**
     * @return true ScratchLayout has mask
     */
    public boolean hasMask() {
        return mMask != null;
    }

    /**
     * Set the strokes width
     */
    public void setStrokeWidth(int strokeWidth) {
        mErasePaint.setStrokeWidth(strokeWidth);
    }

    /**
     * Set reveal percent,when ScratchBitmap scratch pixels has reach the percent,
     * ScratchLayout consider self has been reveal no matter if the ScratchBitmap has been totally scratched
     *
     * @param percent reveal percent must be (0,100]
     */
    public void setRevealPercent(int percent) {
        if (percent <= 0 && percent > 100) {
            throw new IllegalArgumentException("reveal percent must be (0,100]");
        }
        mRevealPercent = percent;
    }

    /**
     * Whether ScratchLayout reveal self when reach the reveal percent
     *
     * @param autoReveal true if ScratchLayout should reveal when reach reveal percent
     */
    public void setAutoReveal(boolean autoReveal) {
        mAutoReveal = autoReveal;
    }

    /**
     * Whether ScratchLayout could be scratch
     *
     * @param enable true if ScratchLayout could be scratched
     */
    public void setScratchEnable(boolean enable) {
        mScratchEnable = enable;
    }


    /**
     * set a mask drawable for scratching
     *
     * @param mask mask drawable
     */
    public void setMask(Drawable mask) {
        setMask(mask, mMaskMode);
    }

    /**
     * set a mask drawable for scratching
     *
     * @param mask mask drawable
     * @param mode determine how ScratchLayout draw this drawable when mask is smaller than ScratchLayout
     */
    public void setMask(Drawable mask, @MaskMode int mode) {
        mMask = mask;
        mMaskMode = mode;
        reset();
    }

    /**
     * set clear animation duration
     *
     * @param duration animation duration
     */
    public void setEraseDuration(int duration) {
        mEraseDuration = duration;
    }


    /**
     * Add a OnRevealListener to ScratchLayout
     *
     * @param listener listener that will be call when ScratchLayout has been revealed
     */
    public void addOnRevealListener(OnRevealListener listener) {
        if (mOnRevealListeners == null) {
            mOnRevealListeners = new ArrayList<>();
        }
        mOnRevealListeners.add(listener);
    }

    /**
     * Remove a OnRevealListener
     *
     * @param listener listener that should be removed
     */
    public void removeOnRevealListener(OnRevealListener listener) {
        if (mOnRevealListeners != null) {
            mOnRevealListeners.remove(listener);
        }
    }

    static class RevealHandler extends Handler {

        private WeakReference<ScratchLayout> mReference;

        RevealHandler(Looper looper, ScratchLayout layout) {
            super(looper);
            mReference = new WeakReference<>(layout);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                ScratchLayout scratchLayout = mReference.get();
                if (scratchLayout != null) {
                    scratchLayout.onRevealed();
                }
            }
        }
    }
}
