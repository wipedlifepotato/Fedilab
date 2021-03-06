package app.fedilab.android.services;
/* Copyright 2019 Thomas Schneider
 *
 * This file is a part of Fedilab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Fedilab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Fedilab; if not,
 * see <http://www.gnu.org/licenses>. */

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import app.fedilab.android.R;
import app.fedilab.android.activities.MainActivity;
import app.fedilab.android.client.API;
import app.fedilab.android.client.APIResponse;
import app.fedilab.android.client.Entities.Account;
import app.fedilab.android.client.Entities.Notification;
import app.fedilab.android.client.GNUAPI;
import app.fedilab.android.fragments.DisplayNotificationsFragment;
import app.fedilab.android.helper.Helper;
import app.fedilab.android.sqlite.AccountDAO;
import app.fedilab.android.sqlite.Sqlite;

import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;
import static app.fedilab.android.helper.Helper.getMainLogo;
import static app.fedilab.android.helper.Helper.getNotificationIcon;
import static app.fedilab.android.helper.Helper.sleeps;


/**
 * Created by Thomas on 10/09/2019.
 * Manage service for live notifications delayed
 */

public class LiveNotificationDelayedService extends Service {


    public static String CHANNEL_ID = "live_notifications";
    public static int totalAccount = 0;
    public static int eventsCount = 0;
    public static HashMap<String, String> since_ids = new HashMap<>();
    protected Account account;
    private NotificationChannel channel;
    private boolean fetch;


    public void onCreate() {
        super.onCreate();
        final SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean notify = sharedpreferences.getBoolean(Helper.SET_NOTIFY, true);

        if (Build.VERSION.SDK_INT >= 26) {
            channel = new NotificationChannel(CHANNEL_ID,
                    "Live notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) Objects.requireNonNull(getSystemService(Context.NOTIFICATION_SERVICE))).createNotificationChannel(channel);
        }

        SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
        List<Account> accountStreams = new AccountDAO(getApplicationContext(), db).getAllAccountCrossAction();
        totalAccount = 0;
        if( accountStreams != null) {
            for (Account account : accountStreams) {
                boolean allowStream = sharedpreferences.getBoolean(Helper.SET_ALLOW_STREAM + account.getId() + account.getInstance(), true);
                if (allowStream) {
                    totalAccount++;
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 26) {
            Intent myIntent = new Intent(getApplicationContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    myIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setShowWhen(false)
                    .setContentIntent(pendingIntent)
                    .setContentTitle(getString(R.string.top_notification))
                    .setSmallIcon(getNotificationIcon(getApplicationContext()))
                    .setContentText(getString(R.string.top_notification_message, String.valueOf(totalAccount), String.valueOf(eventsCount))).build();
            startForeground(1, notification);
        }


        if( !notify ){
            stopSelf();
            return;
        }
        if (totalAccount > 0) {
            startStream();
        } else {
            stopSelf();
        }

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("notif_delayed_")){
                t.interrupt();
            }
        }
        Thread.currentThread().interrupt();
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean notify = sharedpreferences.getBoolean(Helper.SET_NOTIFY, true);
        if (!notify || intent == null || intent.getBooleanExtra("stop", false)) {
            totalAccount = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationManager notificationManager =  (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert notificationManager != null;
                notificationManager.deleteNotificationChannel(CHANNEL_ID);
            }
            if (intent != null) {
                intent.replaceExtras(new Bundle());
            }
            stopSelf();
        }
        if (totalAccount > 0) {
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }


    private void startStream() {

        SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
        if (Helper.liveNotifType(getApplicationContext()) == Helper.NOTIF_DELAYED) {
            List<Account> accountStreams = new AccountDAO(getApplicationContext(), db).getAllAccountCrossAction();
            final SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            fetch = true;
            if (accountStreams != null) {
                for (final Account accountStream : accountStreams) {
                    String key = accountStream.getUsername() + "@" + accountStream.getInstance();
                    boolean allowStream = sharedpreferences.getBoolean(Helper.SET_ALLOW_STREAM + accountStream.getId() + accountStream.getInstance(), true);
                    if (!allowStream) {
                        continue;
                    }
                    if (!sleeps.containsKey(key)) {
                        sleeps.put(key, 30000);
                    }
                    Thread thread = Helper.getThreadByName("notif_delayed_"+key);
                    if( thread == null){
                        startThread(accountStream, key);
                    } else if(thread.getState() != Thread.State.RUNNABLE) {
                        thread.interrupt();
                        startThread(accountStream, key);
                    }
                }
            }
        }
    }

    private void startThread(Account accountStream, String key){
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (fetch) {
                    taks(accountStream);
                    fetch = (Helper.liveNotifType(getApplicationContext()) == Helper.NOTIF_DELAYED);
                    if (sleeps.containsKey(key) && sleeps.get(key) != null) {
                        try {
                            Thread.sleep(sleeps.get(key));
                        } catch (InterruptedException e) {
                            SystemClock.sleep(sleeps.get(key));
                        }
                    }
                }
            }
        };
        thread.setName("notif_delayed_"+key);
        thread.start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void taks(Account account) {
        String key = account.getUsername() + "@" + account.getInstance();
        APIResponse apiResponse;

        String last_notifid = null;
        if (since_ids.containsKey(key)) {
            last_notifid = since_ids.get(key);
        }
        apiResponse = null;
        try {
            if(account.getSocial().compareTo("FRIENDICA") != 0 && account.getSocial().compareTo("GNU") != 0 ) {
                API api;
                api = new API(getApplicationContext(), account.getInstance(), account.getToken());
                apiResponse = api.getNotificationsSince(DisplayNotificationsFragment.Type.ALL, last_notifid, false);
            }else{
                GNUAPI gnuApi;
                gnuApi = new GNUAPI(getApplicationContext(), account.getInstance(), account.getToken());
                apiResponse = gnuApi.getNotificationsSince(DisplayNotificationsFragment.Type.ALL, last_notifid, false);
            }
        } catch (Exception ignored) {
        }

        if (apiResponse != null && apiResponse.getNotifications() != null && apiResponse.getNotifications().size() > 0) {
            since_ids.put(key, apiResponse.getNotifications().get(0).getId());
            for (Notification notification : apiResponse.getNotifications()) {
                if (last_notifid != null && notification.getId().compareTo(last_notifid) > 0) {
                    onRetrieveStreaming(account, notification);
                    sleeps.put(key, 30000);
                } else {
                    if (apiResponse.getNotifications().size() == 1) { //TODO: use min id with Pixelfed when available for removing this fix.
                        if (sleeps.containsKey(key) && sleeps.get(key) != null) {
                            int newWaitTime = sleeps.get(key) + 30000;
                            if (newWaitTime > 900000) {
                                newWaitTime = 900000;
                            }
                            sleeps.put(key, newWaitTime);
                        } else {
                            sleeps.put(key, 60000);
                        }
                    }
                    break;
                }
            }
        } else {
            if (sleeps.containsKey(key) && sleeps.get(key) != null) {
                int newWaitTime = sleeps.get(key) + 30000;
                if (newWaitTime > 900000) {
                    newWaitTime = 900000;
                }
                sleeps.put(key, newWaitTime);
            } else {
                sleeps.put(key, 60000);
            }
        }
    }


    private void onRetrieveStreaming(Account account, Notification notification) {

        Bundle b = new Bundle();
        boolean canSendBroadCast = true;
        Helper.EventStreaming event;
        final SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        try {
            eventsCount++;
            if (Build.VERSION.SDK_INT >= 26) {
                channel = new NotificationChannel(CHANNEL_ID,
                        "Live notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ((NotificationManager) Objects.requireNonNull(getSystemService(Context.NOTIFICATION_SERVICE))).createNotificationChannel(channel);
                android.app.Notification notificationChannel = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setShowWhen(false)
                        .setContentTitle(getString(R.string.top_notification))
                        .setSmallIcon(getNotificationIcon(getApplicationContext()))
                        .setContentText(getString(R.string.top_notification_message, String.valueOf(totalAccount), String.valueOf(eventsCount))).build();

                startForeground(1, notificationChannel);
            }

            event = Helper.EventStreaming.NOTIFICATION;
            boolean canNotify = Helper.canNotify(getApplicationContext());
            boolean notify = sharedpreferences.getBoolean(Helper.SET_NOTIFY, true);
            String targeted_account = null;
            Helper.NotifType notifType = Helper.NotifType.MENTION;
            boolean activityRunning = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("isMainActivityRunning", false);
            boolean allowStream = sharedpreferences.getBoolean(Helper.SET_ALLOW_STREAM + account.getId() + account.getInstance(), true);
            if (!allowStream) {
                canNotify = false;
            }
            if ((userId == null || !userId.equals(account.getId()) || !activityRunning) && canNotify && notify) {
                boolean notif_follow = sharedpreferences.getBoolean(Helper.SET_NOTIF_FOLLOW, true);
                boolean notif_add = sharedpreferences.getBoolean(Helper.SET_NOTIF_ADD, true);
                boolean notif_mention = sharedpreferences.getBoolean(Helper.SET_NOTIF_MENTION, true);
                boolean notif_share = sharedpreferences.getBoolean(Helper.SET_NOTIF_SHARE, true);
                boolean notif_poll = sharedpreferences.getBoolean(Helper.SET_NOTIF_POLL, true);
                boolean somethingToPush = (notif_follow || notif_add || notif_mention || notif_share || notif_poll);

                String message = null;
                if (somethingToPush) {
                    switch (notification.getType()) {
                        case "mention":
                            notifType = Helper.NotifType.MENTION;
                            if (notif_mention) {
                                if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                                    message = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), getString(R.string.notif_mention));
                                else
                                    message = String.format("@%s %s", notification.getAccount().getAcct(), getString(R.string.notif_mention));
                                if (notification.getStatus() != null) {
                                    if (notification.getStatus().getSpoiler_text() != null && notification.getStatus().getSpoiler_text().length() > 0) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                            message = "\n" + new SpannableString(Html.fromHtml(notification.getStatus().getSpoiler_text(), FROM_HTML_MODE_LEGACY));
                                        else
                                            message = "\n" + new SpannableString(Html.fromHtml(notification.getStatus().getSpoiler_text()));
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                            message = "\n" + new SpannableString(Html.fromHtml(notification.getStatus().getContent(), FROM_HTML_MODE_LEGACY));
                                        else
                                            message = "\n" + new SpannableString(Html.fromHtml(notification.getStatus().getContent()));
                                    }
                                }
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        case "reblog":
                            notifType = Helper.NotifType.BOOST;
                            if (notif_share) {
                                if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                                    message = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), getString(R.string.notif_reblog));
                                else
                                    message = String.format("@%s %s", notification.getAccount().getAcct(), getString(R.string.notif_reblog));
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        case "favourite":
                            notifType = Helper.NotifType.FAV;
                            if (notif_add) {
                                if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                                    message = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), getString(R.string.notif_favourite));
                                else
                                    message = String.format("@%s %s", notification.getAccount().getAcct(), getString(R.string.notif_favourite));
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        case "follow_request":
                            notifType = Helper.NotifType.FOLLLOW;
                            if (notif_follow) {
                                if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                                    message = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), getString(R.string.notif_follow_request));
                                else
                                    message = String.format("@%s %s", notification.getAccount().getAcct(), getString(R.string.notif_follow_request));
                                targeted_account = notification.getAccount().getId();
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        case "follow":
                            notifType = Helper.NotifType.FOLLLOW;
                            if (notif_follow) {
                                if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                                    message = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), getString(R.string.notif_follow));
                                else
                                    message = String.format("@%s %s", notification.getAccount().getAcct(), getString(R.string.notif_follow));
                                targeted_account = notification.getAccount().getId();
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        case "poll":
                            notifType = Helper.NotifType.POLL;
                            if (notif_poll) {
                                if (notification.getAccount().getId() != null && notification.getAccount().getId().equals(userId))
                                    message = getString(R.string.notif_poll_self);
                                else
                                    message = getString(R.string.notif_poll);
                            } else {
                                canSendBroadCast = false;
                            }
                            break;
                        default:
                    }

                    //Some others notification
                    final Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(Helper.INTENT_ACTION, Helper.NOTIFICATION_INTENT);
                    intent.putExtra(Helper.PREF_KEY_ID, account.getId());
                    intent.putExtra(Helper.PREF_INSTANCE, account.getInstance());
                    if (targeted_account != null) {
                        intent.putExtra(Helper.INTENT_TARGETED_ACCOUNT, targeted_account);
                    }
                    final String finalMessage = message;
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Helper.NotifType finalNotifType = notifType;
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (finalMessage != null) {
                                Glide.with(getApplicationContext())
                                        .asBitmap()
                                        .load(notification.getAccount().getAvatar())
                                        .listener(new RequestListener<Bitmap>() {
                                            @Override
                                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                                return false;
                                            }

                                            @Override
                                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target target, boolean isFirstResource) {
                                                Helper.notify_user(getApplicationContext(), account, intent, BitmapFactory.decodeResource(getResources(),
                                                        getMainLogo(getApplicationContext())), finalNotifType, "@" + notification.getAccount().getAcct(), finalMessage);
                                                return false;
                                            }
                                        })
                                        .into(new SimpleTarget<Bitmap>() {
                                            @Override
                                            public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {

                                                Helper.notify_user(getApplicationContext(), account, intent, resource, finalNotifType, "@" + notification.getAccount().getAcct(), finalMessage);
                                            }
                                        });
                            }
                        }
                    };
                    mainHandler.post(myRunnable);
                }
            }

            if (canSendBroadCast) {
                b.putString("userIdService", account.getId());
                Intent intentBC = new Intent(Helper.RECEIVE_DATA);
                intentBC.putExtra("eventStreaming", event);
                intentBC.putExtras(b);
                b.putParcelable("data", notification);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentBC);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), notification.getId());
                editor.apply();
            }
        } catch (Exception ignored) {
        }
    }

}