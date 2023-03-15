package api.pot.sound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WaveformView extends ImageView {
    public static final int FIRST_GES_GRADIENT_COLOR = Color.parseColor("#d105c6");
    public static final int SECOND_GES_GRADIENT_COLOR = Color.parseColor("#ff0059");
    public static final int THIRD_GES_GRADIENT_COLOR = Color.parseColor("#ede100");
    public static final int MAIN_GES_COLOR = Color.parseColor("#4b0082");

    // The number of buffer frames to keep around (for a nice fade-out visualization).
    private static final int HISTORY_SIZE = 6;

    // To make quieter sounds still show up well on the display, we use +/- 8192 as the amplitude
    // that reaches the top/bottom of the view instead of +/- 32767. Any samples that have
    // magnitude higher than this limit will simply be clipped during drawing.
    private static final float MAX_AMPLITUDE_TO_DRAW = 8192.0f;//correct: 8192.0f

    // The queue that will hold historical audio data.
    private final LinkedList<short[]> mAudioData;

    private List<Short> mSuperMax = new ArrayList<>();
    private int cmpSuperMAx = 0;
    private long lastTime = 0;

    private final Paint mPaint;

    public WaveformView(Context context) {
        this(context, null, 0);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mAudioData = new LinkedList<short[]>();

        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(0);
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas cvs) {
        super.onDraw(cvs);
        try {
            drawRadialWaveform(cvs);
        }catch (Exception e){}
    }

    /**
     * Updates the waveform view with a new "frame" of samples and renders it. The new frame gets
     * added to the front of the rendering queue, pushing the previous frames back, causing them to
     * be faded out visually.
     *
     * @param buffer the most recent buffer of audio samples
     */
    public synchronized void updateAudioData(short[] buffer) {
        short[] newBuffer;

        // We want to keep a small amount of history in the view to provide a nice fading effect.
        // We use a linked list that we treat as a queue for this.
        if (mAudioData.size() == HISTORY_SIZE) {
            newBuffer = mAudioData.removeFirst();
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        } else {
            newBuffer = buffer.clone();
        }

        mAudioData.addLast(newBuffer);

        post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
        // Update the display.
        /*Canvas canvas = getHolder().lockCanvas();
        if (canvas != null) {
            //drawWaveform(canvas);
            //***********
            drawRadialWaveform(canvas);
            //#############
            getHolder().unlockCanvasAndPost(canvas);
        }*/
    }

    public void setUse(boolean use) {
        this.use = use;
    }
    private boolean use = false;

    public synchronized void updateAudioData(byte[] byteBuffer) {
        int size = byteBuffer.length;
        short[] shortBuffer = new short[size/2];
        short max = (short) MAX_AMPLITUDE_TO_DRAW;

        if(use){
            for (int i = 0; i < shortBuffer.length; i++) {
                int x = i;
                int t = 0;
                if (x < 1024) {
                    t = (int) (((byte) (Math.abs(byteBuffer[x]) + 128)) * max / 128);
                }
                shortBuffer[i] = (short) -t;
            }
        }else {
            short s;
            for (int i=0; i<shortBuffer.length; i++) {
                s = getShort(byteBuffer[i*2], byteBuffer[i*2+1]);
                shortBuffer[i] = s>max?max:s;
            }
        }

        updateAudioData(shortBuffer);
    }

    private short getShort(byte argB1, byte argB2) {
        return (short)(argB1 | (argB2 << 8));
    }

    private class Spot{
        public float x, y;

        public Spot(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public Spot(double x, double y) {
            this.x = (float) x;
            this.y = (float) y;
        }
    }

    private void drawRadialWaveform(Canvas canvas) {
        // Clear the screen each time because SurfaceView won't do this for us.
        //canvas.drawColor(Color.BLACK);

        short max_amplitude_to_draw = (short) MAX_AMPLITUDE_TO_DRAW;

        float maxRadial = Math.min(getWidth(), getHeight())/2,
                minRadial = maxRadial/2, radial = minRadial, crownWidth = radial/10;
        RectF bounds = new RectF(getWidth()/2-maxRadial, getHeight()/2-maxRadial,
                getWidth()/2+maxRadial, getHeight()/2+maxRadial);

        Paint paint = new Paint();
        RadialGradient radialGradient = new RadialGradient(bounds.centerX(), bounds.centerY(), maxRadial,
                new int[]{MAIN_GES_COLOR, MAIN_GES_COLOR, MAIN_GES_COLOR,
                        FIRST_GES_GRADIENT_COLOR, SECOND_GES_GRADIENT_COLOR, THIRD_GES_GRADIENT_COLOR}, null, Shader.TileMode.CLAMP);
        paint.setShader(radialGradient);
        paint.setAlpha(255);

        float amplitudeWidth = crownWidth/4;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(amplitudeWidth);

        short superMax = 0;

        long avg = 0;
        int avg_cmp = 0;
        long max = 0;
        float  alpha = 0;
        int cmp = 0, nomber = (int) (((2*minRadial*Math.PI)/(amplitudeWidth*2)));
        //nomber = nomber>mAudioData.get(mAudioData.size()-1).length?mAudioData.get(mAudioData.size()-1).length:nomber;
        //Log.d("http://pot.com", nomber+"/"+mAudioData.get(mAudioData.size()-1).length);
        int delta = /*1*/mAudioData.get(mAudioData.size()-1).length/nomber, startAngle = -180, index = 0;
        Spot center = new Spot(bounds.centerX(), bounds.centerY()), minSpot, currentSpot;
        for(short data : mAudioData.get(mAudioData.size()-1)){
            /*if(data>max) max = data<MAX_AMPLITUDE_TO_DRAW?data:(short)MAX_AMPLITUDE_TO_DRAW;
            if(data>max) max = max<0?0:max;*/
            data = (short) Math.abs(data);
            avg += data;
            avg_cmp++;
            //if(data>superMax && data<MAX_AMPLITUDE_TO_DRAW) superMax = data<MAX_AMPLITUDE_TO_DRAW?data:(short)MAX_AMPLITUDE_TO_DRAW;

            if(index%delta==0) {
                max = (avg/avg_cmp);
                max = max>max_amplitude_to_draw?max_amplitude_to_draw:max;
                if(max>superMax) superMax = max<max_amplitude_to_draw? (short) max : max_amplitude_to_draw;

                minSpot = new Spot(center.x + (radial * Math.cos(Math.toRadians(startAngle+alpha))), center.y + (radial * Math.sin(Math.toRadians(startAngle+alpha))));
                currentSpot = new Spot(center.x + ((radial + radial * (Float.valueOf(max) / max_amplitude_to_draw)) * Math.cos(Math.toRadians(startAngle+alpha))),
                        center.y + ((radial + radial * (Float.valueOf(max) / max_amplitude_to_draw)) * Math.sin(Math.toRadians(startAngle+alpha))));

                Path path = new Path();
                path.moveTo(minSpot.x, minSpot.y);
                path.lineTo(currentSpot.x, currentSpot.y);

                canvas.drawPath(path, paint);

                alpha+=360/Float.valueOf(nomber);
                max = 0;

                avg = 0;
                avg_cmp = 0;
            }

            index++;//index+=delta;//

            if(alpha>=360) break;//doit etre present pour eviter le mauvais, cause de la duplication des donnée sur le spectre
        }
        Log.d("ges_sonnore", "size: "+index);

        int current = 0;
        Paint nowPaint = new Paint();
        if(lastTime==0 || System.currentTimeMillis()-lastTime>=0) {
            if (mSuperMax.size() < nomber) {
                current = mSuperMax.size();
                mSuperMax.add(superMax);
            }else {
                current = cmpSuperMAx % mSuperMax.size();
                mSuperMax.set(cmpSuperMAx % mSuperMax.size(), superMax);
                cmpSuperMAx++;
                /*mSuperMax = mSuperMax.subList(0, mSuperMax.size()-1);
                mSuperMax.set(mSuperMax.size()-1, superMax);*/
            }

            /*int[] colors = {Color.BLACK, Color.RED, Color.BLACK};
            float[] positions = {0.0f, 1.0f*current/nomber, 1.0f};*/
            int[] colors = {Color.TRANSPARENT, Color.TRANSPARENT, Color.RED};
            float[] positions = {0.0f, 0.1f, 1.0f};
            SweepGradient sweepGradient = new SweepGradient(center.x, center.y, colors, positions);
            Matrix matrix = new Matrix();
            matrix.setRotate(current*360f/nomber+180, center.x, center.y);
            sweepGradient.setLocalMatrix(matrix);
            nowPaint.setShader(sweepGradient);
            nowPaint.setStyle(Paint.Style.STROKE);
            nowPaint.setStrokeWidth(amplitudeWidth);

            lastTime = System.currentTimeMillis();
        }

        alpha = 0;
        index = 0;
        //inverse(mSuperMax);
        for(short data : mSuperMax){
            minSpot = new Spot(center.x + (radial * Math.cos(Math.toRadians(startAngle+alpha))), center.y + (radial * Math.sin(Math.toRadians(startAngle+alpha))));
            currentSpot = new Spot(center.x + ((radial - (radial/2) * (Float.valueOf(data) / MAX_AMPLITUDE_TO_DRAW)) * Math.cos(Math.toRadians(startAngle+alpha))),
                    center.y + ((radial - (radial/2) * (Float.valueOf(data) / MAX_AMPLITUDE_TO_DRAW)) * Math.sin(Math.toRadians(startAngle+alpha))));

            Path path = new Path();
            path.moveTo(minSpot.x, minSpot.y);
            path.lineTo(currentSpot.x, currentSpot.y);

            canvas.drawPath(path, nowPaint);

            index++;
            alpha+=360f/nomber;
        }

        //Log.d("ges_son", "max ampli: "+max);
        //paint.setShader(null);
        //paint.setStrokeWidth(crownWidth);
        //paint.setColor(MAIN_GES_COLOR);
        //paint.setAlpha((int) (255*(Float.valueOf(max)/MAX_AMPLITUDE_TO_DRAW)));
        ///canvas.drawCircle(bounds.centerX(), bounds.centerY(), minRadial, paint);
        //canvas.drawCircle(bounds.centerX(), bounds.centerY(), maxRadial, paint);
    }

    private void drawWaveform(Canvas canvas) {
        // Clear the screen each time because SurfaceView won't do this for us.
        canvas.drawColor(Color.BLACK);

        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;

        // We draw the history from oldest to newest so that the older audio data is further back
        // and darker than the most recent data.
        int colorDelta = 255 / (HISTORY_SIZE + 1);
        int brightness = colorDelta;

        for (short[] buffer : mAudioData) {
            mPaint.setColor(Color.argb(brightness, 128, 255, 192));

            float lastX = -1;
            float lastY = -1;

            // For efficiency, we don't draw all of the samples in the buffer, but only the ones
            // that align with pixel boundaries.
            for (int x = 0; x < width; x++) {
                int index = (int) ((x / width) * buffer.length);
                short sample = buffer[index];
                float y = (sample / MAX_AMPLITUDE_TO_DRAW) * centerY + centerY;

                if (lastX != -1) {
                    canvas.drawLine(lastX, lastY, x, y, mPaint);
                }

                lastX = x;
                lastY = y;
            }

            brightness += colorDelta;
        }
    }

    /*private void drawWaveform(Canvas canvas) {
        // Clear the screen each time because SurfaceView won't do this for us.
        canvas.drawColor(Color.BLACK);

        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;

        // We draw the history from oldest to newest so that the older audio data is further back
        // and darker than the most recent data.
        int colorDelta = 255 / (HISTORY_SIZE + 1);
        int brightness = colorDelta;

        for (short[] buffer : mAudioData) {
            mPaint.setColor(Color.argb(brightness, 128, 255, 192));

            float lastX = -1;
            float lastY = -1;

            // For efficiency, we don't draw all of the samples in the buffer, but only the ones
            // that align with pixel boundaries.
            for (int x = 0; x < width; x++) {
                int index = (int) ((x / width) * buffer.length);
                short sample = buffer[index];
                float y = (sample / MAX_AMPLITUDE_TO_DRAW) * centerY + centerY;

                if (lastX != -1) {
                    canvas.drawLine(lastX, lastY, x, y, mPaint);
                }

                lastX = x;
                lastY = y;
            }

            brightness += colorDelta;
        }
    }*/
}
