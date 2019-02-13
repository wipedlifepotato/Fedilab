package fr.gouv.etalab.mastodon.client;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */

import android.content.Context;

import java.util.List;

import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Conversation;
import fr.gouv.etalab.mastodon.client.Entities.Emojis;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.client.Entities.Filters;
import fr.gouv.etalab.mastodon.client.Entities.HowToVideo;
import fr.gouv.etalab.mastodon.client.Entities.Instance;
import fr.gouv.etalab.mastodon.client.Entities.Notification;
import fr.gouv.etalab.mastodon.client.Entities.Peertube;
import fr.gouv.etalab.mastodon.client.Entities.PeertubeNotification;
import fr.gouv.etalab.mastodon.client.Entities.Relationship;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.Entities.StoredStatus;

/**
 * Created by Thomas on 03/06/2017.
 * Hydrate response from the API
 */

public class CustomSharingResponse {

    private List<Account> accounts = null;
    private List<Status> statuses = null;
    private List<Context> contexts = null;
    private List<Conversation> conversations = null;
    private List<Notification> notifications = null;
    private List<Relationship> relationships = null;
    private List<HowToVideo> howToVideos = null;
    private List<Peertube> peertubes = null;
    private List<PeertubeNotification> peertubeNotifications = null;
    private List<Filters> filters = null;
    private List<String> domains = null;
    private List<fr.gouv.etalab.mastodon.client.Entities.List> lists = null;
    private List<Emojis> emojis = null;
    private Error error = null;
    private String since_id, max_id;
    private Instance instance;
    private List<StoredStatus> storedStatuses;
    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public List<Status> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<Status> statuses) {
        this.statuses = statuses;
    }

    public List<Context> getContexts() {
        return contexts;
    }

    public void setContexts(List<Context> contexts) {
        this.contexts = contexts;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public String getMax_id() {
        return max_id;
    }

    public void setMax_id(String max_id) {
        this.max_id = max_id;
    }

    public String getSince_id() {
        return since_id;
    }

    public void setSince_id(String since_id) {
        this.since_id = since_id;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    public List<Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships;
    }

    public List<Emojis> getEmojis() {
        return emojis;
    }

    public void setEmojis(List<Emojis> emojis) {
        this.emojis = emojis;
    }

    public List<fr.gouv.etalab.mastodon.client.Entities.List> getLists() {
        return lists;
    }

    public void setLists(List<fr.gouv.etalab.mastodon.client.Entities.List> lists) {
        this.lists = lists;
    }

    public List<Filters> getFilters() {
        return filters;
    }

    public void setFilters(List<Filters> filters) {
        this.filters = filters;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }

    public List<HowToVideo> getHowToVideos() {
        return howToVideos;
    }

    public void setHowToVideos(List<HowToVideo> howToVideos) {
        this.howToVideos = howToVideos;
    }

    public List<Peertube> getPeertubes() {
        return peertubes;
    }

    public void setPeertubes(List<Peertube> peertubes) {
        this.peertubes = peertubes;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public void setConversations(List<Conversation> conversations) {
        this.conversations = conversations;
    }

    public List<StoredStatus> getStoredStatuses() {
        return storedStatuses;
    }

    public void setStoredStatuses(List<StoredStatus> storedStatuses) {
        this.storedStatuses = storedStatuses;
    }

    public List<PeertubeNotification> getPeertubeNotifications() {
        return peertubeNotifications;
    }

    public void setPeertubeNotifications(List<PeertubeNotification> peertubeNotifications) {
        this.peertubeNotifications = peertubeNotifications;
    }
}
