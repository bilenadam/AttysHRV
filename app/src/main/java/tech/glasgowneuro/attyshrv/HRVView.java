package tech.glasgowneuro.attyshrv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.ListIterator;


/**
 * Created by Paul Miller on 06/02/17.
 */

public class HRVView extends View {
    final String TAG = "HRVView";

    private final int MAXSAMPLES = 400, STROKEWIDTH = 2, INNERCIRCLEWIDTH = 40;
    private float heartRate = 60, maxHR = 0, minHR=0;
    private float txtSizeMult = (float) 1.4;
    private float prevHR = 0;
    private ArrayList<Float> HRVValues = null;
    private int[] ringsColours;
    private float[] ringsStops;
    private int alphas[];
    private float maxCircleRadius;
    private final float HRVDecayConst = 0.01F;

    private Paint paintClear = null;
    private Paint paintWhite = null;
    private Paint paintBlack = null;
    private Paint paintCircle = null;
    private Paint paintTxt = null;
    private Paint paintRings = null;

    private RadialGradient ringsShader;

    public HRVView(Context context){
        super(context);
        init();
    }

    public HRVView(Context context, AttributeSet attrs){
        super(context, attrs);
        init();
    }
    public HRVView(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs, defStyle);
        init();
    }

    private void init(){
        paintWhite = new Paint();
        paintWhite.setColor(Color.WHITE);
        paintBlack = new Paint();
        paintBlack.setColor(Color.BLACK);
        paintCircle = new Paint();
        paintClear = new Paint();
        paintClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        HRVValues = new ArrayList<>();
        paintTxt = new Paint();
        paintTxt.setColor(Color.argb(255, 255, 255, 0));
        paintRings = new Paint();
        HRVValues = new ArrayList<Float>();

        maxCircleRadius  = Math.min(getHeight(), getWidth()) / 2F;
        maxCircleRadius  = 1000F;
//        Log.d(TAG, "maxCircleRadius: " + maxCircleRadius + " Height: " + getHeight() + " Width: " + getWidth());

        ringsColours = new int[MAXSAMPLES];
        ringsStops = new float[MAXSAMPLES];
        alphas = new int[MAXSAMPLES];

        for (int i=0; i< MAXSAMPLES; i++){
//            ringsColours[i] = Color.argb(128, 255 * i/MAXSAMPLES, 255 * i/MAXSAMPLES, 255 * i/MAXSAMPLES);
            ringsStops[i] = i / (float)MAXSAMPLES;
            alphas[i] = (int)(220F-(220F*ringsStops[i]*ringsStops[i]));
//            alphas[i] = (int)(220F-(220F*Math.pow( (i / (((float)MAXSAMPLES - 1F))), 0.25)));
            HRVValues.add(heartRate);
//            Log.d(TAG, "Colour: " +  255 * i/MAXSAMPLES + " Stops: " + ringsStops[i] + " alpha: " + alphas[i]);

        }
//        ringsShader = new RadialGradient(1280, 1280, maxCircleRadius, ringsColours, ringsStops, Shader.TileMode.CLAMP);
    }

    public void reset() {
        maxHR = 0;
        minHR = 0;
        init();
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        ArrayList<Float> TempHRVValues = new ArrayList<Float>(HRVValues);

        ListIterator li = TempHRVValues.listIterator();

        canvas.drawPaint(paintClear);

        int txtDiv = 25;
        Rect bounds = new Rect();
        String HRVTxt = "";
        HRVTxt = HRVTxt + "".format("%d", (int)heartRate);
        int centreX = getWidth() / 2;
        int centreY = getHeight() / 2;
        paintTxt.setTextSize((float)INNERCIRCLEWIDTH * txtSizeMult);

        int i = MAXSAMPLES - 1;
        while (li.hasNext()){
            heartRate = (float) li.next();

            // todo: replace this with a lookup table calculated during init()
//            paintCircle.setColor(heartRateToColour(heartRate));


            ringsColours[i] = heartRateToColour(heartRate, i);



//            Log.d(TAG, "RadiusList = " + li.previous());
//            canvas.drawCircle(centreX, centreY, INNERCIRCLEWIDTH + i * STROKEWIDTH, paintClear);
//            canvas.drawCircle(centreX, centreY, INNERCIRCLEWIDTH + i * STROKEWIDTH, paintCircle);
            i--;

        }

        ringsShader = new RadialGradient(centreX, centreY, maxCircleRadius, ringsColours, ringsStops, Shader.TileMode.CLAMP);
        paintRings.setShader(ringsShader);
        canvas.drawCircle(centreX, centreY, maxCircleRadius, paintRings);
        canvas.drawText(HRVTxt, centreX - INNERCIRCLEWIDTH / 2, centreY + INNERCIRCLEWIDTH / 2, paintTxt);
//        Log.d(TAG, "Width: " + centreX + " Height: " + centreY);

        Log.d(TAG, "HeartRate:  " + heartRate + " Color: " + paintCircle.getColor() + " i: " + i);
    }


    private int heartRateToColour(float HR, int index){

        // normalise HR

        HR = 5F + 250F * (HR - minHR) / (maxHR - minHR);
        HR = Math.max(HR, 0F);
        HR = Math.min(HR, 255F);
        // lookup colour in colourmap
        //int alpha = (int)(200F * (float)(index + 1F - MAXSAMPLES) / (float)MAXSAMPLES);

        return Color.argb(alphas[index], (int) (HR /1.8), (int) (HR / 1.5), (int) (HR / 1.01));

    }

    public synchronized void setHeartRate(float rad, float samplingRate){
//        heartRate = (int) rad;
        // eliminates missing heartbeats and crazy jumps in the heartbeat
        if ((Math.abs(prevHR - rad)<70) && (Math.abs(prevHR-rad*2)>5)) {
            HRVValues.add(rad);

            if (HRVValues.size() > MAXSAMPLES) {
                HRVValues.remove(0);
            }

            if ((maxHR < 10) && (minHR<10)) {
                maxHR = rad+10;
                minHR = rad-10;
            } else {
                maxHR = Math.max(rad, maxHR);
                minHR = Math.min(rad, minHR);
                maxHR = maxHR - HRVDecayConst * maxHR / samplingRate;
                minHR = minHR + HRVDecayConst * minHR / samplingRate;
            }

            invalidate();
        }
        prevHR = rad;
        //Log.d(TAG, "minHR: " + minHR + " maxHR: " + maxHR);
    }


}
