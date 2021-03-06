/**
 * Copyright 2016-2017 Bernd Porr, mail@berndporr.me.uk
 * Copyright 2016-2017 Paul Miller, nlholdem@hotmail.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package tech.glasgowneuro.attyshrv;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import uk.me.berndporr.iirj.Butterworth;

import static tech.glasgowneuro.attyshrv.AttysHRV.DataRecorder.DATA_SEPARATOR_COMMA;
import static tech.glasgowneuro.attyshrv.AttysHRV.DataRecorder.DATA_SEPARATOR_SPACE;
import static tech.glasgowneuro.attyshrv.AttysHRV.DataRecorder.DATA_SEPARATOR_TAB;

public class AttysHRV extends AppCompatActivity {

    private Timer timer = null;
    // screen refresh rate
    private final int REFRESH_IN_MS = 50;

    private RealtimePlotView realtimePlotView = null;
    private HRVView hrvView = null;
    private HeartratePlotFragment heartratePlotFragment = null;

    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;

    UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysHRV";

    private Highpass highpass_II = null;
    private Highpass highpass_III = null;
    private float gain = 500;
    private Butterworth iirNotch_II = null;
    private Butterworth iirNotch_III = null;
    private double notchBW = 2.5; // Hz
    private int notchOrder = 2;
    private float powerlineHz = 50;

    private boolean showECG = true;
    private boolean showHRVanimation = true;
    private float filtBPM = 0;

    private ECG_rr_det ecg_rr_det_ch1 = null;
    private ECG_rr_det ecg_rr_det_ch2 = null;

    private float ytick = 0;

    int ygapForInfo = 0;

    // debugging the ECG detector, commented out for production
    //double ecgDetOut;

    private int timestamp = 0;

    String[] labels = {
            "I", "II", "III",
            "aVR", "aVL", "aVF"};

    private String dataFilename = null;
    private byte dataSeparator = 0;

    private final String ATTYS_SUBDIR = "attys";
    private File attysdir = null;

    ProgressBar progress = null;


    public class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        public final static byte DATA_SEPARATOR_TAB = 0;
        public final static byte DATA_SEPARATOR_COMMA = 1;
        public final static byte DATA_SEPARATOR_SPACE = 2;

        private PrintWriter textdataFileStream = null;
        private File textdataFile = null;
        private byte data_separator = DATA_SEPARATOR_TAB;
        float samplingInterval = 0;
        float bpm = 0;

        // starts the recording
        public java.io.FileNotFoundException startRec(File file) {
            samplingInterval = 1.0F / attysComm.getSamplingRateInHz();
            try {
                textdataFileStream = new PrintWriter(file);
                textdataFile = file;
                messageListener.haveMessage(AttysComm.MESSAGE_STARTED_RECORDING);
            } catch (java.io.FileNotFoundException e) {
                textdataFileStream = null;
                textdataFile = null;
                return e;
            }
            return null;
        }

        // stops it
        public void stopRec() {
            if (textdataFileStream != null) {
                textdataFileStream.close();
                messageListener.haveMessage(AttysComm.MESSAGE_STOPPED_RECORDING);
                textdataFileStream = null;
                textdataFile = null;
            }
        }

        // are we recording?
        public boolean isRecording() {
            return (textdataFileStream != null);
        }

        public File getFile() {
            return textdataFile;
        }

        public void setDataSeparator(byte s) {
            data_separator = s;
        }

        public byte getDataSeparator() {
            return data_separator;
        }

        public void setBPM(float _bpm) {
            bpm = _bpm;
        }

        private void saveData(float I, float II, float III,
                              float aVR, float aVL, float aVF) {

            if (textdataFileStream == null) return;

            char s = ' ';
            switch (data_separator) {
                case DATA_SEPARATOR_SPACE:
                    s = ' ';
                    break;
                case DATA_SEPARATOR_COMMA:
                    s = ',';
                    break;
                case DATA_SEPARATOR_TAB:
                    s = 9;
                    break;
            }
            float t = timestamp + samplingInterval;
            String tmp = String.format(Locale.US,"%f%c", t, s);
            tmp = tmp + String.format(Locale.US,"%f%c", I, s);
            tmp = tmp + String.format(Locale.US,"%f%c", II, s);
            tmp = tmp + String.format(Locale.US,"%f%c", III, s);
            tmp = tmp + String.format(Locale.US,"%f%c", aVR, s);
            tmp = tmp + String.format(Locale.US,"%f%c", aVL, s);
            tmp = tmp + String.format(Locale.US,"%f%c", aVF, s);
            tmp = tmp + String.format(Locale.US,"%f", bpm);
            bpm = 0;

            if (textdataFileStream != null) {
                textdataFileStream.format("%s\n", tmp);
            }
        }
    }

    DataRecorder dataRecorder = new DataRecorder();

    AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
        @Override
        public void haveMessage(final int msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (msg) {
                        case AttysComm.MESSAGE_ERROR:
                            Toast.makeText(getApplicationContext(),
                                    "Bluetooth connection problem", Toast.LENGTH_SHORT).show();
                            if (attysComm != null) {
                                attysComm.stop();
                            }
                            progress.setVisibility(View.GONE);
                            finish();
                            break;
                        case AttysComm.MESSAGE_CONNECTED:
                            progress.setVisibility(View.GONE);
                            break;
                        case AttysComm.MESSAGE_CONFIGURE:
                            Toast.makeText(getApplicationContext(),
                                    "Configuring Attys", Toast.LENGTH_SHORT).show();
                            progress.setEnabled(false);
                            break;
                        case AttysComm.MESSAGE_RETRY:
                            Toast.makeText(getApplicationContext(),
                                    "Bluetooth - trying to connect. Please be patient.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_STARTED_RECORDING:
                            Toast.makeText(getApplicationContext(),
                                    "Started recording data to external storage.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_STOPPED_RECORDING:
                            Toast.makeText(getApplicationContext(),
                                    "Finished recording data to external storage.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_CONNECTING:
                            progress.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    };


    private class UpdatePlotTask extends TimerTask {

        public synchronized void run() {

            if (attysComm != null) {
                if (attysComm.hasFatalError()) {
                    // Log.d(TAG,String.format("No bluetooth connection"));
                    return;
                }
            }
            if (attysComm != null) {
                if (!attysComm.hasActiveConnection()) return;
            }

            int nCh = 0;
            if (attysComm != null) nCh = AttysComm.NCHANNELS;
            if (attysComm != null) {
                float[] tmpSample = new float[nCh];
                float[] tmpMin = new float[nCh];
                float[] tmpMax = new float[nCh];
                float[] tmpTick = new float[nCh];
                String[] tmpLabels = new String[nCh];

                float max = attysComm.getADCFullScaleRange(0) / gain;
                ytick = 1.0F / gain / 10;

                int n = 0;
                if (attysComm != null) {
                    n = attysComm.getNumSamplesAvilable();
                }
                if (realtimePlotView != null) {
                    if (!realtimePlotView.startAddSamples(n)) return;
                    for (int i = 0; ((i < n) && (attysComm != null)); i++) {
                        float[] sample = null;
                        if (attysComm != null) {
                            sample = attysComm.getSampleFromBuffer();
                        }
                        if (sample != null) {
                            // debug ECG detector
                            // sample[AttysComm.INDEX_Analogue_channel_2] = (float)ecgDetOut;
                            timestamp++;

                            float II = sample[AttysComm.INDEX_Analogue_channel_1];
                            II = highpass_II.filter(II);
                            if (iirNotch_II != null) {
                                II = (float) iirNotch_II.filter((double) II);
                            }

                            if (ecg_rr_det_ch1 != null) {
                                ecg_rr_det_ch1.detect(II);
                            }

                            float III = sample[AttysComm.INDEX_Analogue_channel_2];
                            III = highpass_III.filter(III);
                            if (iirNotch_III != null) {
                                III = (float) iirNotch_III.filter((double) III);
                            }

                            if (ecg_rr_det_ch2 != null) {
                                ecg_rr_det_ch2.detect(III);
                            }

                            // https://pdfs.semanticscholar.org/8160/8b62b6efb007d112b438655dd2c897759fb1.pdf
                            // Corrected Formula for the Calculation of the Electrical Heart Axis
                            // Dragutin Novosel, Georg Noll1, Thomas F. Lüscher1

                            // I-II+III = 0
                            float I = II - III;

                            float aVR = III / 2 - II;
                            float aVL = II / 2 - III;
                            float aVF = II / 2 + III / 2;

                            dataRecorder.saveData(I, II, III, aVR, aVL, aVF);

                            int nRealChN = 0;
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[0];
                                tmpSample[nRealChN++] = I;
                            }
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[1];
                                tmpSample[nRealChN++] = II;
                            }
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[2];
                                tmpSample[nRealChN++] = III;
                            }
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[3];
                                tmpSample[nRealChN++] = aVR;
                            }
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[4];
                                tmpSample[nRealChN++] = aVL;
                            }
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[5];
                                tmpSample[nRealChN++] = aVF;
                            }

                            if (realtimePlotView != null) {
                                realtimePlotView.addSamples(Arrays.copyOfRange(tmpSample, 0, nRealChN),
                                        Arrays.copyOfRange(tmpMin, 0, nRealChN),
                                        Arrays.copyOfRange(tmpMax, 0, nRealChN),
                                        Arrays.copyOfRange(tmpTick, 0, nRealChN),
                                        Arrays.copyOfRange(tmpLabels, 0, nRealChN),
                                        ygapForInfo);
                            }
                        }
                    }
                    if (realtimePlotView != null) {
                        realtimePlotView.stopAddSamples();
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ((attysComm != null) && (hrvView != null)) {
                                hrvView.setHeartRate(filtBPM, attysComm.getSamplingRateInHz() / 2);
                            }
                        }
                    });
                }
            }
        }
    }


    @Override
    public void onBackPressed() {
/*        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Back button pressed");
        }*/
        killAttysComm();
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startActivity(startMain);
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        attysdir = new File(Environment.getExternalStorageDirectory().getPath(),
                ATTYS_SUBDIR);
        if (!attysdir.exists()) {
            attysdir.mkdirs();
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.main_activity_layout);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        progress = findViewById(R.id.indeterminateBar);

        highpass_II = new Highpass();
        highpass_III = new Highpass();
        iirNotch_II = null;
        iirNotch_III = null;
        gain = 500;

    }

    // this is called whenever the app is starting or re-starting
    @Override
    public void onStart() {
        super.onStart();

        startDAQ();
        setDefaultUIState();

    }

    private void setDefaultUIState() {
        hidePlotFragment();
        deletePlotWindow();
    }

    private void saveBPM(float bpm) {
        dataRecorder.setBPM(bpm);
        if (heartratePlotFragment != null) {
            heartratePlotFragment.addValue(bpm);
        }
        filtBPM = bpm;
    }


    public void startDAQ() {

        btAttysDevice = AttysComm.findAttysBtDevice();
        if (btAttysDevice == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No Attys Found")
                    .setMessage("Visit www.attys.tech for help.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String url = "http://www.attys.tech";
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(url));
                            startActivity(i);
                        }
                    })
                    .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            finish();
                        }
                    })
                    .show();
        }

        attysComm = new AttysComm(btAttysDevice);
        attysComm.registerMessageListener(messageListener);

        getsetAttysPrefs();

        highpass_II.setAlpha(1.0F / attysComm.getSamplingRateInHz());
        highpass_III.setAlpha(1.0F / attysComm.getSamplingRateInHz());

        iirNotch_II = new Butterworth();
        iirNotch_III = new Butterworth();
        iirNotch_II.bandStop(notchOrder,
                attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
        iirNotch_III.bandStop(notchOrder,
                attysComm.getSamplingRateInHz(), powerlineHz, notchBW);

        realtimePlotView = findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);
        realtimePlotView.init();

        realtimePlotView.registerTouchEventListener(
                new RealtimePlotView.TouchEventListener() {
                    @Override
                    public void touchedChannel(int chNo) {
                        try {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    hrvView.reset();
                                }
                            });
                        } catch (Exception e) {
                            if (Log.isLoggable(TAG, Log.ERROR)) {
                                Log.e(TAG, "Exception in the TouchEventListener (BUG!):", e);
                            }
                        }
                    }
                });


        hrvView = findViewById(R.id.hrvview);

        attysComm.start();

        ecg_rr_det_ch1 = new ECG_rr_det(attysComm.getSamplingRateInHz(), powerlineHz);

        ecg_rr_det_ch1.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber, float bpm, float bpmUnfilt, double amplitude, double confidence) {
                if (ecg_rr_det_ch1.getAmplitude() > ecg_rr_det_ch2.getAmplitude()) {
                    saveBPM(bpm);
                }
            }
        });

        ecg_rr_det_ch2 = new ECG_rr_det(attysComm.getSamplingRateInHz(), powerlineHz);

        ecg_rr_det_ch2.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber, float bpm, float bpmUnfilt, double amplitude, double confidence) {
                if (ecg_rr_det_ch2.getAmplitude() > ecg_rr_det_ch1.getAmplitude()) {
                    saveBPM(bpm);
                }
            }
        });

        timer = new Timer();
        updatePlotTask = new UpdatePlotTask();
        timer.schedule(updatePlotTask, 0, REFRESH_IN_MS);
    }

    private void killAttysComm() {

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
/*            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed timer");
            }*/
        }

        if (updatePlotTask != null) {
            updatePlotTask.cancel();
            updatePlotTask = null;
/*            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed update Plot Task");
            }*/
        }

        if (attysComm != null) {
            attysComm.stop();
            attysComm = null;
/*            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed AttysComm");
            }*/
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
/*
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }*/
        killAttysComm();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
/*
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Restarting");
       }*/
        killAttysComm();
    }


    @Override
    public void onPause() {
        super.onPause();
/*
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Paused");
        }*/

    }


    @Override
    public void onStop() {
        super.onStop();
/*
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped");
        }*/

        killAttysComm();
    }


    private void enterFilename() {

        final EditText filenameEditText = new EditText(this);
        filenameEditText.setSingleLine(true);

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(this)
                .setTitle("Enter filename")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
                        if (!dataFilename.contains(".")) {
                            switch (dataSeparator) {
                                case DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case DATA_SEPARATOR_TAB:
                                    dataFilename = dataFilename + ".tsv";
                            }
                        }
                        Toast.makeText(getApplicationContext(),
                                "Press rec to record to '" + dataFilename + "'",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }


    private void shareData() {

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        final List files = new ArrayList();
        if (attysdir == null) return;
        final String[] list = attysdir.list();
        if (list != null) {
            for (String file : list) {
                if (files != null) {
                    if (file != null) {
                        files.add(file);
                    }
                }
            }
        }

        final ListView listview = new ListView(this);
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_multiple_choice,
                files);
        listview.setAdapter(adapter);
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.setSelected(true);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Share")
                .setMessage("Select filename(s)")
                .setView(listview)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SparseBooleanArray checked = listview.getCheckedItemPositions();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        ArrayList<Uri> files = new ArrayList<>();
                        for (int i = 0; i < listview.getCount(); i++) {
                            if (checked.get(i)) {
                                String filename = list[i];
                                File fp = new File(attysdir, filename);
                                files.add(Uri.fromFile(fp));
/*                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "filename=" + filename);
                                }*/
                            }
                        }
                        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                        sendIntent.setType("text/*");
                        startActivity(Intent.createChooser(sendIntent, "Send your files"));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();

        ViewGroup.LayoutParams layoutParams = listview.getLayoutParams();
        Screensize screensize = new Screensize(getWindowManager());
        layoutParams.height = screensize.getHeightInPixels() / 2;
        listview.setLayoutParams(layoutParams);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attyshrv, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.preferences:
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                return true;

            case R.id.toggleRec:
                if (dataRecorder.isRecording()) {
                    File file = dataRecorder.getFile();
                    dataRecorder.stopRec();
                    if (file != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        Uri contentUri = Uri.fromFile(file);
                        mediaScanIntent.setData(contentUri);
                        sendBroadcast(mediaScanIntent);
                    }
                } else {
                    if (dataFilename != null) {
                        File file = new File(attysdir, dataFilename.trim());
                        dataRecorder.setDataSeparator(dataSeparator);
                        if (file.exists()) {
                            Toast.makeText(getApplicationContext(),
                                    "File exists already. Enter a different one.",
                                    Toast.LENGTH_LONG).show();
                            return true;
                        }
                        java.io.FileNotFoundException e = dataRecorder.startRec(file);
                        if (e != null) {
                            return true;
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "To record enter a filename first", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;

            case R.id.showECG:
                showECG = !showECG;
                item.setChecked(showECG);
                if (showECG) {
                    realtimePlotView.resetX();
                    realtimePlotView.setVisibility(View.VISIBLE);
                } else {
                    realtimePlotView.setVisibility(View.INVISIBLE);
                }
                return true;

            case R.id.showAnimation:
                showHRVanimation = !showHRVanimation;
                item.setChecked(showHRVanimation);
                if (showHRVanimation) {
                    hrvView.setVisibility(View.VISIBLE);
                } else {
                    hrvView.setVisibility(View.INVISIBLE);
                }

            case R.id.Ch1gain200:
                gain = 200;
                return true;

            case R.id.Ch1gain500:
                gain = 500;
                return true;

            case R.id.Ch1gain1000:
                gain = 1000;
                return true;

            case R.id.enterFilename:
                enterFilename();
                return true;

            case R.id.plotWindowBPM:
                if (heartratePlotFragment != null) {
                    hidePlotFragment();
                    deletePlotWindow();
                } else {
                    deletePlotWindow();
                    heartratePlotFragment = new HeartratePlotFragment();
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.fragment_plot_container, heartratePlotFragment, "heartratePlotFragment")
                            .commit();
                    showPlotFragment();
                }
                item.setChecked(heartratePlotFragment != null);
                return true;

            case R.id.filebrowser:
                shareData();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void showPlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        frameLayout = findViewById(R.id.fragment_plot_container);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.5f));

    }

    private void hidePlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));
    }


    private void deletePlotWindow() {
        if (heartratePlotFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(heartratePlotFragment).commit();
            heartratePlotFragment = null;
        }
    }


    private void getsetAttysPrefs() {
        byte mux;
/*
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Setting preferences");
        }*/
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mux = AttysComm.ADC_MUX_ECG_EINTHOVEN;
        attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_6);
        attysComm.setAdc0_mux_index(mux);
        attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_6);
        attysComm.setAdc1_mux_index(mux);

        byte data_separator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        dataRecorder.setDataSeparator(data_separator);

        powerlineHz = Float.parseFloat(prefs.getString("powerline", "50"));
/*        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }*/

        attysComm.setAdc_samplingrate_index(AttysComm.ADC_RATE_250HZ);
    }

}
