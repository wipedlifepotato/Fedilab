package app.fedilab.android.fragments;
/* Copyright 2017 Thomas Schneider
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.ListPreloader;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.util.FixedPreloadSizeProvider;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.fedilab.android.R;
import app.fedilab.android.activities.MainActivity;
import app.fedilab.android.asynctasks.RetrieveMissingNotificationsAsyncTask;
import app.fedilab.android.asynctasks.RetrieveNotificationsAsyncTask;
import app.fedilab.android.asynctasks.UpdateAccountInfoAsyncTask;
import app.fedilab.android.client.APIResponse;
import app.fedilab.android.client.Entities.Account;
import app.fedilab.android.client.Entities.Notification;
import app.fedilab.android.client.Entities.Status;
import app.fedilab.android.drawers.NotificationsListAdapter;
import app.fedilab.android.helper.Helper;
import app.fedilab.android.interfaces.OnRetrieveMissingNotificationsInterface;
import app.fedilab.android.interfaces.OnRetrieveNotificationsInterface;
import app.fedilab.android.services.LiveNotificationDelayedService;
import app.fedilab.android.sqlite.AccountDAO;
import app.fedilab.android.sqlite.Sqlite;
import es.dmoral.toasty.Toasty;

import static android.content.Context.MODE_PRIVATE;
import static app.fedilab.android.activities.BaseMainActivity.countNewNotifications;


/**
 * Created by Thomas on 28/04/2017.
 * Fragment to display notifications related to accounts
 */
public class DisplayNotificationsFragment extends Fragment implements OnRetrieveNotificationsInterface, OnRetrieveMissingNotificationsInterface {


    LinearLayoutManager mLayoutManager;
    Type type;
    private boolean flag_loading;
    private Context context;
    private AsyncTask<Void, Void, Void> asyncTask;
    private NotificationsListAdapter notificationsListAdapter;
    private String max_id;
    private List<Notification> notifications;
    private RelativeLayout mainLoader, nextElementLoader, textviewNoAction;
    private boolean firstLoad;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean swiped;
    private RecyclerView lv_notifications;
    private String userId, instance;
    private SharedPreferences sharedpreferences;
    private BroadcastReceiver receive_action;
    private BroadcastReceiver receive_data;

    public DisplayNotificationsFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_notifications, container, false);
        max_id = null;
        context = getContext();
        firstLoad = true;
        flag_loading = true;
        notifications = new ArrayList<>();
        swiped = false;
        swipeRefreshLayout = rootView.findViewById(R.id.swipeContainer);
        int c1 = getResources().getColor(R.color.cyanea_accent);
        int c2 = getResources().getColor(R.color.cyanea_primary_dark);
        int c3 = getResources().getColor(R.color.cyanea_primary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(c3);
        swipeRefreshLayout.setColorSchemeColors(
                c1, c2, c1
        );
        sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        Bundle bundle = this.getArguments();
        if (bundle != null) {
            type = (Type) bundle.get("type");
        }
        lv_notifications = rootView.findViewById(R.id.lv_notifications);
        mainLoader = rootView.findViewById(R.id.loader);
        nextElementLoader = rootView.findViewById(R.id.loading_next_notifications);
        textviewNoAction = rootView.findViewById(R.id.no_action);
        mainLoader.setVisibility(View.VISIBLE);
        nextElementLoader.setVisibility(View.GONE);
        boolean isOnWifi = Helper.isOnWIFI(context);
        int behaviorWithAttachments = sharedpreferences.getInt(Helper.SET_ATTACHMENT_ACTION, Helper.ATTACHMENT_ALWAYS);
        userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        instance = sharedpreferences.getString(Helper.PREF_INSTANCE, context != null ? Helper.getLiveInstance(context) : null);
        notificationsListAdapter = new NotificationsListAdapter(isOnWifi, behaviorWithAttachments, this.notifications);
        lv_notifications.setAdapter(notificationsListAdapter);
        mLayoutManager = new LinearLayoutManager(context);
        lv_notifications.setLayoutManager(mLayoutManager);
        lv_notifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleItemCount = mLayoutManager.getChildCount();
                    int totalItemCount = mLayoutManager.getItemCount();
                    int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                    if (firstVisibleItem + visibleItemCount == totalItemCount && context != null) {
                        if (!flag_loading) {
                            flag_loading = true;
                            asyncTask = new RetrieveNotificationsAsyncTask(context, type, true, null, max_id, DisplayNotificationsFragment.this).execute();
                            nextElementLoader.setVisibility(View.VISIBLE);
                        }
                    } else {
                        nextElementLoader.setVisibility(View.GONE);
                    }
                }
            }
        });

        if (MainActivity.social == UpdateAccountInfoAsyncTask.SOCIAL.MASTODON || MainActivity.social == UpdateAccountInfoAsyncTask.SOCIAL.PLEROMA) {

            if (receive_action != null)
                LocalBroadcastManager.getInstance(context).unregisterReceiver(receive_action);
            receive_action = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle b = intent.getExtras();
                    assert b != null;
                    Status status = b.getParcelable("status");
                    if (notificationsListAdapter != null && status != null)
                        notificationsListAdapter.notifyNotificationWithActionChanged(status);
                }
            };
            LocalBroadcastManager.getInstance(context).registerReceiver(receive_action, new IntentFilter(Helper.RECEIVE_ACTION));
            if (type == Type.ALL) {
                if (receive_data != null)
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(receive_data);
                receive_data = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Bundle b = intent.getExtras();
                        assert b != null;
                        String userIdService = b.getString("userIdService", null);
                        if (userIdService != null && userIdService.equals(userId)) {
                            Notification notification = b.getParcelable("data");
                            refresh(notification);
                            if (context instanceof MainActivity && type == Type.ALL)
                                ((MainActivity) context).updateNotifCounter();
                        }
                    }
                };
                LocalBroadcastManager.getInstance(context).registerReceiver(receive_data, new IntentFilter(Helper.RECEIVE_DATA));
            }
        }

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                flag_loading = true;
                swiped = true;
                if (type == Type.ALL) {
                    countNewNotifications = 0;
                    try {
                        ((MainActivity) context).updateNotifCounter();
                    } catch (Exception ignored) {
                    }
                }
                String sinceId = null;
                if (notifications != null && notifications.size() > 0)
                    sinceId = notifications.get(0).getId();
                if (context != null)
                    asyncTask = new RetrieveMissingNotificationsAsyncTask(context, type, sinceId, DisplayNotificationsFragment.this).execute();
            }
        });
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        if (context != null)
            asyncTask = new RetrieveNotificationsAsyncTask(context, type, true, null, max_id, DisplayNotificationsFragment.this).execute();
        else
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (context != null)
                        asyncTask = new RetrieveNotificationsAsyncTask(context, type, true, null, max_id, DisplayNotificationsFragment.this).execute();
                }
            }, 500);
        return rootView;
    }

    @Override
    public void onCreate(Bundle saveInstance) {
        super.onCreate(saveInstance);
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (asyncTask != null && asyncTask.getStatus() == AsyncTask.Status.RUNNING)
            asyncTask.cancel(true);
        if (receive_action != null)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receive_action);
        if (receive_data != null)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receive_data);

    }

    @Override
    public void onDestroyView() {
        if (lv_notifications != null) {
            lv_notifications.setAdapter(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        swipeRefreshLayout.setEnabled(true);
        if (context == null)
            return;
        if (getUserVisibleHint() && notifications != null && notifications.size() > 0) {
            retrieveMissingNotifications(notifications.get(0).getId());
            updateNotificationLastId(notifications.get(0).getId());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.clearAnimation();
        }
    }

    @Override
    public void onRetrieveNotifications(APIResponse apiResponse, Account account, boolean refreshData) {
        mainLoader.setVisibility(View.GONE);
        nextElementLoader.setVisibility(View.GONE);
        String lastReadNotifications = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + userId + instance, null);
        if (apiResponse.getError() != null) {

            if (apiResponse.getError().getError().length() < 100) {
                Toasty.error(context, apiResponse.getError().getError(), Toast.LENGTH_LONG).show();
            } else {
                Toasty.error(context, getString(R.string.long_api_error, "\ud83d\ude05"), Toast.LENGTH_LONG).show();
            }
            flag_loading = false;
            swipeRefreshLayout.setRefreshing(false);
            swiped = false;
            return;
        }

        int previousPosition = notifications.size();
        max_id = apiResponse.getMax_id();
        List<Notification> notifications = apiResponse.getNotifications();

        if (!swiped && firstLoad && (notifications == null || notifications.size() == 0))
            textviewNoAction.setVisibility(View.VISIBLE);
        else
            textviewNoAction.setVisibility(View.GONE);
        if (swiped) {
            if (previousPosition > 0) {
                for (int i = 0; i < previousPosition; i++) {
                    this.notifications.remove(0);
                }
                notificationsListAdapter.notifyItemRangeRemoved(0, previousPosition);
            }
            swiped = false;
        }
        if (notifications != null && notifications.size() > 0) {
            if (type == Type.ALL) {
                if (lastReadNotifications != null && notifications.get(0).getId().compareTo(lastReadNotifications) > 0) {
                    countNewNotifications++;
                    SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, MODE_PRIVATE);
                    String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                    String instance = sharedpreferences.getString(Helper.PREF_INSTANCE, Helper.getLiveInstance(context));
                    SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                    Account accountdb = new AccountDAO(context, db).getUniqAccount(userId, instance);
                    LiveNotificationDelayedService.since_ids.put(accountdb.getAcct() + "@" + accountdb.getInstance(), notifications.get(0).getId());
                }
            }
            for (Notification tmpNotification : notifications) {

                if (type == Type.ALL) {
                    if (lastReadNotifications != null && tmpNotification.getId().compareTo(lastReadNotifications) > 0) {
                        countNewNotifications++;
                    }

                    try {
                        ((MainActivity) context).updateNotifCounter();
                    } catch (Exception ignored) {
                    }
                }
                this.notifications.add(tmpNotification);
            }
            if (firstLoad && type == Type.ALL) {
                //Update the id of the last notification retrieved
                if (MainActivity.lastNotificationId == null || notifications.get(0).getId().compareTo(MainActivity.lastNotificationId) > 0) {
                    MainActivity.lastNotificationId = notifications.get(0).getId();
                    updateNotificationLastId(notifications.get(0).getId());
                }
            }
            notificationsListAdapter.notifyItemRangeInserted(previousPosition, notifications.size());
        } else {
            if (firstLoad)
                textviewNoAction.setVisibility(View.VISIBLE);
        }
        if (firstLoad && type == Type.ALL)
            ((MainActivity) context).updateNotifCounter();
        swipeRefreshLayout.setRefreshing(false);
        firstLoad = false;
        //The initial call comes from a classic tab refresh
        flag_loading = (max_id == null);
    }

    /**
     * Called from main activity in onResume to retrieve missing notifications
     *
     * @param sinceId String
     */
    void retrieveMissingNotifications(String sinceId) {
        asyncTask = new RetrieveMissingNotificationsAsyncTask(context, type, sinceId, DisplayNotificationsFragment.this).execute();
    }

    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        if (context == null)
            return;
        //Store last notification id to avoid to notify for those that have been already seen
        if (visible && notifications != null && notifications.size() > 0) {
            retrieveMissingNotifications(notifications.get(0).getId());
            updateNotificationLastId(notifications.get(0).getId());
        }
    }

    public void scrollToTop() {
        if (lv_notifications != null)
            lv_notifications.setAdapter(notificationsListAdapter);
        //Store last toot id for home timeline to avoid to notify for those that have been already seen
        if (this.notifications != null && this.notifications.size() > 0) {
            updateNotificationLastId(this.notifications.get(0).getId());
        }
    }

    public void refreshAll() {
        if (context == null)
            return;
        max_id = null;
        firstLoad = true;
        flag_loading = true;
        swiped = true;
        if (type == Type.ALL) {
            countNewNotifications = 0;
        }
        asyncTask = new RetrieveNotificationsAsyncTask(context, type, true, null, null, DisplayNotificationsFragment.this).execute();
    }

    public void refresh(Notification notification) {
        if (context == null)
            return;
        if (notification != null) {
            //Makes sure the notifications is not already displayed
            if (!this.notifications.contains(notification)) {
                //Update the id of the last notification retrieved

                notifications.add(0, notification);
                if (type == Type.ALL) {
                    MainActivity.lastNotificationId = notification.getId();
                    countNewNotifications++;
                    try {
                        ((MainActivity) context).updateNotifCounter();
                    } catch (Exception ignored) {
                    }
                }
                int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem > 0)
                    notificationsListAdapter.notifyItemInserted(0);
                else
                    notificationsListAdapter.notifyDataSetChanged();
                if (textviewNoAction.getVisibility() == View.VISIBLE)
                    textviewNoAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onRetrieveMissingNotifications(List<Notification> notifications) {
        flag_loading = false;
        swipeRefreshLayout.setRefreshing(false);
        if (this.notifications != null && this.notifications.size() > 0 && swiped) {
            swiped = false;
            notificationsListAdapter.notifyItemRangeChanged(0, this.notifications.size());
        }
        if (notifications != null && notifications.size() > 0) {
            if (type == Type.ALL) {
                //Update the id of the last notification retrieved
                if (MainActivity.lastNotificationId == null || notifications.get(0).getId().compareTo(MainActivity.lastNotificationId) >= 0) {
                    MainActivity.lastNotificationId = notifications.get(0).getId();
                    updateNotificationLastId(notifications.get(0).getId());
                }
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, MODE_PRIVATE);
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                String instance = sharedpreferences.getString(Helper.PREF_INSTANCE, Helper.getLiveInstance(context));
                SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                Account account = new AccountDAO(context, db).getUniqAccount(userId, instance);
                if (MainActivity.lastNotificationId != null && notifications.get(0).getId().compareTo(MainActivity.lastNotificationId) > 0) {
                    LiveNotificationDelayedService.since_ids.put(account.getAcct() + "@" + account.getInstance(), notifications.get(0).getId());
                }
            }
            if (textviewNoAction.getVisibility() == View.VISIBLE) {
                textviewNoAction.setVisibility(View.GONE);
                lv_notifications.setVisibility(View.VISIBLE);
            }
            int inserted = 0;
            for (int i = notifications.size() - 1; i >= 0; i--) {
                if (this.notifications != null && this.notifications.size() == 0 ||
                        notifications.get(i).getId().compareTo(this.notifications.get(0).getId()) > 0) {
                    countNewNotifications++;
                    this.notifications.add(0, notifications.get(i));
                    inserted++;
                }
            }
            notificationsListAdapter.notifyItemRangeInserted(0, inserted);
            try {
                ((MainActivity) context).updateNotifCounter();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Records the id of the notification only if its greater than the previous one.
     *
     * @param notificationId String current notification id to check
     */
    private void updateNotificationLastId(String notificationId) {
        if (type == Type.ALL) {
            String lastNotif = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + userId + instance, null);
            if (lastNotif == null || notificationId.compareTo(lastNotif) >= 0) {
                countNewNotifications = 0;
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(Helper.LAST_NOTIFICATION_MAX_ID + userId + instance, notificationId);
                editor.apply();
            }
        }
    }

    /**
     * Records the id of the notification only if its greater than the previous one.
     */
    public void updateNotificationRead() {
        if (type == Type.ALL) {
            String lastNotif = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + userId + instance, null);
            countNewNotifications = 0;
            if (this.notifications != null && this.notifications.size() > 0 && this.notifications.get(0).getId().compareTo(lastNotif) > 0) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(Helper.LAST_NOTIFICATION_MAX_ID + userId + instance, this.notifications.get(0).getId());
                editor.apply();
            }
        }
    }

    public enum Type {
        ALL,
        MENTION,
        FAVORITE,
        BOOST,
        POLL,
        FOLLOW
    }
}
