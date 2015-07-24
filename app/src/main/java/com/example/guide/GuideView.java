package com.example.guide;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.model.LatLng;


/**
 * TODO: document your custom view class.
 */
public class GuideView extends View {
    private String mExampleString = "Hello, GuideView"; // TODO: use a default from R.string...
    private int mExampleColor = Color.BLACK; // TODO: use a default from R.color...
    private float mExampleDimension = 24; // TODO: use a default from R.dimen...
    private Drawable mExampleDrawable;

    private Paint mPaint = new Paint();
    private TextPaint mTextPaint;
    private float mTextWidth;
    private float mTextHeight;

    private LatLng target;
    private LatLng myPosition = null;

    private float mDistance;

    private float mMyDirection; //真正面から左回り
    private float mMapDirection;
    private float mShowDirection;
    int time;
    int theta = 1;

    private final String DISP_LEST = "あと";
    private final String DISP_M = "m";
    private final String DISP_KM = "km";
    private final int TIME_MAX=20;
    private int contentWidth;
    private int contentHeight;
    private int paddingRight;
    private int paddingLeft;
    private int paddingTop;
    private int paddingBottom;

    public GuideView(Context context) {
        super(context);
        init(null, 0);
    }

    public GuideView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public GuideView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public GuideView(Context context,LatLng target,LatLng myPosition){
        super(context);
        time = 0;
        this.target = target;
        this.myPosition = myPosition;
        Log.d("guideview",target.toString()+" , " + myPosition.toString());
        init(null, 0);
    }

    public void setMyPosition(LatLng myPosition){
        this.myPosition = myPosition;
    }

    public void setMyDirection(float direction){
        mMyDirection = direction;
    }
    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.GuideView, defStyle, 0);

        mExampleString = a.getString(
                R.styleable.GuideView_exampleString);
        mExampleColor = a.getColor(
                R.styleable.GuideView_exampleColor,
                mExampleColor);
        // Use getDimensionPixelSize or getDimensionPixelOffset when dealing with
        // values that should fall on pixel boundaries.
        mExampleDimension = a.getDimension(
                R.styleable.GuideView_exampleDimension,
                mExampleDimension);

        if (a.hasValue(R.styleable.GuideView_exampleDrawable)) {
            mExampleDrawable = a.getDrawable(
                    R.styleable.GuideView_exampleDrawable);
            mExampleDrawable.setCallback(this);
        }

        a.recycle();

        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();
    }

    private void invalidateTextPaintAndMeasurements() {
        mTextPaint.setTextSize(mExampleDimension);
        mTextPaint.setColor(mExampleColor);
        mExampleString="Hello, Android!";
        mTextWidth = mTextPaint.measureText(mExampleString);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;
    }

    private Runnable animateTask = new Runnable() {
        @Override
        public void run() {
            //TODO: 埋める
            float[] result = new float[1];
            if(time % 10 == 0) {
                Location.distanceBetween(myPosition.latitude, myPosition.longitude, target.latitude, target.longitude, result);
                mDistance = result[0];
            }
            if(time % 5 == 0) {
                Location source = new Location("");
                source.setLatitude(myPosition.latitude);
                source.setLongitude(myPosition.longitude);
                Location dest = new Location("");
                dest.setLatitude(target.latitude);
                dest.setLongitude(target.longitude);

                Log.d("guideview",Math.toDegrees(mMyDirection)+"");
                mMapDirection = -source.bearingTo(dest);
                mShowDirection = (float) Math.toRadians(mMapDirection) - mMyDirection;
            }
                //Log.d("aaaaaaaaa","Map:"+mMapDirection+", My:"+Math.toDegrees(mMyDirection)+" ,Show:"+Math.toDegrees(mShowDirection));
            time = (time + 1) % TIME_MAX;

            postInvalidate();
        }
    };

    public Runnable getTask(){
        return animateTask;
    }

    private float[] calcStop(float middleX,float middleY,float theta){
        float[] stop = new float[2];
        float stopX,stopY;
        float sin = (float) Math.sin(theta);
        float cos = (float) Math.cos(theta);
        float tan = (float) Math.abs(Math.tan(theta));

        float lestX = sin >= 0? middleX: contentWidth - middleX;
        float lestY = cos >= 0? middleY: contentHeight - middleY;

        if(Float.compare(Math.abs(lestX / sin),Math.abs(lestY / cos)) >= 0) {
            if(sin > 0) {
                stopX = middleX - lestY * tan;
            }else{
                stopX = middleX + lestY * tan;
            }
            if(cos > 0) {
                stopY = 0;
            }else{
                stopY = contentHeight;
            }
        }else{
            if(cos > 0) {
                stopY = middleY - lestX / tan;
            }else{
                stopY = middleY + lestX / tan;
            }
            if(sin > 0) {
                stopX = 0;
            }else{
                stopX = contentWidth;
            }
        }
        stop[0] = stopX;
        stop[1] = stopY;
        return stop;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.


        paddingLeft = getPaddingLeft();
        paddingTop = getPaddingTop();
        paddingRight = getPaddingRight();
        paddingBottom = getPaddingBottom();

        contentWidth = getWidth() - paddingLeft - paddingRight;
        contentHeight = getHeight() - paddingTop - paddingBottom;

        float middleX;
        float middleY;
        float centerX = contentWidth / 2;
        float centerY = contentHeight / 2;


        mPaint.setColor(Color.parseColor("#4682B4"));
        mPaint.setStrokeWidth(40.0f);


        //float mShowDirection = (float) Math.toRadians(theta);
        float sin = (float) Math.sin(mShowDirection);
        float cos = (float) Math.cos(mShowDirection);

        middleX = centerX - time * 20 * sin;
        middleY = centerY - time * 20 * cos;

        float theta1 = mShowDirection + (float) Math.toRadians(120);
        float theta2 = mShowDirection - (float) Math.toRadians(120);

        int nMax = (int) Math.abs(centerX / (100*sin));
        for(int i = -nMax - 3;i <= nMax + 3;i++){
            float x = middleX + 100*sin*i;
            float y = middleY + 100*cos*i;
            float[] stop1 = calcStop(x,y,theta1);
            float[] stop2 = calcStop(x,y,theta2);
            canvas.drawLine(x,y,stop1[0],stop1[1],mPaint);
            canvas.drawLine(x,y,stop2[0],stop2[1],mPaint);
            canvas.drawPoint(x,y,mPaint);
        }

        int distance = (int) mDistance;
        String showDist;
        if(distance >= 1000){
            showDist = String.format("%.1f"+DISP_KM,mDistance / 1000);
        }else{
            showDist = distance + DISP_M;
        }
        String showStr = DISP_LEST + showDist;
        mTextPaint.setTextSize(96f);
        // Draw the text.
        canvas.drawText(showStr,
                paddingLeft + contentWidth / 20,
                paddingTop + contentHeight / 8,
                mTextPaint);

        // Draw the example drawable on top of the text.
        if (mExampleDrawable != null) {
            mExampleDrawable.setBounds(paddingLeft, paddingTop,
                    paddingLeft + contentWidth, paddingTop + contentHeight);
            mExampleDrawable.draw(canvas);
        }
    }

    /**
     * Gets the example string attribute value.
     *
     * @return The example string attribute value.
     */
    public String getExampleString() {
        return mExampleString;
    }

    /**
     * Sets the view's example string attribute value. In the example view, this string
     * is the text to draw.
     *
     * @param exampleString The example string attribute value to use.
     */
    public void setExampleString(String exampleString) {
        mExampleString = exampleString;
        invalidateTextPaintAndMeasurements();
    }

    /**
     * Gets the example color attribute value.
     *
     * @return The example color attribute value.
     */
    public int getExampleColor() {
        return mExampleColor;
    }

    /**
     * Sets the view's example color attribute value. In the example view, this color
     * is the font color.
     *
     * @param exampleColor The example color attribute value to use.
     */
    public void setExampleColor(int exampleColor) {
        mExampleColor = exampleColor;
        invalidateTextPaintAndMeasurements();
    }

    /**
     * Gets the example dimension attribute value.
     *
     * @return The example dimension attribute value.
     */
    public float getExampleDimension() {
        return mExampleDimension;
    }

    /**
     * Sets the view's example dimension attribute value. In the example view, this dimension
     * is the font size.
     *
     * @param exampleDimension The example dimension attribute value to use.
     */
    public void setExampleDimension(float exampleDimension) {
        mExampleDimension = exampleDimension;
        invalidateTextPaintAndMeasurements();
    }

    /**
     * Gets the example drawable attribute value.
     *
     * @return The example drawable attribute value.
     */
    public Drawable getExampleDrawable() {
        return mExampleDrawable;
    }

    /**
     * Sets the view's example drawable attribute value. In the example view, this drawable is
     * drawn above the text.
     *
     * @param exampleDrawable The example drawable attribute value to use.
     */
    public void setExampleDrawable(Drawable exampleDrawable) {
        mExampleDrawable = exampleDrawable;
    }
}
