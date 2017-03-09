package com.ianzieg.markovseq;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Looper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    final String LOG_TAG = "MarkovSeq";

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mMidiDeviceToollbarView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mMidiDeviceToollbarView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mVisible = true;
        mMidiDeviceToollbarView = findViewById(R.id.midi_device_toolbar);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        Context context = getApplicationContext();

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            // do MIDI stuff
            Toast.makeText(context, "We have MIDI", Toast.LENGTH_SHORT).show();
            MidiManager manager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
            MidiDeviceInfo[] deviceList = manager.getDevices();
            if (deviceList.length > 0) {
                Toast.makeText(context, "we have a device:"+deviceList[0].toString(), Toast.LENGTH_LONG ).show();
            } else {
                Toast.makeText(context, "No devices", Toast.LENGTH_SHORT).show();
            }
            populateMidiDeviceSpinner(deviceList);
        } else {
            Toast.makeText(context, "MIDI is not available", Toast.LENGTH_SHORT).show();
        }




        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //findViewById(R.id.openDeviceButton).setOnTouchListener(mDelayHideTouchListener);
    }

    protected MidiDevice _activeMidiInputDevice;
    protected MidiDevice _activeMidiOutputDevice;
    protected MidiInputPort _activeMidiInputPort;
    protected MidiOutputPort _activeMidiOutputPort;

    protected void closeMidiInputConnection() {
        if (_activeMidiInputPort != null) {
            try {
                _activeMidiInputPort.close();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to close midi port: "+ex);
            }
        }
        if (_activeMidiInputDevice != null) {
            try {
                _activeMidiInputDevice.close();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to close midi device: "+ex);
            }
        }
    }

    protected void openMidiInputConnection(final MidiPortAddress portAddress) {
        MidiManager manager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        MidiDeviceInfo[] deviceList = manager.getDevices();
        MidiDeviceInfo deviceInfo = deviceList[portAddress.deviceIndex];
        manager.openDevice(deviceInfo, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice device) {
                _activeMidiInputDevice = device;
                if (device == null) {
                    Log.e(LOG_TAG, "could not open device " + device);
                    _activeMidiInputPort = null;
                } else {
                    Log.i(LOG_TAG, "opened device: " + device);
                    _activeMidiInputPort = device.openInputPort(portAddress.portIndex);
                }
            }

        }, new Handler(Looper.getMainLooper()));
    }

    protected void closeMidiOutputConnection() {
        if (_activeMidiOutputPort != null) {
            try {
                _activeMidiOutputPort.close();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to close midi port: "+ex);
            }
        }
        if (_activeMidiOutputDevice != null) {
            try {
                _activeMidiOutputDevice.close();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to close midi device: "+ex);
            }
        }
    }

    protected void openMidiOutputConnection(final MidiPortAddress portAddress) {
        MidiManager manager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        MidiDeviceInfo[] deviceList = manager.getDevices();
        MidiDeviceInfo deviceInfo = deviceList[portAddress.deviceIndex];
        manager.openDevice(deviceInfo, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice device) {
                _activeMidiOutputDevice = device;
                if (device == null) {
                    Log.e(LOG_TAG, "could not open device " + device);
                    _activeMidiOutputPort = null;
                } else {
                    Log.i(LOG_TAG, "opened device: " + device);
                    _activeMidiOutputPort = device.openOutputPort(portAddress.portIndex);


                    _activeMidiOutputPort.connect(new MidiReceiver() {
                        @Override
                        public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
                            int status = -1;
                            int chan = -1;
                            int d0 = -1;
                            int d1 = -1;
                            if (count > 0) {
                                status = (msg[offset] & 0b11110000);
                                chan = (msg[offset] & 0b1111);
                            }
                            if (count > 1) {
                                d0 = msg[offset+1];
                            }
                            if (count > 2) {
                                d1 = msg[offset+2];
                            }
                            Log.i(LOG_TAG, "count: "+count+" status: "+status+" chan: "+chan + " d0: "+d0+" d1: "+d1);
                        }
                    });


                }
            }

        }, new Handler(Looper.getMainLooper()));
    }

    protected class MidiPortAddress {
        public int deviceIndex;
        public int portIndex;
        MidiPortAddress(int device, int port) {
            deviceIndex = device;
            portIndex = port;
        }
    }


    protected HashMap<Integer, MidiPortAddress> populateDeviceSpinner(MidiDeviceInfo[] deviceList, int portType, Spinner spinner) {

        ArrayList<String> portNames = new ArrayList<String>();
        final HashMap<Integer, MidiPortAddress> portMap = new HashMap<Integer, MidiPortAddress>();
        int mapIndex = 0;
        int deviceIndex = 0;
        for (MidiDeviceInfo deviceInfo : deviceList) {

            Bundle deviceProperties = deviceInfo.getProperties();
            String deviceName = (String) deviceProperties.get(MidiDeviceInfo.PROPERTY_NAME);

            int portIndex = 0;
            for (MidiDeviceInfo.PortInfo portInfo : deviceInfo.getPorts()) {
                if (portInfo.getType() == portType) {
                    portNames.add(deviceName + ": " + portInfo.getName() + "," + portInfo.getPortNumber());
                    portMap.put(mapIndex++, new MidiPortAddress(deviceIndex, portIndex));
                    portIndex++; // output Port Index
                }
            }
            deviceIndex++;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, portNames);
        spinner.setAdapter(adapter);

        return portMap;
    }

    protected void populateMidiChannelSpinner(Spinner spinner) {
        ArrayAdapter<String> midiChanAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[] {"1", "2","3","4","5","6","7","8","9","10","11","12","13","14","15","16"});
        spinner.setAdapter(midiChanAdapter);
    }

    protected void populateMidiDeviceSpinner(MidiDeviceInfo[] deviceList) {

        final Spinner inputDeviceSpinner = (Spinner) findViewById(R.id.midiDeviceSpinner);
        final HashMap<Integer, MidiPortAddress> inputPortMap = populateDeviceSpinner(deviceList, MidiDeviceInfo.PortInfo.TYPE_INPUT, inputDeviceSpinner);

        final Spinner outputDeviceSpinner = (Spinner) findViewById(R.id.midiOutputDeviceSpinner);
        final HashMap<Integer, MidiPortAddress> outputPortMap = populateDeviceSpinner(deviceList, MidiDeviceInfo.PortInfo.TYPE_OUTPUT, outputDeviceSpinner);

        final Spinner midiChannelSpinner = (Spinner) findViewById(R.id.channelSelect);
        populateMidiChannelSpinner(midiChannelSpinner);


        inputDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                closeMidiInputConnection();
                final int portMapIndex = inputDeviceSpinner.getSelectedItemPosition();
                final MidiPortAddress portAddress = inputPortMap.get(portMapIndex);
                openMidiInputConnection(portAddress);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                closeMidiInputConnection();
            }
        });

        outputDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                closeMidiOutputConnection();
                final int portMapIndex = outputDeviceSpinner.getSelectedItemPosition();
                final MidiPortAddress portAddress = outputPortMap.get(portMapIndex);
                openMidiOutputConnection(portAddress);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                closeMidiOutputConnection();
            }
        });


        Button playNoteButton = (Button) findViewById(R.id.playNoteButton);
        playNoteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (_activeMidiInputPort != null) {

                    int channel = midiChannelSpinner.getSelectedItemPosition();
                    playNote(channel, 200, 60);
                }
            }
        });

    }

    protected void playNote(int channel, int durationMillis, int note) {
        byte[] buffer = new byte[32];
        int offset = 0;
        int numBytes;

        note = note % 127;

        // post is non-blocking
        try {

            // Note On
            numBytes = 0;
            buffer[numBytes++] = (byte)(0x90 + (channel)); // note on
            buffer[numBytes++] = (byte)note; // pitch is middle C
            buffer[numBytes++] = (byte)127; // max velocity
            _activeMidiInputPort.send(buffer, offset, numBytes);


            //final long NANOS_PER_SECOND = 1000000000L;
            final long NANOS_PER_MILLI = 1000000L;
            long now = System.nanoTime();
            long future = now + (durationMillis * NANOS_PER_MILLI);

            // Note off
            numBytes = 0;
            buffer[numBytes++] = (byte)(0x80 + (channel)); // note on
            buffer[numBytes++] = (byte)60; // pitch is middle C
            buffer[numBytes++] = (byte)0; // max velocity
            _activeMidiInputPort.send(buffer, offset, numBytes, future);


        } catch (Exception e) {
            Log.e("MarkovSeq",e.toString());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        //mMidiDeviceToollbarView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
