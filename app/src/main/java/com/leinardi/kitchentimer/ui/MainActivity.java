/**
 *  Kitchen Timer
 *  Copyright (C) 2010 Roberto Leinardi
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.leinardi.kitchentimer.ui;

/*
 * TODO:
 * Opzione per far suonare anche quando silenziato
 * Rendere modificabile il nome dei Timers
 */

import java.util.Timer;
import java.util.TimerTask;

import com.leinardi.kitchentimer.R;
import com.leinardi.kitchentimer.customviews.NumberPicker;
import com.leinardi.kitchentimer.misc.Changelog;
import com.leinardi.kitchentimer.misc.Constants;
import com.leinardi.kitchentimer.misc.Eula;
import com.leinardi.kitchentimer.misc.Log;
import com.leinardi.kitchentimer.utils.Utils;

import android.R.bool;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public final static String TAG = "MainActivity";

    private final static int[] TIMERS = {
            0, 1, 2
    };

    protected static final int MESSAGE_UPDATE_TIMER_TIME = 0;

    protected static final int MESSAGE_RESET_TIMER = 1;

    private static final int MESSAGE_DISPLAY_TIMER_AS_RUNNING = 2;

    private static final int MESSAGE_DISPLAY_TIMER_AS_NOT_RUNNING = 3;

    private static final int DIALOG_EXIT_QUESTION = 0;

    private static final int DIALOG_DONATE = 1;

    private static final int DIALOG_SET_TIMER_NAME = 2;

    private PowerManager.WakeLock mWakeLock = null;

    private AlarmManager mAlarmManager;

    private PendingIntent[] mPendingIntent;

    NotificationManager mNotificationManager;

    private SharedPreferences mPrefs;

    private boolean[] timerIsRunning;

    private int[] timerSeconds;

    private long[] timerStartTime;

    private String[] timerDefaultName;

    private NumberPicker npHours;

    private NumberPicker npMinutes;

    private NumberPicker npSeconds;

    private Button[] btnTimer;

    private TextView[] tvTimer;

    private TextView[] tvTimerLabel;

    private String presetName;

    ColorStateList timerDefaultColor;

    private Timer mTimerUpdateTimer;

    private boolean mTimersUpdateThreadIsStopped = true;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_UPDATE_TIMER_TIME:
                    tvTimer[msg.arg1].setText((String) msg.obj);
                    break;

                case MESSAGE_RESET_TIMER:

                    int timer = msg.arg1;
                    if (mPrefs.getBoolean(getString(R.string.pref_clear_timer_label_key), false)) {
                        tvTimerLabel[timer].setText(timerDefaultName[timer]);
                    }

                    tvTimer[timer].setTextColor(getResources().getColor(R.color.indian_red_1));

                    tvTimer[timer].setShadowLayer(Utils.dp2px(7, getApplicationContext()), 0f, 0f,
                            getResources().getColor(R.color.indian_red_1));
                    break;

                case MESSAGE_DISPLAY_TIMER_AS_RUNNING:
                    displayTimerAsRunning(msg.arg1);
                    break;

                case MESSAGE_DISPLAY_TIMER_AS_NOT_RUNNING:
                    displayTimerAsNotRunning(msg.arg1);
                    break;

                default:
                    break;
            }

            super.handleMessage(msg);
        }

    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Eula.show(this);
        Changelog.show(this);

        timerSeconds = new int[Constants.NUM_TIMERS];
        timerStartTime = new long[Constants.NUM_TIMERS];
        timerIsRunning = new boolean[Constants.NUM_TIMERS];

        timerDefaultName = new String[Constants.NUM_TIMERS];
        timerDefaultName[0] = getString(R.string.timer1);
        timerDefaultName[1] = getString(R.string.timer2);
        timerDefaultName[2] = getString(R.string.timer3);

        mPendingIntent = new PendingIntent[Constants.NUM_TIMERS];

        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.setReferenceCounted(false);

        for (int timer = 0; timer < Constants.NUM_TIMERS; timer++) {
            timerIsRunning[timer] = false;
            Intent intent = new Intent(Constants.INTENT_TIMER_ENDED);
            intent.putExtra(Constants.TIMER, timer);
            // intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mPendingIntent[timer] = PendingIntent.getBroadcast(this, timer, intent, 0);
        }

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        acquireWakeLock();

        initWidgets();

        if (mPrefs.getBoolean(getString(R.string.pref_show_tips_key), true)) {
            Toast toast = Toast.makeText(this, getString(R.string.tip1), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
        }
    }

    /** Get references to UI widgets and initialize them if needed */
    private void initWidgets() {
        npHours = (NumberPicker) findViewById(R.id.npHours);
        npMinutes = (NumberPicker) findViewById(R.id.npMinutes);
        npSeconds = (NumberPicker) findViewById(R.id.npSeconds);

        npHours.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
        npMinutes.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
        npSeconds.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);

        npHours.setRange(0, 23);
        npMinutes.setRange(0, 59);
        npSeconds.setRange(0, 59);

        npHours.setSpeed(50);
        npMinutes.setSpeed(50);
        npSeconds.setSpeed(50);

        npHours.setCurrent(mPrefs.getInt(Constants.PREF_HOURS, 0));
        npMinutes.setCurrent(mPrefs.getInt(Constants.PREF_MINUTES, 0));
        npSeconds.setCurrent(mPrefs.getInt(Constants.PREF_SECONDS, 0));

        btnTimer = new Button[Constants.NUM_TIMERS];
        btnTimer[0] = (Button) findViewById(R.id.btnTimer0);
        btnTimer[1] = (Button) findViewById(R.id.btnTimer1);
        btnTimer[2] = (Button) findViewById(R.id.btnTimer2);

        tvTimer = new TextView[Constants.NUM_TIMERS];
        tvTimer[0] = (TextView) this.findViewById(R.id.tvTimer0);
        tvTimer[1] = (TextView) this.findViewById(R.id.tvTimer1);
        tvTimer[2] = (TextView) this.findViewById(R.id.tvTimer2);

        tvTimerLabel = new TextView[Constants.NUM_TIMERS];
        tvTimerLabel[0] = (TextView) this.findViewById(R.id.tvTimer0_label);
        tvTimerLabel[1] = (TextView) this.findViewById(R.id.tvTimer1_label);
        tvTimerLabel[2] = (TextView) this.findViewById(R.id.tvTimer2_label);

        timerDefaultColor = tvTimer[0].getTextColors();

        for (int timer = 0; timer < Constants.NUM_TIMERS; timer++) {
            tvTimerLabel[timer].setText(mPrefs.getString(Constants.PREF_TIMERS_NAMES[timer],
                    timerDefaultName[timer]));
            btnTimer[timer].setOnClickListener(this.clickListener);
            tvTimerLabel[timer].setOnClickListener(this.clickListener);
            tvTimer[timer].setOnClickListener(this.clickListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(Constants.PREF_HOURS, npHours.getCurrent());
        editor.putInt(Constants.PREF_MINUTES, npMinutes.getCurrent());
        editor.putInt(Constants.PREF_SECONDS, npSeconds.getCurrent());
        editor.commit();

        if (!areAllTimerStopped()) {
            stopTimerUpdateThread();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (int timer = 0; timer < Constants.NUM_TIMERS; timer++) {
            timerSeconds[timer] = mPrefs.getInt(Constants.PREF_TIMERS_SECONDS[timer], 0);
            timerStartTime[timer] = mPrefs.getLong(Constants.PREF_START_TIMES[timer], 0L);
            timerIsRunning[timer] = (timerStartTime[timer] != 0L);
            manageTimerUpdateThread();
            tvTimerLabel[timer].setText(mPrefs.getString(Constants.PREF_TIMERS_NAMES[timer],
                    timerDefaultName[timer]));
        }
    }

    private void manageTimerUpdateThread() {
        if (!areAllTimerStopped() && mTimersUpdateThreadIsStopped) {
            startTimerUpdateThread();
            return;
        }

        if (areAllTimerStopped() && !mTimersUpdateThreadIsStopped) {
            stopTimerUpdateThread();
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        acquireWakeLock();
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseWakeLock();
    }

    private void acquireWakeLock() {
        // It's okay to double-acquire this because we are not using it
        // in reference-counted mode.
        if (mPrefs.getBoolean(getString(R.string.pref_keep_screen_on_key), false))
            mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        // Don't release the wake lock if it hasn't been created and acquired.
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_PRESETS) {
            if (resultCode == RESULT_OK) {
                Bundle extras = data.getExtras();
                npHours.setCurrent(extras.getInt("hours"));
                npMinutes.setCurrent(extras.getInt("minutes"));
                npSeconds.setCurrent(extras.getInt("seconds"));
                presetName = extras.getString("name");
            }
        }
    }

    private OnClickListener clickListener = new OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tvTimer0_label:
                    setTimerName(TIMERS[0]);
                    break;
                case R.id.tvTimer1_label:
                    setTimerName(TIMERS[1]);
                    break;
                case R.id.tvTimer2_label:
                    setTimerName(TIMERS[2]);
                    break;
                case R.id.tvTimer0:
                    cancelTimeIsOverNotification(TIMERS[0]);
                    break;
                case R.id.tvTimer1:
                    cancelTimeIsOverNotification(TIMERS[1]);
                    break;
                case R.id.tvTimer2:
                    cancelTimeIsOverNotification(TIMERS[2]);
                    break;
                case R.id.btnTimer0:
                    startTimer(TIMERS[0]);
                    break;
                case R.id.btnTimer1:
                    startTimer(TIMERS[1]);
                    break;
                case R.id.btnTimer2:
                    startTimer(TIMERS[2]);
                    break;
            }
        }
    };

    private int mTimerNumberForEditingName;

    private void setTimerName(final int timer) {
        // Under API level 8 is not possible to pass Bundle object to showDialog
        // then we need to save the value in a field
        mTimerNumberForEditingName = timer;
        showDialog(DIALOG_SET_TIMER_NAME);
    }

    private void startTimer(int timer) {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(timer + 10);
        }
        mPendingIntent[timer].cancel();
        Intent intent = new Intent(Constants.INTENT_TIMER_ENDED);
        intent.putExtra(Constants.TIMER, timer);
        SharedPreferences.Editor editor = mPrefs.edit();
        if (presetName != null) {
            intent.putExtra(Constants.TIMER_NAME, presetName);
            tvTimerLabel[timer].setText(presetName);
            editor.putString(Constants.PREF_TIMERS_NAMES[timer], presetName);
        } else {
            intent.putExtra(Constants.TIMER_NAME, mPrefs.getString(
                    Constants.PREF_TIMERS_NAMES[timer], timerDefaultName[timer]));
        }
        editor.commit();
        mPendingIntent[timer] = PendingIntent.getBroadcast(this, timer, intent, 0);

        btnTimer[timer].requestFocusFromTouch();
        if (timerIsRunning[timer])
            setTimerState(false, timer);
        else {
            timerSeconds[timer] = npHours.getCurrent() * 3600 + npMinutes.getCurrent() * 60
                    + npSeconds.getCurrent();
            if (timerSeconds[timer] > 0) {
                setTimerState(true, timer);
                if (mPrefs.getBoolean(getString(R.string.pref_show_tips_key), true)) {
                    Toast toast = Toast.makeText(this, getString(R.string.tip1), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.TOP, 0, 0);
                    toast.show();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_time), Toast.LENGTH_LONG).show();
            }
        }

        manageTimerUpdateThread();
    }

    private void cancelTimeIsOverNotification(int timer) {
        if (!timerIsRunning[timer]) {
            tvTimer[timer].setTextColor(timerDefaultColor);
            tvTimer[timer].setShadowLayer(0f, 0f, 0f, 0);
            if (mNotificationManager != null) {
                mNotificationManager.cancel(timer + 10);
            }
        }
    }

    /**
     * Sets the timer on or off
     * 
     * @param timer
     */
    private void setTimerState(boolean state, int timer) {
        timerIsRunning[timer] = state;

        Message m = mHandler.obtainMessage(state ? MESSAGE_DISPLAY_TIMER_AS_RUNNING
                : MESSAGE_DISPLAY_TIMER_AS_NOT_RUNNING, timer, 0);

        mHandler.sendMessage(m);

        setAlarmState(state, timer);
        sendTimerIsRunningNotification(state, timer);
    }

    /**
     * Sets the alarm on or off This makes use of the alarm system service
     * 
     * @param timer
     */
    private void setAlarmState(boolean state, int timer) {
        if (state) {
            timerStartTime[timer] = SystemClock.elapsedRealtime();
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()
                    + timerSeconds[timer] * 1000, mPendingIntent[timer]);
        } else {
            timerStartTime[timer] = 0L;
            mAlarmManager.cancel(mPendingIntent[timer]);
        }
        presetName = null;
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(Constants.PREF_TIMERS_SECONDS[timer], timerSeconds[timer]);
        editor.putLong(Constants.PREF_START_TIMES[timer], timerStartTime[timer]);
        editor.commit();
    }

    private void displayTimerAsNotRunning(int timer) {
        tvTimer[timer].setTextColor(timerDefaultColor);
        tvTimer[timer].setShadowLayer(0f, 0f, 0f, 0);
        tvTimer[timer].setText(timer == 0 ? R.string.sixzeros : R.string.fourzeros);
        btnTimer[timer].setText(R.string.start);
    }

    private void displayTimerAsRunning(int timer) {
        tvTimer[timer].setTextColor(getResources().getColor(R.color.white));
        tvTimer[timer].setShadowLayer(Utils.dp2px(4, this), 0f, 0f, getResources().getColor(
                R.color.white));
        btnTimer[timer].setText(R.string.stop);
    }

    /**
     * @param running
     * @param timer
     */
    private void sendTimerIsRunningNotification(boolean running, int timer) {
        if (running) {
            int icon = R.drawable.stat_notify_alarm;
            CharSequence mTickerText = getString(R.string.timer_started);
            long when = System.currentTimeMillis();
            Notification notification = new Notification(icon, mTickerText, when);

            Context context = getApplicationContext();
            CharSequence mContentTitle = getString(R.string.app_name);
            CharSequence mContentText = getString(R.string.click_to_open);

            Intent clickIntent = new Intent(this, MainActivity.class);
            clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            notification.setLatestEventInfo(context, mContentTitle, mContentText, contentIntent);

            notification.ledARGB = 0x00000000;
            notification.ledOnMS = 0;
            notification.ledOffMS = 0;
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.flags |= Notification.FLAG_NO_CLEAR;

            mNotificationManager.notify(Constants.APP_NOTIF_ID, notification);
        } else {
            boolean thereAreTimersRunning = false;
            for (int i = 0; i < timerIsRunning.length; i++) {
                thereAreTimersRunning = thereAreTimersRunning || timerIsRunning[i];
            }
            if (!thereAreTimersRunning) {
                mNotificationManager.cancel(Constants.APP_NOTIF_ID);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_info:
                intent = new Intent(this, InfoActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_presets:
                intent = new Intent(this, PresetsActivity.class);
                startActivityForResult(intent, Constants.REQUEST_PRESETS);
                return true;
            case R.id.menu_preferences:
                intent = new Intent(this, ConfigActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_exit:
                showDialog(DIALOG_EXIT_QUESTION);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void stopAllTimersAndCancelNotification() {
        boolean flag = false;
        for (int i = 0; i < timerIsRunning.length; i++) {
            if (timerIsRunning[i]) {
                mAlarmManager.cancel(mPendingIntent[i]);
                flag = true;
            }
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putInt(Constants.PREF_TIMERS_SECONDS[i], 0);
            editor.putLong(Constants.PREF_START_TIMES[i], 0L);
            editor.commit();
            timerSeconds[i] = 0;
            timerStartTime[i] = 0L;
            timerIsRunning[i] = false;
        }
        if (flag) {
            mNotificationManager.cancel(Constants.APP_NOTIF_ID);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        AlertDialog.Builder builder;

        switch (id) {
            case DIALOG_EXIT_QUESTION:
                builder = new AlertDialog.Builder(this);

                builder.setTitle(R.string.exit_title);
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage(R.string.exit_message);

                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        stopAllTimersAndCancelNotification();
                        finish();
                    }
                });

                builder.setNeutralButton(R.string.cancel, null);

                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                });

                return builder.create();

            case DIALOG_DONATE:
                builder = new AlertDialog.Builder(this);

                builder.setTitle(R.string.pref_donate);
                builder.setMessage(R.string.donate_message);

                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Utils.donate(getApplicationContext());
                    }
                });

                builder.setNegativeButton(R.string.no, null);

                return builder.create();

            case DIALOG_SET_TIMER_NAME:
                builder = new AlertDialog.Builder(this);

                builder.setTitle(R.string.edit_timer_name);
                builder.setIcon(R.drawable.icon);

                // TODO Externalize this into a xml layout

                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setPadding((int) Utils.dp2px(10, this), 0, (int) Utils.dp2px(10, this),
                        0);
                final EditText etTimerName = new EditText(this);
                etTimerName.setHint(R.string.timer_name);
                etTimerName.setMaxLines(1);
                etTimerName.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                etTimerName.setFilters(new InputFilter[] {
                    new InputFilter.LengthFilter(50)
                });
                etTimerName.setText(mPrefs.getString(
                        Constants.PREF_TIMERS_NAMES[mTimerNumberForEditingName],
                        timerDefaultName[mTimerNumberForEditingName]));
                frameLayout.addView(etTimerName);
                builder.setView(frameLayout);

                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String timerName = (etTimerName.getText().toString());
                        if (timerName.length() > 0) {
                            tvTimerLabel[mTimerNumberForEditingName].setText(timerName);
                            SharedPreferences.Editor editor = mPrefs.edit();
                            editor.putString(
                                    Constants.PREF_TIMERS_NAMES[mTimerNumberForEditingName],
                                    timerName);
                            editor.commit();
                        }
                    }
                });

                builder.setNeutralButton(R.string.default_text,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                tvTimerLabel[mTimerNumberForEditingName]
                                        .setText(timerDefaultName[mTimerNumberForEditingName]);
                                SharedPreferences.Editor editor = mPrefs.edit();
                                editor.putString(
                                        Constants.PREF_TIMERS_NAMES[mTimerNumberForEditingName],
                                        timerDefaultName[mTimerNumberForEditingName]);
                                editor.commit();
                            }
                        });

                builder.setNegativeButton(R.string.cancel, null);

                return builder.create();

            default:
                break;
        }
        return super.onCreateDialog(id);
    }

    public void startTimerUpdateThread() {
        mTimersUpdateThreadIsStopped = false;
        mTimerUpdateTimer = new Timer();
        mTimerUpdateTimer.schedule(new UpdateTimerTask(), 0, 1000);
    }

    public void stopTimerUpdateThread() {
        mTimersUpdateThreadIsStopped = true;
        mTimerUpdateTimer.cancel();
    }

    public boolean areAllTimerStopped() {

        for (int i = 0; i < timerIsRunning.length; i++) {
            if (timerIsRunning[i]) {
                return false;
            }
        }

        return true;
    }

    private final class UpdateTimerTask extends TimerTask {

        @Override
        public void run() {

            for (int timer = 0; timer < Constants.NUM_TIMERS; timer++) {
                if (timerIsRunning[timer]) {

                    long remainingSeconds = timerSeconds[timer]
                            - (SystemClock.elapsedRealtime() - timerStartTime[timer]) / 1000;

                    String newTime = Utils.formatTime(Math.max(remainingSeconds, 0L), timer);

                    Message m = mHandler
                            .obtainMessage(MESSAGE_UPDATE_TIMER_TIME, timer, 0, newTime);
                    mHandler.sendMessage(m);

                    if (remainingSeconds <= 0) {

                        setTimerState(false, timer);
                        Message resetMessage = mHandler.obtainMessage(MESSAGE_RESET_TIMER, timer);
                        mHandler.sendMessage(resetMessage);

                    }

                }
            }

        }
    }

}
