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
package app.fedilab.android.interfaces;


import app.fedilab.android.client.API;
import app.fedilab.android.client.Entities.Error;

/**
 * Created by Thomas on 29/04/2017.
 * Interface when post actions has been done with a status
 */
public interface OnPostActionInterface {
    void onPostAction(int statusCode, API.StatusAction statusAction, String userId, Error error);
}
