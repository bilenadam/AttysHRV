package tech.glasgowneuro.attyshrv;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;



/**
 * Created by Bernd Porr on 20/01/17.
 * Heartrate Plot
 */

public class HeartratePlotFragment extends Fragment {

    String TAG = "HeartratePlotFragment";

    private static final int HISTORY_SIZE = 60;

    private SimpleXYSeries bpmHistorySeries = null;
    private SimpleXYSeries bpmFullSeries = null;

    private XYPlot bpmPlot = null;

    private TextView statsText = null;

    private Button bpmResetButton = null;

    private ToggleButton bpmAutoscaleButton = null;

    View view = null;

    boolean showStats = true;
    boolean autoscale = false;

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        Log.d(TAG, "onCreate, creating Fragment");

        if (container == null) {
            return null;
        }

        setHasOptionsMenu(true);

        view = inflater.inflate(R.layout.heartrateplotfragment, container, false);

        // setup the APR Levels plot:
        bpmPlot = (XYPlot) view.findViewById(R.id.bpmPlotView);
        statsText = (TextView) view.findViewById(R.id.statsTextView);

        bpmHistorySeries = new SimpleXYSeries("Heart rate / beats per minute");
        bpmHistorySeries.useImplicitXVals();
        bpmFullSeries = new SimpleXYSeries("Heart rate / beats per minute");
        bpmFullSeries.useImplicitXVals();

        bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
        bpmPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        bpmPlot.addSeries(bpmHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        bpmPlot.setDomainLabel("Heartbeat number");
        bpmPlot.setRangeLabel(null);

        Screensize screensize = new Screensize(getActivity().getWindowManager());

        if (screensize.isTablet()) {
            bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 25);
        } else {
            bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 50);
        }

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        bpmPlot.getGraph().setDomainGridLinePaint(paint);
        bpmPlot.getGraph().setRangeGridLinePaint(paint);

        return view;

    }


    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.hrvplotoptions, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.hrplotfragmentreset:
                reset();
                return true;

            case R.id.hrplotfragmentstats:
                showStats = !showStats;
                item.setChecked(showStats);
                return true;

            case R.id.hrplotfragmentautoscale:
                autoscale = !autoscale;
                item.setChecked(autoscale);
                Screensize screensize = new Screensize(getActivity().getWindowManager());
                if (autoscale) {
                    if (screensize.isTablet()) {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 5);
                    } else {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 20);
                    }
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.AUTO);
                } else {
                    if (screensize.isTablet()) {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 25);
                    } else {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 50);
                    }
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
                }
                bpmPlot.redraw();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void reset() {
        int n = bpmHistorySeries.size();
        for (int i = 0; i < n; i++) {
            bpmHistorySeries.removeLast();
        }
        bpmFullSeries = new SimpleXYSeries("");
        bpmPlot.redraw();
    }

    public synchronized void addValue(final float v) {

        if (bpmFullSeries == null) return;

        float sum = 0;
        if (showStats) {
            for (int i = 0; i < bpmFullSeries.size(); i++) {
                sum = sum + bpmFullSeries.getY(i).floatValue();
            }
            float avg = sum / (float) (bpmFullSeries.size());
            double dev = 0;
            for (int i = 0; i < bpmFullSeries.size(); i++) {
                dev = dev + Math.pow(bpmFullSeries.getY(i).floatValue() - avg, 2);
            }
            dev = dev / (bpmFullSeries.size() - 1);
            double rms = 0;
            for (int i = 0; i < bpmFullSeries.size() - 1; i++) {
                rms = rms + Math.pow((bpmFullSeries.getY(i).floatValue() -
                        bpmFullSeries.getY(i + 1).floatValue()), 2);
            }
            rms = rms / bpmFullSeries.size();
            final String statsString = String.format("avg = %3.02f, sd = %3.02f, rmssd = %3.02f",
                    avg, dev, rms);
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (statsText != null) {
                            statsText.setEnabled(true);
                            statsText.setText(statsString);
                        }
                    }
                });
            }
        } else {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (statsText != null) {
                            statsText.setEnabled(false);
                            statsText.setText("");
                        }
                    }
                });
            }
        }

        if (bpmHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "bpmHistorySeries == null");
            }
            return;
        }
        if (bpmFullSeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "bpmFullSeries == null");
            }
            return;
        }
        // get rid the oldest sample in history:
        if (bpmHistorySeries.size() > HISTORY_SIZE) {
            bpmHistorySeries.removeFirst();
        }

        // add the latest history sample:
        bpmHistorySeries.addLast(null, v);
        bpmFullSeries.addLast(null, v);
        bpmPlot.redraw();

    }
}
