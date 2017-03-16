package com.ianzieg.markovseq;

import android.media.midi.MidiReceiver;
import android.util.Log;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.math.BigDecimal;




public abstract class MidiSequencer extends MidiReceiver {

    public static final String LOG_TAG = "MIDI SEQ";

    public static final int NOTE_OFF = 0x80;
    public static final int NOTE_ON = 0x90;
    public static final int POLYPHONIC_AFTERTOUCH = 0xA0;
    public static final int CONTROL_CHANGE = 0xB0;
    public static final int PROGRAM_CHANGE = 0xC0;
    public static final int CHANNEL_AFTERTOUCH = 0x0;
    public static final int PITCH_WHEEL_RANGE = 0xE0;

    public static final int SYSEX_START = 0xF0;
    public static final int SYSEX_END = 0xF7;

    public static final int CLOCK_TICK = 0xF8;
    public static final int CLOCK_START = 0xFA;
    public static final int CLOCK_CONTINUE = 0xFB;
    public static final int CLOCK_STOP = 0xFC;
    public static final int CLOCK_ACTIVE_SENSE = 0xFE;
    public static final int CLOCK_RESET = 0xFF;

    public static final long NANOS_PER_SEC = 1000000000L;
    public static final int CLOCK_PER_QUANT = 24;

    protected long _clockCounter = 0;
    protected long _lastClockTimestamp = 0;
    protected long _clockTickDuration = 0;
    protected ArrayList<Long> _recentClockDurations = new ArrayList<Long>();


    public abstract void playNote(int pitch, int durationMillis);

    /***
     * http://www.midimountain.com/midi/midi_status.htm
     * @param msg
     * @param offset
     * @param count
     * @param timestamp
     * @throws IOException
     */
    @Override
    public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
        try {
            decodeMidi(msg, offset, count, timestamp);
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to decode midi message: count="+count+" timestamp="+timestamp+"\n"+ex);
        }
    }

    private void decodeMidi(byte[] received, int offset, int count, long timestamp) {
        int chan = 0;
        int d0 = 0;
        int d1 = 0;

        if (count < 1) {
            // Nothing to do
            return;
        }

        int status = received[offset] & 0xFF;

        if (0x80 <= status && status <= 0xEF) {

            // Instrument message
            chan = (status & 0xF);
            status = (status & 0xF0);
            if (count > 1) { d0 = received[offset+1] & 0xFF; }
            if (count > 2) { d1 = received[offset+2] & 0xFF; }

            switch (status) {
                case NOTE_OFF: break;
                case NOTE_ON: break;
                case POLYPHONIC_AFTERTOUCH: break;
                case CONTROL_CHANGE: break;
                case PROGRAM_CHANGE: break;
                case CHANNEL_AFTERTOUCH: break;
                case PITCH_WHEEL_RANGE: break;
            }

        } else if (SYSEX_START <= status && status <= SYSEX_END) {
            // System Exclusive Message
        } else if (CLOCK_TICK <= status && status <= CLOCK_RESET) {
            // System Time
            switch (status) {
                case CLOCK_TICK: handleClockTick(timestamp); break;
                case CLOCK_START: handleClockStart(); break;
                case CLOCK_CONTINUE: break;
                case CLOCK_STOP: handleClockStop(); break;
                case CLOCK_ACTIVE_SENSE: break;
                case CLOCK_RESET: break;

            }
        }

        //Log.i(LOG_TAG, "status: "+status+" chan: "+chan+" d0: "+d0+" d1: "+d1);
    }

    protected static final int CLOCK_HISTORY = 24;

    //protected int _noteCounter = 0;

    protected void handleClockTick(long timestamp) {
        //long now = System.nanoTime();
        if (_clockCounter % 2 == 0) {
            long now = timestamp;
            long deltLastTick = now - _lastClockTimestamp;
            _clockTickDuration = deltLastTick;
            _lastClockTimestamp = now;


            _recentClockDurations.add(deltLastTick);

            if (_recentClockDurations.size() > CLOCK_HISTORY) {
                 //_recentClockDurations = new ArrayList<Long>(_recentClockDurations.subList(_recentClockDurations.size() - CLOCK_HISTORY, CLOCK_HISTORY));
                Collections.sort(_recentClockDurations);
                _recentClockDurations = new ArrayList<Long>(_recentClockDurations.subList(0, 22));
                Collections.reverse(_recentClockDurations);
                _recentClockDurations =  new ArrayList<Long>(_recentClockDurations.subList(0, 20));
            }

            if (_clockCounter % CLOCK_PER_QUANT == 0) {
                int duration = getAverageTickMillis() * CLOCK_PER_QUANT / 8;
                int pitch = (int) (50L + ((_clockCounter / CLOCK_PER_QUANT) % 12L));
                playNote(pitch, duration);
                Log.i(LOG_TAG, "Clock Tick: duration="+deltLastTick+" bpm="+getBeatsPerMinute() +" pitch="+pitch+" dur="+duration);
            }
        }

        _clockCounter++;


      //  Log.i(LOG_TAG, "Clock Tick: "+getBeatsPerMinute()+"bpm @ "+timestamp);
    }

    public int getAverageTickMillis() {
        double _avgClockTick = 1;
        ArrayList<Long> clockDurs = new ArrayList<Long>(_recentClockDurations.subList(0, _recentClockDurations.size()));

        if (clockDurs.size() > 0) {
            for (long tickDur : clockDurs) {
                _avgClockTick += (double) tickDur;
            }
            _avgClockTick = _avgClockTick / clockDurs.size();
        }

        return (int) (_avgClockTick*2)/1000000;
    }

    public double getBeatsPerMinute() {
        //return 60.0 / ((NANOS_PER_SEC / _clockTickDuration) * CLOCK_PER_QUANT);
        double _avgClockTick = 0;
        ArrayList<Long> clockDurs = new ArrayList<Long>(_recentClockDurations.subList(0, _recentClockDurations.size()));

/*        if (clockDurs.size() > CLOCK_HISTORY) {
            Collections.sort(clockDurs);
            clockDurs = new ArrayList<Long>(clockDurs.subList(0, CLOCK_HISTORY - CLOCK_HISTORY/8));
            Collections.reverse(clockDurs);
            clockDurs =  new ArrayList<Long>(clockDurs.subList(0, CLOCK_HISTORY - CLOCK_HISTORY/4));
        }*/

        double bpm;
        if (clockDurs.size() > 0) {
            for (long tickDur : clockDurs) {
                _avgClockTick += (double) tickDur;
            }
            _avgClockTick = _avgClockTick / clockDurs.size();
            bpm = (1 / (_avgClockTick / NANOS_PER_SEC) / 24) * 60;
        } else {
            bpm = 0;
        }

        bpm = (new BigDecimal(bpm)).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return bpm *2;

    }

    protected void handleClockStart() {
        _clockCounter = 0;
        Log.i(LOG_TAG,"Clock Start");
    }

    protected void handleClockStop() {
        Log.i(LOG_TAG,"Clock Stop");
    }

}
