package app.fedilab.android.client;
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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.fedilab.android.R;
import app.fedilab.android.client.Entities.Account;
import app.fedilab.android.client.Entities.AccountCreation;
import app.fedilab.android.client.Entities.Attachment;
import app.fedilab.android.client.Entities.Conversation;
import app.fedilab.android.client.Entities.Emojis;
import app.fedilab.android.client.Entities.Error;
import app.fedilab.android.client.Entities.Filters;
import app.fedilab.android.client.Entities.HowToVideo;
import app.fedilab.android.client.Entities.Instance;
import app.fedilab.android.client.Entities.InstanceNodeInfo;
import app.fedilab.android.client.Entities.InstanceReg;
import app.fedilab.android.client.Entities.Peertube;
import app.fedilab.android.client.Entities.PeertubeAccountNotification;
import app.fedilab.android.client.Entities.PeertubeActorFollow;
import app.fedilab.android.client.Entities.PeertubeComment;
import app.fedilab.android.client.Entities.PeertubeInformation;
import app.fedilab.android.client.Entities.PeertubeNotification;
import app.fedilab.android.client.Entities.PeertubeVideoNotification;
import app.fedilab.android.client.Entities.Playlist;
import app.fedilab.android.client.Entities.Relationship;
import app.fedilab.android.client.Entities.Results;
import app.fedilab.android.client.Entities.Status;
import app.fedilab.android.helper.Helper;
import app.fedilab.android.sqlite.AccountDAO;
import app.fedilab.android.sqlite.Sqlite;


/**
 * Created by Thomas on 02/01/2019.
 * Manage Calls to the Peertube REST API
 */

public class PeertubeAPI {


    private Account account;
    private Context context;
    private Results results;
    private Attachment attachment;
    private List<Account> accounts;
    private List<Status> statuses;
    private List<Conversation> conversations;
    private int tootPerPage, accountPerPage, notificationPerPage;
    private int actionCode;
    private String instance;
    private String prefKeyOauthTokenT;
    private APIResponse apiResponse;
    private Error APIError;
    private List<String> domains;


    public PeertubeAPI(Context context) {
        this.context = context;
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        tootPerPage = Helper.TOOTS_PER_PAGE;
        accountPerPage = Helper.ACCOUNTS_PER_PAGE;
        notificationPerPage = Helper.NOTIFICATIONS_PER_PAGE;
        if (Helper.getLiveInstance(context) != null)
            this.instance = Helper.getLiveInstance(context);
        else {
            SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
            String instance = sharedpreferences.getString(Helper.PREF_INSTANCE, Helper.getLiveInstance(context));
            Account account = new AccountDAO(context, db).getUniqAccount(userId, instance);
            if (account == null) {
                apiResponse = new APIResponse();
                APIError = new Error();
                return;
            }
            this.instance = account.getInstance().trim();
        }
        this.prefKeyOauthTokenT = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
        apiResponse = new APIResponse();
        APIError = null;
    }

    public PeertubeAPI(Context context, String instance, String token) {
        this.context = context;
        if (context == null) {
            apiResponse = new APIResponse();
            APIError = new Error();
            return;
        }
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        tootPerPage = Helper.TOOTS_PER_PAGE;
        accountPerPage = Helper.ACCOUNTS_PER_PAGE;
        notificationPerPage = Helper.NOTIFICATIONS_PER_PAGE;
        if (instance != null)
            this.instance = instance;
        else
            this.instance = Helper.getLiveInstance(context);

        if (token != null)
            this.prefKeyOauthTokenT = token;
        else
            this.prefKeyOauthTokenT = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
        apiResponse = new APIResponse();
        APIError = null;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    private static PeertubeNotification parsePeertubeNotifications(Context context, JSONObject resobj) {
        PeertubeNotification peertubeNotification = new PeertubeNotification();
        try {
            peertubeNotification.setId(resobj.get("id").toString());
            peertubeNotification.setType(resobj.getInt("type"));
            peertubeNotification.setUpdatedAt(Helper.mstStringToDate(context, resobj.get("updatedAt").toString()));
            peertubeNotification.setCreatedAt(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            peertubeNotification.setRead(resobj.getBoolean("read"));

            if (resobj.has("comment")) {
                PeertubeComment peertubeComment = new PeertubeComment();
                JSONObject comment = resobj.getJSONObject("comment");
                if (comment.has("account")) {
                    JSONObject account = comment.getJSONObject("account");
                    PeertubeAccountNotification peertubeAccountNotification = new PeertubeAccountNotification();
                    peertubeAccountNotification.setDisplayName(account.get("displayName").toString());
                    peertubeAccountNotification.setName(account.get("name").toString());
                    peertubeAccountNotification.setId(account.get("id").toString());
                    if (account.has("host")) {
                        peertubeAccountNotification.setHost(account.get("host").toString());
                    }
                    peertubeAccountNotification.setAvatar(account.getJSONObject("avatar").get("path").toString());
                    peertubeComment.setPeertubeAccountNotification(peertubeAccountNotification);
                }
                if (comment.has("video")) {
                    JSONObject video = comment.getJSONObject("video");
                    PeertubeVideoNotification peertubeVideoNotification = new PeertubeVideoNotification();
                    peertubeVideoNotification.setUuid(video.get("uuid").toString());
                    peertubeVideoNotification.setName(video.get("name").toString());
                    peertubeVideoNotification.setId(video.get("id").toString());
                    peertubeComment.setPeertubeVideoNotification(peertubeVideoNotification);
                }
                peertubeComment.setId(comment.get("id").toString());
                peertubeComment.setThreadId(comment.get("threadId").toString());
                peertubeNotification.setPeertubeComment(peertubeComment);
            }

            if (resobj.has("video")) {
                PeertubeVideoNotification peertubeVideoNotification = new PeertubeVideoNotification();
                JSONObject video = resobj.getJSONObject("video");
                peertubeVideoNotification.setUuid(video.get("uuid").toString());
                peertubeVideoNotification.setName(video.get("name").toString());
                peertubeVideoNotification.setId(video.get("id").toString());
                if (video.has("channel")) {
                    PeertubeAccountNotification peertubeAccountNotification = new PeertubeAccountNotification();
                    JSONObject channel = video.getJSONObject("channel");
                    peertubeAccountNotification.setDisplayName(channel.get("displayName").toString());
                    peertubeAccountNotification.setName(channel.get("name").toString());
                    peertubeAccountNotification.setId(channel.get("id").toString());
                    if (channel.has("avatar")) {
                        peertubeAccountNotification.setAvatar(channel.getJSONObject("avatar").get("path").toString());
                    }
                    peertubeVideoNotification.setPeertubeAccountNotification(peertubeAccountNotification);
                }
                peertubeNotification.setPeertubeVideoNotification(peertubeVideoNotification);
            }

            if (resobj.has("actorFollow")) {
                PeertubeActorFollow peertubeActorFollow = new PeertubeActorFollow();
                JSONObject actorFollow = resobj.getJSONObject("actorFollow");

                JSONObject follower = actorFollow.getJSONObject("follower");
                JSONObject following = actorFollow.getJSONObject("following");

                PeertubeAccountNotification peertubeAccountNotification = new PeertubeAccountNotification();
                peertubeAccountNotification.setDisplayName(follower.get("displayName").toString());
                peertubeAccountNotification.setName(follower.get("name").toString());
                peertubeAccountNotification.setId(follower.get("id").toString());
                if (follower.has("host")) {
                    peertubeAccountNotification.setHost(follower.get("host").toString());
                }
                if (follower.has("avatar")) {
                    peertubeAccountNotification.setAvatar(follower.getJSONObject("avatar").get("path").toString());
                }
                peertubeActorFollow.setFollower(peertubeAccountNotification);

                PeertubeAccountNotification peertubeAccounFollowingNotification = new PeertubeAccountNotification();
                peertubeAccounFollowingNotification.setDisplayName(following.get("displayName").toString());
                peertubeAccounFollowingNotification.setName(following.get("name").toString());
                try {
                    peertubeAccounFollowingNotification.setId(following.get("id").toString());
                } catch (Exception ignored) {
                }
                if (following.has("avatar")) {
                    peertubeAccounFollowingNotification.setAvatar(following.getJSONObject("avatar").get("path").toString());
                }
                peertubeActorFollow.setFollowing(peertubeAccounFollowingNotification);
                peertubeActorFollow.setId(actorFollow.get("id").toString());
                peertubeNotification.setPeertubeActorFollow(peertubeActorFollow);

            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return peertubeNotification;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    public static Peertube parsePeertube(Context context, JSONObject resobj) {
        Peertube peertube = new Peertube();
        if (resobj.has("video")) {
            try {
                resobj = resobj.getJSONObject("video");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            peertube.setId(resobj.get("id").toString());
            peertube.setCache(resobj);
            peertube.setUuid(resobj.get("uuid").toString());
            peertube.setName(resobj.get("name").toString());
            peertube.setDescription(resobj.get("description").toString());
            peertube.setEmbedPath(resobj.get("embedPath").toString());
            peertube.setPreviewPath(resobj.get("previewPath").toString());
            peertube.setThumbnailPath(resobj.get("thumbnailPath").toString());
            peertube.setAccount(parseAccountResponsePeertube(context, resobj.getJSONObject("account")));
            try {
                peertube.setChannel(parseAccountResponsePeertube(context, resobj.getJSONObject("channel")));
            } catch (Exception ignored) {
            }
            peertube.setView(Integer.parseInt(resobj.get("views").toString()));
            peertube.setLike(Integer.parseInt(resobj.get("likes").toString()));
            peertube.setDislike(Integer.parseInt(resobj.get("dislikes").toString()));
            peertube.setDuration(Integer.parseInt(resobj.get("duration").toString()));
            peertube.setSensitive(Boolean.parseBoolean(resobj.get("nsfw").toString()));
            try {
                peertube.setCommentsEnabled(Boolean.parseBoolean(resobj.get("commentsEnabled").toString()));
            } catch (Exception ignored) {
            }

            try {
                peertube.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }

            try {
                LinkedHashMap<String, String> langue = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> category = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> license = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> privacy = new LinkedHashMap<>();
                category.put(resobj.getJSONObject("category").getInt("id"), resobj.getJSONObject("category").get("label").toString());
                license.put(resobj.getJSONObject("licence").getInt("id"), resobj.getJSONObject("licence").get("label").toString());
                privacy.put(resobj.getJSONObject("privacy").getInt("id"), resobj.getJSONObject("privacy").get("label").toString());
                langue.put(resobj.getJSONObject("language").get("id").toString(), resobj.getJSONObject("language").get("label").toString());

                peertube.setCategory(category);
                peertube.setLicense(license);
                peertube.setLanguage(langue);
                peertube.setPrivacy(privacy);
            } catch (Exception ignored) {
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return peertube;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    private static Peertube parseSinglePeertube(Context context, String instance, JSONObject resobj) {
        Peertube peertube = new Peertube();
        try {
            peertube.setId(resobj.get("id").toString());
            peertube.setUuid(resobj.get("uuid").toString());
            peertube.setName(resobj.get("name").toString());
            peertube.setCache(resobj);
            peertube.setInstance(instance);
            peertube.setHost(resobj.getJSONObject("account").get("host").toString());
            peertube.setDescription(resobj.get("description").toString());
            peertube.setEmbedPath(resobj.get("embedPath").toString());
            peertube.setPreviewPath(resobj.get("previewPath").toString());
            peertube.setThumbnailPath(resobj.get("thumbnailPath").toString());
            peertube.setView(Integer.parseInt(resobj.get("views").toString()));
            peertube.setLike(Integer.parseInt(resobj.get("likes").toString()));
            peertube.setCommentsEnabled(Boolean.parseBoolean(resobj.get("commentsEnabled").toString()));
            peertube.setDislike(Integer.parseInt(resobj.get("dislikes").toString()));
            peertube.setDuration(Integer.parseInt(resobj.get("duration").toString()));
            peertube.setAccount(parseAccountResponsePeertube(context, resobj.getJSONObject("account")));
            List<String> tags = new ArrayList<>();
            try {
                JSONArray tagsA = resobj.getJSONArray("tags");
                for (int i = 0; i < tagsA.length(); i++) {
                    String value = tagsA.getString(i);
                    tags.add(value);
                }
                peertube.setTags(tags);
            } catch (Exception ignored) {
            }
            try {
                peertube.setChannel(parseAccountResponsePeertube(context, resobj.getJSONObject("channel")));
            } catch (Exception ignored) {
            }
            peertube.setSensitive(Boolean.parseBoolean(resobj.get("nsfw").toString()));
            try {
                peertube.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }

            try {
                peertube.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            JSONArray files = resobj.getJSONArray("files");
            ArrayList<String> resolutions = new ArrayList<>();
            for (int j = 0; j < files.length(); j++) {
                JSONObject attObj = files.getJSONObject(j);
                resolutions.add(attObj.getJSONObject("resolution").get("id").toString());
            }
            try {
                LinkedHashMap<String, String> langue = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> category = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> license = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> privacy = new LinkedHashMap<>();
                category.put(resobj.getJSONObject("category").getInt("id"), resobj.getJSONObject("category").get("label").toString());
                license.put(resobj.getJSONObject("licence").getInt("id"), resobj.getJSONObject("licence").get("label").toString());
                privacy.put(resobj.getJSONObject("privacy").getInt("id"), resobj.getJSONObject("privacy").get("label").toString());
                langue.put(resobj.getJSONObject("language").get("id").toString(), resobj.getJSONObject("language").get("label").toString());

                peertube.setCategory(category);
                peertube.setLicense(license);
                peertube.setLanguage(langue);
                peertube.setPrivacy(privacy);
            } catch (Exception ignored) {
            }
            peertube.setResolution(resolutions);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return peertube;
    }

    /**
     * Parse json response for peertube comments
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    private static List<Status> parseSinglePeertubeComments(Context context, String instance, JSONObject resobj) {
        List<Status> statuses = new ArrayList<>();
        try {
            JSONArray jsonArray = resobj.getJSONArray("data");
            int i = 0;
            while (i < jsonArray.length()) {
                Status status = new Status();
                JSONObject comment = jsonArray.getJSONObject(i);
                status.setId(comment.get("id").toString());
                status.setUri(comment.get("url").toString());
                status.setUrl(comment.get("url").toString());
                status.setSensitive(false);
                status.setSpoiler_text("");
                status.setContent(context, comment.get("text").toString());
                status.setIn_reply_to_id(comment.get("inReplyToCommentId").toString());
                status.setAccount(parseAccountResponsePeertube(context, comment.getJSONObject("account")));
                status.setCreated_at(Helper.mstStringToDate(context, comment.get("createdAt").toString()));
                status.setMentions(new ArrayList<>());
                status.setEmojis(new ArrayList<>());
                status.setMedia_attachments(new ArrayList<>());
                status.setVisibility("public");
                i++;
                statuses.add(status);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return statuses;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return HowToVideo
     */
    private static HowToVideo parseHowTo(Context context, JSONObject resobj) {
        HowToVideo howToVideo = new HowToVideo();
        try {
            howToVideo.setId(resobj.get("id").toString());
            howToVideo.setUuid(resobj.get("uuid").toString());
            howToVideo.setName(resobj.get("name").toString());
            howToVideo.setDescription(resobj.get("description").toString());
            howToVideo.setEmbedPath(resobj.get("embedPath").toString());
            howToVideo.setPreviewPath(resobj.get("previewPath").toString());
            howToVideo.setThumbnailPath(resobj.get("thumbnailPath").toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return howToVideo;
    }

    /**
     * Parse json response for emoji
     *
     * @param resobj JSONObject
     * @return Emojis
     */
    private static Emojis parseEmojis(JSONObject resobj) {
        Emojis emojis = new Emojis();
        try {
            emojis.setShortcode(resobj.get("shortcode").toString());
            emojis.setStatic_url(resobj.get("static_url").toString());
            emojis.setUrl(resobj.get("url").toString());
        } catch (Exception ignored) {
        }
        return emojis;
    }

    /**
     * Parse json response for emoji
     *
     * @param resobj JSONObject
     * @return Emojis
     */
    private static Emojis parseMisskeyEmojis(JSONObject resobj) {
        Emojis emojis = new Emojis();
        try {
            emojis.setShortcode(resobj.get("name").toString());
            emojis.setStatic_url(resobj.get("url").toString());
            emojis.setUrl(resobj.get("url").toString());
        } catch (Exception ignored) {
        }
        return emojis;
    }

    /**
     * Parse json response for emoji
     *
     * @param resobj JSONObject
     * @return Emojis
     */
    private static Playlist parsePlaylist(Context context, JSONObject resobj) {
        Playlist playlist = new Playlist();
        try {
            playlist.setId(resobj.getString("id"));
            playlist.setUuid(resobj.getString("uuid"));
            playlist.setCreatedAt(Helper.stringToDate(context, resobj.getString("createdAt")));
            playlist.setDescription(resobj.getString("description"));
            playlist.setDisplayName(resobj.getString("displayName"));
            playlist.setLocal(resobj.getBoolean("isLocal"));
            playlist.setVideoChannelId(resobj.getString("videoChannel"));
            playlist.setThumbnailPath(resobj.getString("thumbnailPath"));
            playlist.setOwnerAccount(parseAccountResponsePeertube(context, resobj.getJSONObject("ownerAccount")));
            playlist.setVideosLength(resobj.getInt("videosLength"));
            try {
                LinkedHashMap<Integer, String> type = new LinkedHashMap<>();
                LinkedHashMap<Integer, String> privacy = new LinkedHashMap<>();
                privacy.put(resobj.getJSONObject("privacy").getInt("id"), resobj.getJSONObject("privacy").get("label").toString());
                type.put(resobj.getJSONObject("type").getInt("id"), resobj.getJSONObject("type").get("label").toString());
                playlist.setType(type);
                playlist.setPrivacy(privacy);
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }


            try {
                playlist.setUpdatedAt(Helper.stringToDate(context, resobj.getString("updatedAt")));
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return playlist;
    }

    /**
     * Parse json response an unique peertube account
     *
     * @param accountObject JSONObject
     * @return Account
     */
    private static Account parseAccountResponsePeertube(Context context, JSONObject accountObject) {
        Account account = new Account();
        try {
            account.setId(accountObject.get("id").toString());
            account.setUuid(accountObject.get("id").toString());
            account.setUsername(accountObject.get("name").toString());
            account.setAcct(accountObject.get("name").toString() + "@" + accountObject.get("host"));
            account.setDisplay_name(accountObject.get("name").toString());
            account.setHost(accountObject.get("host").toString());
            account.setSocial("PEERTUBE");

            if (accountObject.has("createdAt"))
                account.setCreated_at(Helper.mstStringToDate(context, accountObject.get("createdAt").toString()));
            else
                account.setCreated_at(new Date());
            if (accountObject.has("followersCount"))
                account.setFollowers_count(Integer.valueOf(accountObject.get("followersCount").toString()));
            else
                account.setFollowers_count(0);
            if (accountObject.has("followingCount"))
                account.setFollowing_count(Integer.valueOf(accountObject.get("followingCount").toString()));
            else
                account.setFollowing_count(0);
            account.setStatuses_count(0);
            if (accountObject.has("description"))
                account.setNote(accountObject.get("description").toString());
            else
                account.setNote("");

            account.setUrl(accountObject.get("url").toString());
            if (accountObject.has("avatar") && !accountObject.isNull("avatar")) {
                account.setAvatar(accountObject.getJSONObject("avatar").get("path").toString());
            } else
                account.setAvatar("null");
            account.setHeader("null");
            account.setHeader_static("null");
            account.setAvatar_static(accountObject.get("avatar").toString());
        } catch (JSONException ignored) {
            ignored.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return account;
    }

    /**
     * Parse json response an unique attachment
     *
     * @param resobj JSONObject
     * @return Relationship
     */
    static Attachment parseAttachmentResponse(JSONObject resobj) {

        Attachment attachment = new Attachment();
        try {
            attachment.setId(resobj.get("id").toString());
            attachment.setType(resobj.get("type").toString());
            attachment.setUrl(resobj.get("url").toString());
            try {
                attachment.setDescription(resobj.get("description").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setRemote_url(resobj.get("remote_url").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setPreview_url(resobj.get("preview_url").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setMeta(resobj.get("meta").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setText_url(resobj.get("text_url").toString());
            } catch (JSONException ignore) {
            }

        } catch (JSONException ignored) {
        }
        return attachment;
    }

    /***
     * Get info on the current Instance *synchronously*
     * @return APIResponse
     */
    public APIResponse getInstance() {
        try {
            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/instance"), 30, null, prefKeyOauthTokenT);
            Instance instanceEntity = parseInstance(new JSONObject(response));
            apiResponse.setInstance(instanceEntity);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }

    /**
     * Update video meta data *synchronously*
     *
     * @param peertube Peertube
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse updateVideo(Peertube peertube) {

        LinkedHashMap<String, String> params = new LinkedHashMap<>();

        params.put("name", peertube.getName());
        //Category
        Map.Entry<Integer, String> categoryM = peertube.getCategory().entrySet().iterator().next();
        Integer idCategory = categoryM.getKey();
        params.put("category", String.valueOf(idCategory));
        //License
        Map.Entry<Integer, String> licenseM = peertube.getLicense().entrySet().iterator().next();
        Integer idLicense = licenseM.getKey();
        params.put("licence", String.valueOf(idLicense));
        //language
        Map.Entry<String, String> languagesM = peertube.getLanguage().entrySet().iterator().next();
        String iDlanguage = languagesM.getKey();
        params.put("language", iDlanguage);
        params.put("support", "null");
        params.put("description", peertube.getDescription());
        //Channel
        Map.Entry<String, String> channelsM = peertube.getChannelForUpdate().entrySet().iterator().next();
        String iDChannel = channelsM.getValue();
        params.put("channelId", iDChannel);
        //Privacy
        Map.Entry<Integer, String> privacyM = peertube.getPrivacy().entrySet().iterator().next();
        Integer idPrivacy = privacyM.getKey();
        params.put("privacy", String.valueOf(idPrivacy));
        if (peertube.getTags() != null && peertube.getTags().size() > 0) {
            StringBuilder parameters = new StringBuilder();
            parameters.append("[]&");
            for (String tag : peertube.getTags())
                parameters.append("tags=").append(tag).append("&");
            String strParam = parameters.toString();
            strParam = strParam.substring(0, strParam.length() - 1);
            params.put("tags[]", strParam);
        } else {
            params.put("tags", "null");
        }
        params.put("nsfw", String.valueOf(peertube.isSensitive()));
        params.put("waitTranscoding", "true");
        params.put("commentsEnabled", String.valueOf(peertube.isCommentsEnabled()));
        params.put("scheduleUpdate", "null");
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            httpsConnection.put(getAbsoluteUrl(String.format("/videos/%s", peertube.getId())), 60, params, prefKeyOauthTokenT);
            peertubes.add(peertube);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /***
     * Verifiy PeertubeInformation of the authenticated user *synchronously*
     * @return Account
     */
    public PeertubeInformation getPeertubeInformation() throws HttpsConnection.HttpsConnectionException {
        PeertubeInformation peertubeInformation = new PeertubeInformation();
        try {

            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/videos/categories"), 60, null, null);
            JSONObject categories = new JSONObject(response);
            LinkedHashMap<Integer, String> _pcategories = new LinkedHashMap<>();
            for (int i = 1; i <= categories.length(); i++) {
                _pcategories.put(i, categories.getString(String.valueOf(i)));

            }
            peertubeInformation.setCategories(_pcategories);

            response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/videos/languages"), 60, null, null);
            JSONObject languages = new JSONObject(response);
            LinkedHashMap<String, String> _languages = new LinkedHashMap<>();
            Iterator<String> iter = languages.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    _languages.put(key, (String) languages.get(key));
                } catch (JSONException ignored) {
                }
            }
            peertubeInformation.setLanguages(_languages);

            response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/videos/privacies"), 60, null, null);
            JSONObject privacies = new JSONObject(response);
            LinkedHashMap<Integer, String> _pprivacies = new LinkedHashMap<>();
            for (int i = 1; i <= privacies.length(); i++) {
                _pprivacies.put(i, privacies.getString(String.valueOf(i)));

            }
            peertubeInformation.setPrivacies(_pprivacies);


            response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/video-playlists/privacies"), 60, null, null);
            JSONObject plprivacies = new JSONObject(response);
            LinkedHashMap<Integer, String> _plprivacies = new LinkedHashMap<>();
            for (int i = 1; i <= plprivacies.length(); i++) {
                _plprivacies.put(i, plprivacies.getString(String.valueOf(i)));

            }
            peertubeInformation.setPlaylistPrivacies(_plprivacies);

            response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/videos/licences"), 60, null, null);
            JSONObject licences = new JSONObject(response);
            LinkedHashMap<Integer, String> _plicences = new LinkedHashMap<>();
            for (int i = 1; i <= licences.length(); i++) {
                _plicences.put(i, licences.getString(String.valueOf(i)));

            }
            peertubeInformation.setLicences(_plicences);


            String instance = Helper.getLiveInstance(context);
            String lang = null;
            if (PeertubeInformation.langueMapped.containsKey(Locale.getDefault().getLanguage()))
                lang = PeertubeInformation.langueMapped.get(Locale.getDefault().getLanguage());

            if (lang != null && !lang.startsWith("en")) {
                response = new HttpsConnection(context, this.instance).get(String.format("https://" + instance + "/client/locales/%s/server.json", lang), 60, null, null);
                JSONObject translations = new JSONObject(response);
                LinkedHashMap<String, String> _translations = new LinkedHashMap<>();
                Iterator<String> itertrans = translations.keys();
                while (itertrans.hasNext()) {
                    String key = itertrans.next();
                    try {
                        _translations.put(key, (String) translations.get(key));
                    } catch (JSONException ignored) {
                    }
                }
                peertubeInformation.setTranslations(_translations);
            }

        } catch (NoSuchAlgorithmException | IOException | KeyManagementException | JSONException e) {
            e.printStackTrace();
        }
        return peertubeInformation;
    }

    /***
     * Verifiy credential of the authenticated user *synchronously*
     * @return Account
     */
    public Account verifyCredentials() {
        account = new Account();
        InstanceNodeInfo nodeinfo = new API(context).displayNodeInfo(instance);
        String social = null;
        if (nodeinfo != null) {
            social = nodeinfo.getName();
        }
        try {
            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/users/me"), 60, null, prefKeyOauthTokenT);
            JSONObject accountObject = new JSONObject(response).getJSONObject("account");
            account = parseAccountResponsePeertube(context, accountObject);
            if (social != null) {
                account.setSocial(social.toUpperCase());
            }
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException | JSONException e) {
            e.printStackTrace();
        } catch (HttpsConnection.HttpsConnectionException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                Account targetedAccount = new AccountDAO(context, db).getAccountByToken(prefKeyOauthTokenT);
                if (targetedAccount != null) {
                    HashMap<String, String> values = refreshToken(targetedAccount.getClient_id(), targetedAccount.getClient_secret(), targetedAccount.getRefresh_token());
                    if (values.containsKey("access_token") && values.get("access_token") != null) {
                        targetedAccount.setToken(values.get("access_token"));
                        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                        String token = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
                        //This account is currently logged in, the token is updated
                        if (prefKeyOauthTokenT != null && prefKeyOauthTokenT.equals(token)) {
                            SharedPreferences.Editor editor = sharedpreferences.edit();
                            editor.putString(Helper.PREF_KEY_OAUTH_TOKEN, targetedAccount.getToken());
                            editor.apply();
                        }
                    }
                    if (values.containsKey("refresh_token") && values.get("refresh_token") != null)
                        targetedAccount.setRefresh_token(values.get("refresh_token"));
                    new AccountDAO(context, db).updateAccount(targetedAccount);

                    String response;
                    try {
                        response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/users/me"), 60, null, targetedAccount.getToken());
                        JSONObject accountObject = new JSONObject(response).getJSONObject("account");
                        account = parseAccountResponsePeertube(context, accountObject);
                        if (social != null) {
                            account.setSocial(social.toUpperCase());
                        }
                    } catch (IOException | NoSuchAlgorithmException | KeyManagementException | JSONException e1) {
                        e1.printStackTrace();
                    } catch (HttpsConnection.HttpsConnectionException e1) {
                        e1.printStackTrace();
                        setError(e.getStatusCode(), e);
                    }
                }else{
                    setError(e.getStatusCode(), e);
                }
                e.printStackTrace();
            }
        }
        return account;
    }

    /***
     * Get instance for registering an account *synchronously*
     * @return APIResponse
     */
    public APIResponse getInstanceReg() {
        apiResponse = new APIResponse();
        try {
            String response = new HttpsConnection(context, null).get("https://instances.joinpeertube.org/api/v1/instances?start=0&count=50&signup=true&health=100&sort=-totalUsers");
            JSONObject result = new JSONObject(response);
            List<InstanceReg> instanceRegs = parseInstanceReg(result.getJSONArray("data"));
            apiResponse.setInstanceRegs(instanceRegs);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException | JSONException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }

    public APIResponse createAccount(AccountCreation accountCreation) {
        apiResponse = new APIResponse();

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("username", accountCreation.getUsername());
            params.put("email", accountCreation.getEmail());
            params.put("password", accountCreation.getPassword());
            String response = new HttpsConnection(context, this.instance).post(getAbsoluteUrl("/users/register"), 30, params, null);

        } catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
            e.printStackTrace();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        }
        return apiResponse;
    }

    /***
     * Verifiy credential of the authenticated user *synchronously*
     * @return Account
     */
    private HashMap<String, String> refreshToken(String client_id, String client_secret, String refresh_token) {
        account = new Account();
        HashMap<String, String> params = new HashMap<>();
        HashMap<String, String> newValues = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("client_id", client_id);
        params.put("client_secret", client_secret);
        params.put("refresh_token", refresh_token);
        try {
            String response = new HttpsConnection(context, this.instance).post(getAbsoluteUrl("/users/token"), 60, params, null);
            JSONObject resobj = new JSONObject(response);
            String token = resobj.get("access_token").toString();
            if (resobj.has("refresh_token"))
                refresh_token = resobj.get("refresh_token").toString();
            newValues.put("access_token", token);
            newValues.put("refresh_token", refresh_token);
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException | JSONException | HttpsConnection.HttpsConnectionException e) {
            e.printStackTrace();
        }
        return newValues;
    }

    /**
     * Returns an account
     *
     * @param accountId String account fetched
     * @return Account entity
     */
    public Account getAccount(String accountId) {

        account = new Account();
        try {
            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl(String.format("/accounts/%s", accountId)), 60, null, prefKeyOauthTokenT);
            account = parseAccountResponsePeertube(context, new JSONObject(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            e.printStackTrace();
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return account;
    }

    /**
     * Returns a relationship between the authenticated account and an account
     *
     * @param uri String accounts fetched
     * @return Relationship entity
     */
    public boolean isFollowing(String uri) {
        HashMap<String, String> params = new HashMap<>();

        params.put("uris", uri);

        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl("/users/me/subscriptions/exist"), 60, params, prefKeyOauthTokenT);
            return new JSONObject(response).getBoolean(uri);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Retrieves videos for the account *synchronously*
     *
     * @param acct   String Id of the account
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getVideos(String acct, String max_id) {
        return getVideos(acct, max_id, null);
    }

    /**
     * Retrieves history for videos for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getMyHistory(String max_id) {
        return getMyHistory(max_id, null);
    }

    /**
     * Retrieves history for videos for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getMyHistory(String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        List<Peertube> peertubes = new ArrayList<>();
        try {

            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl("/users/me/history/videos"), 60, params, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves videos for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getMyVideos(String max_id) {
        return getMyVideos(max_id, null);
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getMyVideos(String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        List<Peertube> peertubes = new ArrayList<>();
        try {

            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl("/users/me/videos"), 60, params, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param acct     String Id of the account
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getVideos(String acct, String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(String.format("/accounts/%s/videos", acct)), 60, params, prefKeyOauthTokenT);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube notifications for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getNotifications(String max_id) {
        return getNotifications(max_id, null);
    }

    /**
     * Retrieves Peertube notifications since id for the account *synchronously*
     *
     * @param since_id String id since
     * @return APIResponse
     */
    public APIResponse getNotificationsSince(String since_id) {
        return getNotifications(null, since_id);
    }

    /**
     * Retrieves Peertube notifications for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getNotifications(String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        List<PeertubeNotification> peertubeNotifications = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl("/users/me/notifications"), 60, params, prefKeyOauthTokenT);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubeNotifications = parsePeertubeNotifications(jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubeNotifications(peertubeNotifications);
        return apiResponse;
    }

    /**
     * Retrieves videos channel for the account *synchronously*
     *
     * @param acct   String Id of the account
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getVideosChannel(String acct, String max_id) {
        return getVideosChannel(acct, max_id, null);
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param acct     String Id of the account
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getVideosChannel(String acct, String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        List<Peertube> peertubes = new ArrayList<>();
        try {

            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(String.format("/video-channels/%s/videos", acct)), 60, params, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves subscription videos *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getSubscriptionsTL(String max_id) {
        try {
            return getTL("/users/me/subscriptions/videos", "-publishedAt", null, max_id, null, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                Account targetedAccount = new AccountDAO(context, db).getAccountByToken(prefKeyOauthTokenT);
                HashMap<String, String> values = refreshToken(targetedAccount.getClient_id(), targetedAccount.getClient_secret(), targetedAccount.getRefresh_token());
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                if (values.containsKey("access_token") && values.get("access_token") != null) {
                    targetedAccount.setToken(values.get("access_token"));
                    String token = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
                    //This account is currently logged in, the token is updated
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    prefKeyOauthTokenT = targetedAccount.getToken();
                    editor.putString(Helper.PREF_KEY_OAUTH_TOKEN, targetedAccount.getToken());
                    editor.apply();
                }
                if (values.containsKey("refresh_token") && values.get("refresh_token") != null)
                    targetedAccount.setRefresh_token(values.get("refresh_token"));
                new AccountDAO(context, db).updateAccount(targetedAccount);
                try {
                    return getTL("/users/me/subscriptions/videos", "-publishedAt", null, max_id, null, null);
                } catch (HttpsConnection.HttpsConnectionException e1) {
                    setError(e.getStatusCode(), e);
                    return apiResponse;
                }
            }
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves overview videos *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getOverviewTL(String max_id) {
        try {
            return getTL("/overviews/videos", null, null, max_id, null, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves trending videos *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getTrendingTL(String max_id) {
        try {
            return getTL("/videos/", "-trending", null, max_id, null, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves trending videos *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getRecentlyAddedTL(String max_id) {
        try {
            return getTL("/videos/", "-publishedAt", null, max_id, null, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves trending videos *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getLocalTL(String max_id) {
        try {
            return getTL("/videos/", "-publishedAt", "local", max_id, null, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves home timeline for the account since an Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSubscriptionsTLSinceId(String since_id) {
        try {
            return getTL("/users/me/subscriptions/videos", null, null, null, since_id, null);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves home timeline for the account from a min Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSubscriptionsTLMinId(String min_id) {
        try {
            return getTL("/users/me/subscriptions/videos", null, null, null, null, min_id);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            return apiResponse;
        }
    }

    /**
     * Retrieves home timeline for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @return APIResponse
     */
    private APIResponse getTL(String action, String sort, String filter, String max_id, String since_id, String min_id) throws HttpsConnection.HttpsConnectionException {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        if (min_id != null)
            params.put("min_id", min_id);
        params.put("count", String.valueOf(tootPerPage));
        if (sort != null)
            params.put("sort", sort);
        else
            params.put("sort", "publishedAt");
        if (filter != null)
            params.put("filter", filter);
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean nsfw = sharedpreferences.getBoolean(Helper.SET_VIDEO_NSFW, false);
        params.put("nsfw", String.valueOf(nsfw));
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(action), 60, params, prefKeyOauthTokenT);
            // Helper.largeLog(response);
            if (!action.equals("/overviews/videos")) {
                JSONArray values = new JSONObject(response).getJSONArray("data");
                peertubes = parsePeertube(values);
            } else {
                JSONArray categories = new JSONObject(response).getJSONArray("categories");
                JSONArray channels = new JSONObject(response).getJSONArray("channels");
                JSONArray tags = new JSONObject(response).getJSONArray("tags");

                for (int i = 0; i < categories.length(); i++) {
                    JSONArray categoriesVideos = categories.getJSONObject(i).getJSONArray("videos");
                    List<Peertube> peertubeCategories = parsePeertube(categoriesVideos);
                    if (peertubeCategories != null && peertubeCategories.size() > 0) {
                        peertubeCategories.get(0).setHeaderType("categories");
                        peertubeCategories.get(0).setHeaderTypeValue(categories.getJSONObject(i).getJSONObject("category").getString("label"));
                        peertubes.addAll(peertubeCategories);
                    }
                }


                for (int i = 0; i < channels.length(); i++) {
                    JSONArray channelsVideos = channels.getJSONObject(i).getJSONArray("videos");
                    List<Peertube> peertubeChannels = parsePeertube(channelsVideos);
                    if (peertubeChannels != null && peertubeChannels.size() > 0) {
                        peertubeChannels.get(0).setHeaderType("channels");
                        peertubeChannels.get(0).setHeaderTypeValue(channels.getJSONObject(i).getJSONObject("channel").getString("displayName"));
                        peertubes.addAll(peertubeChannels);
                    }
                }

                for (int i = 0; i < tags.length(); i++) {
                    JSONArray tagsVideos = tags.getJSONObject(i).getJSONArray("videos");
                    List<Peertube> peertubeTags = parsePeertube(tagsVideos);
                    if (peertubeTags != null && peertubeTags.size() > 0) {
                        peertubeTags.get(0).setHeaderType("tags");
                        peertubeTags.get(0).setHeaderTypeValue(tags.getJSONObject(i).getString("tag"));
                        peertubes.addAll(peertubeTags);
                    }
                }


            }
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException | JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube channel from an account *synchronously*
     * Peertube channels are dealt like accounts
     *
     * @return APIResponse
     */
    public APIResponse getPeertubeChannel(String name) {

        List<Account> accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(String.format("/accounts/%s/video-channels", name)), 60, null, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            accounts = parseAccountResponsePeertube(context, instance, jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getPeertubeChannelVideos(String instance, String name) {

        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/video-channels/%s/videos", name), 60, null, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getPeertube(String instance, String max_id) {

        List<Peertube> peertubes = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        if (max_id == null)
            max_id = "0";
        params.put("start", String.valueOf(tootPerPage));
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get("https://" + instance + "/api/v1/videos", 60, params, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSinglePeertube(String instance, String videoId, String token) {

        Peertube peertube = null;
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/videos/%s", videoId), 60, null, token);
            JSONObject jsonObject = new JSONObject(response);
            peertube = parseSinglePeertube(context, instance, jsonObject);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        List<Peertube> peertubes = new ArrayList<>();
        peertubes.add(peertube);
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves peertube search *synchronously*
     *
     * @param query String search
     * @return APIResponse
     */
    public APIResponse searchPeertube(String instance, String query) {
        HashMap<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(tootPerPage));
        try {
            params.put("search", URLEncoder.encode(query, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            params.put("search", query);
        }
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get("https://" + instance + "/api/v1/search/videos", 60, params, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSinglePeertubeComments(String instance, String videoId) {
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/videos/%s/comment-threads", videoId), 60, null, null);
            JSONObject jsonObject = new JSONObject(response);
            statuses = parseSinglePeertubeComments(context, instance, jsonObject);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }

    /**
     * Retrieves rating of user on a video  *synchronously*
     *
     * @param id String id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public String getRating(String id) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(String.format("/users/me/videos/%s/rating", id)), 60, null, prefKeyOauthTokenT);
            return new JSONObject(response).get("rating").toString();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Makes the post action for a status
     *
     * @param statusAction Enum
     * @param targetedId   String id of the targeted Id *can be this of a status or an account*
     * @return in status code - Should be equal to 200 when action is done
     */
    public int postAction(API.StatusAction statusAction, String targetedId) {
        return postAction(statusAction, targetedId, null, null);
    }

    public int postRating(String targetedId, String actionMore) {
        return postAction(API.StatusAction.RATEVIDEO, targetedId, actionMore, null);
    }

    public int postComment(String targetedId, String actionMore) {
        return postAction(API.StatusAction.PEERTUBECOMMENT, targetedId, actionMore, null);
    }

    public int postReply(String targetedId, String actionMore, String targetedComment) {
        return postAction(API.StatusAction.PEERTUBEREPLY, targetedId, actionMore, targetedComment);
    }

    public int deleteComment(String targetedId, String targetedComment) {
        return postAction(API.StatusAction.PEERTUBEDELETECOMMENT, targetedId, null, targetedComment);
    }

    public int deleteVideo(String targetedId) {
        return postAction(API.StatusAction.PEERTUBEDELETEVIDEO, targetedId, null, null);
    }

    /**
     * Makes the post action
     *
     * @param statusAction    Enum
     * @param targetedId      String id of the targeted Id *can be this of a status or an account*
     * @param actionMore      String another action
     * @param targetedComment String another action
     * @return in status code - Should be equal to 200 when action is done
     */
    private int postAction(API.StatusAction statusAction, String targetedId, String actionMore, String targetedComment) {

        String action;
        String actionCall = "POST";
        HashMap<String, String> params = null;
        switch (statusAction) {
            case FOLLOW:
                action = "/users/me/subscriptions";
                params = new HashMap<>();
                params.put("uri", targetedId);
                break;
            case UNFOLLOW:
                action = String.format("/users/me/subscriptions/%s", targetedId);
                actionCall = "DELETE";
                break;
            case RATEVIDEO:
                action = String.format("/videos/%s/rate", targetedId);
                params = new HashMap<>();
                params.put("rating", actionMore);
                actionCall = "PUT";
                break;
            case PEERTUBECOMMENT:
                action = String.format("/videos/%s/comment-threads", targetedId);
                params = new HashMap<>();
                params.put("text", actionMore);
                break;
            case PEERTUBEDELETECOMMENT:
                action = String.format("/videos/%s/comments/%s", targetedId, targetedComment);
                actionCall = "DELETE";
                break;
            case PEERTUBEDELETEVIDEO:
                action = String.format("/videos/%s", targetedId);
                actionCall = "DELETE";
                break;
            case PEERTUBEREPLY:
                action = String.format("/videos/%s/comment/%s", targetedId, targetedComment);
                params = new HashMap<>();
                params.put("text", actionMore);
                break;
            default:
                return -1;
        }
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            if (actionCall.equals("POST"))
                httpsConnection.post(getAbsoluteUrl(action), 60, params, prefKeyOauthTokenT);
            else if (actionCall.equals("DELETE"))
                httpsConnection.delete(getAbsoluteUrl(action), 60, params, prefKeyOauthTokenT);
            else if (actionCall.equals("PUT"))
                httpsConnection.put(getAbsoluteUrl(action), 60, params, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Changes media description
     *
     * @param mediaId     String
     * @param description String
     * @return Attachment
     */
    public Attachment updateDescription(String mediaId, String description) {

        HashMap<String, String> params = new HashMap<>();
        try {
            params.put("description", URLEncoder.encode(description, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            params.put("description", description);
        }
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.put(getAbsoluteUrl(String.format("/media/%s", mediaId)), 240, params, prefKeyOauthTokenT);
            attachment = parseAttachmentResponse(new JSONObject(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return attachment;
    }

    /**
     * Video is in play lists
     *
     * @return APIResponse
     */
    public APIResponse getPlaylistForVideo(String videoId) {

        HashMap<String, String> params = new HashMap<>();
        params.put("videoIds", videoId);
        List<String> ids = new ArrayList<>();
        try {
            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl("/users/me/video-playlists/videos-exist"), 60, params, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONObject(response).getJSONArray(videoId);
            try {
                int i = 0;
                while (i < jsonArray.length()) {
                    JSONObject resobj = jsonArray.getJSONObject(i);
                    String playlistId = resobj.getString("playlistId");
                    ids.add(playlistId);
                    i++;
                }
            } catch (JSONException e) {
                setDefaultError(e);
            }
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse = new APIResponse();
        apiResponse.setPlaylistForVideos(ids);
        return apiResponse;
    }

    /**
     * Get lists for the user
     *
     * @return APIResponse
     */
    public APIResponse getPlayists(String username) {

        List<Playlist> playlists = new ArrayList<>();
        try {
            String response = new HttpsConnection(context, this.instance).get(getAbsoluteUrl(String.format("/accounts/%s/video-playlists", username)), 60, null, prefKeyOauthTokenT);
            playlists = parsePlaylists(context, new JSONObject(response).getJSONArray("data"));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPlaylists(playlists);
        return apiResponse;
    }

    /**
     * Delete a Playlist
     *
     * @param playlistId String, the playlist id
     * @return int
     */
    public int deletePlaylist(String playlistId) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            httpsConnection.delete(getAbsoluteUrl(String.format("/video-playlists/%s", playlistId)), 60, null, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Delete video in a Playlist
     *
     * @param playlistId String, the playlist id
     * @param videoId    String, the video id
     * @return int
     */
    public int deleteVideoPlaylist(String playlistId, String videoId) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            httpsConnection.delete(getAbsoluteUrl(String.format("/video-playlists/%s/videos/%s", playlistId, videoId)), 60, null, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Add video in a Playlist
     *
     * @param playlistId String, the playlist id
     * @param videoId    String, the video id
     * @return int
     */
    public int addVideoPlaylist(String playlistId, String videoId) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            HashMap<String, String> params = new HashMap<>();
            params.put("videoId", videoId);
            httpsConnection.post(getAbsoluteUrl(String.format("/video-playlists/%s/videos", playlistId)), 60, params, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param playlistid String Id of the playlist
     * @param max_id     String id max
     * @param since_id   String since the id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getPlaylistVideos(String playlistid, String max_id, String since_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("start", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        params.put("count", String.valueOf(tootPerPage));
        params.put("sort", "-updatedAt");
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context, this.instance);
            String response = httpsConnection.get(getAbsoluteUrl(String.format("/video-playlists/%s/videos", playlistid)), 60, params, prefKeyOauthTokenT);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(jsonArray);

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Parse json response for several howto
     *
     * @param jsonArray JSONArray
     * @return List<HowToVideo>
     */
    private List<HowToVideo> parseHowTos(JSONArray jsonArray) {

        List<HowToVideo> howToVideos = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {

                JSONObject resobj = jsonArray.getJSONObject(i);
                HowToVideo howToVideo = parseHowTo(context, resobj);
                i++;
                howToVideos.add(howToVideo);
            }

        } catch (JSONException e) {
            setDefaultError(e);
        }
        return howToVideos;
    }

    /**
     * Parse json response for peertube notifications
     *
     * @param jsonArray JSONArray
     * @return List<PeertubeNotification>
     */
    private List<PeertubeNotification> parsePeertubeNotifications(JSONArray jsonArray) {
        List<PeertubeNotification> peertubeNotifications = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                PeertubeNotification peertubeNotification = parsePeertubeNotifications(context, resobj);
                i++;
                peertubeNotifications.add(peertubeNotification);
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return peertubeNotifications;
    }

    /**
     * Parse json response for several instance reg
     *
     * @param jsonArray JSONArray
     * @return List<Status>
     */
    public List<InstanceReg> parseInstanceReg(JSONArray jsonArray) {

        List<InstanceReg> instanceRegs = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                InstanceReg instanceReg = parseInstanceReg(resobj);
                i++;
                instanceRegs.add(instanceReg);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return instanceRegs;
    }

    /**
     * Parse json response an unique instance for registering
     *
     * @param resobj JSONObject
     * @return InstanceReg
     */
    private InstanceReg parseInstanceReg(JSONObject resobj) {
        InstanceReg instanceReg = new InstanceReg();
        try {
            instanceReg.setDomain(resobj.getString("host"));
            instanceReg.setVersion(resobj.getString("version"));
            instanceReg.setDescription(resobj.getString("shortDescription"));
            instanceReg.setLanguage(resobj.getString("country"));
            instanceReg.setCategory("");
            instanceReg.setProxied_thumbnail("");
            instanceReg.setTotal_users(resobj.getInt("totalUsers"));
            instanceReg.setTotalInstanceFollowers(resobj.getInt("totalInstanceFollowers"));
            instanceReg.setTotalInstanceFollowing(resobj.getInt("totalInstanceFollowing"));
            instanceReg.setLast_week_users(0);
            instanceReg.setCountry(resobj.getString("country"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return instanceReg;
    }

    /**
     * Parse json response for several howto
     *
     * @param jsonArray JSONArray
     * @return List<Peertube>
     */
    private List<Peertube> parsePeertube(JSONArray jsonArray) {

        List<Peertube> peertubes = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Peertube peertube = parsePeertube(context, resobj);
                i++;
                peertubes.add(peertube);
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return peertubes;
    }

    /**
     * Parse json response an unique instance
     *
     * @param resobj JSONObject
     * @return Instance
     */
    private Instance parseInstance(JSONObject resobj) {

        Instance instance = new Instance();
        try {
            instance.setUri(resobj.get("uri").toString());
            instance.setTitle(resobj.get("title").toString());
            instance.setDescription(resobj.get("description").toString());
            instance.setEmail(resobj.get("email").toString());
            instance.setVersion(resobj.get("version").toString());
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return instance;
    }

    /**
     * Parse emojis
     *
     * @param jsonArray JSONArray
     * @return List<Emojis> of emojis
     */
    private List<Emojis> parseEmojis(JSONArray jsonArray) {
        List<Emojis> emojis = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Emojis emojis1 = parseEmojis(resobj);
                emojis.add(emojis1);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return emojis;
    }

    /**
     * Parse emojis
     *
     * @param jsonArray JSONArray
     * @return List<Emojis> of emojis
     */
    private List<Emojis> parseMisskeyEmojis(JSONArray jsonArray) {
        List<Emojis> emojis = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Emojis emojis1 = parseMisskeyEmojis(resobj);
                emojis.add(emojis1);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return emojis;
    }

    /**
     * Parse Filters
     *
     * @param jsonArray JSONArray
     * @return List<Filters> of filters
     */
    private List<Filters> parseFilters(JSONArray jsonArray) {
        List<Filters> filters = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Filters filter = parseFilter(resobj);
                if (filter != null)
                    filters.add(filter);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return filters;
    }

    /**
     * Parse json response for filter
     *
     * @param resobj JSONObject
     * @return Filter
     */
    private Filters parseFilter(JSONObject resobj) {
        Filters filter = new Filters();
        try {

            filter.setId(resobj.get("id").toString());
            if (resobj.get("phrase").toString() == null)
                return null;
            filter.setPhrase(resobj.get("phrase").toString());
            if (resobj.get("expires_at") != null && !resobj.get("expires_at").toString().equals("null"))
                filter.setSetExpires_at(Helper.mstStringToDate(context, resobj.get("expires_at").toString()));
            filter.setWhole_word(Boolean.parseBoolean(resobj.get("whole_word").toString()));
            filter.setIrreversible(Boolean.parseBoolean(resobj.get("irreversible").toString()));
            String contextString = resobj.get("context").toString();
            contextString = contextString.replaceAll("\\[", "");
            contextString = contextString.replaceAll("]", "");
            contextString = contextString.replaceAll("\"", "");
            if (contextString != null) {
                String[] context = contextString.split(",");
                if (contextString.length() > 0) {
                    ArrayList<String> finalContext = new ArrayList<>();
                    for (String c : context)
                        finalContext.add(c.trim());
                    filter.setContext(finalContext);
                }
            }
            return filter;
        } catch (Exception ignored) {
            return null;
        }

    }

    /**
     * Parse Playlists
     *
     * @param jsonArray JSONArray
     * @return List<Playlist> of lists
     */
    private List<Playlist> parsePlaylists(Context context, JSONArray jsonArray) {
        List<Playlist> playlists = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Playlist playlist = parsePlaylist(context, resobj);
                playlists.add(playlist);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return playlists;
    }

    private List<Account> parseAccountResponsePeertube(Context context, String instance, JSONArray jsonArray) {
        List<Account> accounts = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Account account = parseAccountResponsePeertube(context, resobj);
                accounts.add(account);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return accounts;
    }

    /**
     * Parse json response an unique relationship
     *
     * @param resobj JSONObject
     * @return Relationship
     */
    private Relationship parseRelationshipResponse(JSONObject resobj) {

        Relationship relationship = new Relationship();
        try {
            relationship.setId(resobj.get("id").toString());
            relationship.setFollowing(Boolean.valueOf(resobj.get("following").toString()));
            relationship.setFollowed_by(Boolean.valueOf(resobj.get("followed_by").toString()));
            relationship.setBlocking(Boolean.valueOf(resobj.get("blocking").toString()));
            relationship.setMuting(Boolean.valueOf(resobj.get("muting").toString()));
            try {
                relationship.setMuting_notifications(Boolean.valueOf(resobj.get("muting_notifications").toString()));
            } catch (Exception ignored) {
                relationship.setMuting_notifications(true);
            }
            try {
                relationship.setEndorsed(Boolean.valueOf(resobj.get("endorsed").toString()));
            } catch (Exception ignored) {
                relationship.setMuting_notifications(false);
            }
            try {
                relationship.setShowing_reblogs(Boolean.valueOf(resobj.get("showing_reblogs").toString()));
            } catch (Exception ignored) {
                relationship.setMuting_notifications(false);
            }
            relationship.setRequested(Boolean.valueOf(resobj.get("requested").toString()));
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return relationship;
    }

    /**
     * Parse json response for list of relationship
     *
     * @param jsonArray JSONArray
     * @return List<Relationship>
     */
    private List<Relationship> parseRelationshipResponse(JSONArray jsonArray) {

        List<Relationship> relationships = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Relationship relationship = parseRelationshipResponse(resobj);
                relationships.add(relationship);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return relationships;
    }

    /**
     * Set the error message
     *
     * @param statusCode int code
     * @param error      Throwable error
     */
    private void setError(int statusCode, Throwable error) {
        APIError = new Error();
        APIError.setStatusCode(statusCode);
        String message = statusCode + " - " + error.getMessage();
        try {
            JSONObject jsonObject = new JSONObject(error.getMessage());
            String errorM = jsonObject.get("error").toString();
            message = "Error " + statusCode + " : " + errorM;
        } catch (JSONException e) {
            if (error.getMessage().split(".").length > 0) {
                String errorM = error.getMessage().split(".")[0];
                message = "Error " + statusCode + " : " + errorM;
            }
        }
        APIError.setError(message);
        apiResponse.setError(APIError);
    }

    private void setDefaultError(Exception e) {
        APIError = new Error();
        if (e.getLocalizedMessage() != null && e.getLocalizedMessage().trim().length() > 0)
            APIError.setError(e.getLocalizedMessage());
        else if (e.getMessage() != null && e.getMessage().trim().length() > 0)
            APIError.setError(e.getMessage());
        else
            APIError.setError(context.getString(R.string.toast_error));
        apiResponse.setError(APIError);
    }


    public Error getError() {
        return APIError;
    }


    private String getAbsoluteUrl(String action) {
        return Helper.instanceWithProtocol(this.context, this.instance) + "/api/v1" + action;
    }

    private String getAbsoluteUrlRemote(String remote, String action) {
        return "https://" + remote + "/api/v1" + action;
    }

    private String getAbsoluteUrlRemoteInstance(String instanceName) {
        return "https://" + instanceName + "/api/v1/timelines/public?local=true";
    }

    private String getAbsoluteUrlCommunitywiki(String action) {
        return "https://communitywiki.org/trunk/api/v1" + action;
    }

}
