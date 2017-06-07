package fr.gouv.etalab.mastodon.drawers;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastodon Etalab for mastodon.etalab.gouv.fr
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastodon Etalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Thomas Schneider; if not,
 * see <http://www.gnu.org/licenses>. */


import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;

import java.util.ArrayList;
import java.util.List;

import fr.gouv.etalab.mastodon.asynctasks.PostActionAsyncTask;
import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnPostActionInterface;
import mastodon.etalab.gouv.fr.mastodon.R;


/**
 * Created by Thomas on 07/05/2017.
 * Adapter for accounts asking a follow request
 */
public class AccountsFollowRequestAdapter extends BaseAdapter implements OnPostActionInterface {

    private List<Account> accounts;
    private LayoutInflater layoutInflater;
    private ImageLoader imageLoader;
    private DisplayImageOptions options;
    private Context context;
    private AccountsFollowRequestAdapter accountsFollowRequestAdapter;

    public AccountsFollowRequestAdapter(Context context, List<Account> accounts){
        this.accounts = accounts;
        layoutInflater = LayoutInflater.from(context);
        imageLoader = ImageLoader.getInstance();
        this.context = context;
        options = new DisplayImageOptions.Builder().displayer(new SimpleBitmapDisplayer()).cacheInMemory(false)
                .cacheOnDisk(true).resetViewBeforeLoading(true).build();
        accountsFollowRequestAdapter = this;
    }

    @Override
    public int getCount() {
        return accounts.size();
    }

    @Override
    public Object getItem(int position) {
        return accounts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        final Account account = accounts.get(position);
        final ViewHolder holder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.drawer_account_follow_request, parent, false);
            holder = new ViewHolder();
            holder.account_pp = (ImageView) convertView.findViewById(R.id.account_pp);
            holder.account_un = (TextView) convertView.findViewById(R.id.account_un);
            holder.btn_authorize = (Button) convertView.findViewById(R.id.btn_authorize);
            holder.btn_reject = (Button) convertView.findViewById(R.id.btn_reject);

            holder.account_container = (LinearLayout) convertView.findViewById(R.id.account_container);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.account_un.setText(String.format("@%s", account.getUsername()));
        //Profile picture
        imageLoader.displayImage(account.getAvatar(), holder.account_pp, options);

        holder.btn_authorize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new PostActionAsyncTask(context, API.StatusAction.AUTHORIZE, account.getId(), AccountsFollowRequestAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        holder.btn_reject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new PostActionAsyncTask(context, API.StatusAction.REJECT, account.getId(), AccountsFollowRequestAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        return convertView;
    }

    @Override
    public void onPostAction(int statusCode, API.StatusAction statusAction, String userId, Error error) {
        if( error != null){
            final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            boolean show_error_messages = sharedpreferences.getBoolean(Helper.SET_SHOW_ERROR_MESSAGES, true);
            if( show_error_messages)
                Toast.makeText(context, error.getError(),Toast.LENGTH_LONG).show();
            return;
        }
        Helper.manageMessageStatusCode(context, statusCode, statusAction);
        //When authorizing or rejecting an account, this account is removed from the list
        List<Account> accountToRemove = new ArrayList<>();
        if( statusAction == API.StatusAction.AUTHORIZE || statusAction == API.StatusAction.REJECT){
            for(Account account: accounts){
                if( account.getId().equals(userId))
                    accountToRemove.add(account);
            }
            accounts.removeAll(accountToRemove);
            accountsFollowRequestAdapter.notifyDataSetChanged();
        }
    }


    private class ViewHolder {
        ImageView account_pp;
        Button btn_authorize;
        Button btn_reject;
        TextView account_un;
        LinearLayout account_container;
    }


}