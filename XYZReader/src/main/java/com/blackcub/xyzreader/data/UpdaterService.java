package com.blackcub.xyzreader.data;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.text.format.Time;
import android.util.Log;

import com.blackcub.xyzreader.R;
import com.blackcub.xyzreader.remote.RemoteEndpointUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class UpdaterService extends IntentService {
    private static final String TAG = "UpdaterService";

    public static final String BROADCAST_ACTION_STATE_CHANGE
            = "com.example.com.blackcub.xyzreader.intent.action.STATE_CHANGE";
    public static final String EXTRA_REFRESHING
            = "com.example.com.blackcub.xyzreader.intent.extra.REFRESHING";

    public UpdaterService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Time time = new Time();

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            Log.w(TAG, "Not online, not refreshing.");
            return;
        }

        sendStickyBroadcast(
                new Intent(BROADCAST_ACTION_STATE_CHANGE).putExtra(EXTRA_REFRESHING, true));

        // Don't even inspect the intent, we only do one thing, and that's fetch content.
        ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

        Uri dirUri = ItemsContract.Items.buildDirUri();

        // Delete all items
        cpo.add(ContentProviderOperation.newDelete(dirUri).build());

        try {
            Notification notification = new Notification.Builder(this)
                    .setContentText("Downloading")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(0, notification);
            JSONArray array = RemoteEndpointUtil.fetchJsonArray();
            notificationManager.cancel(0);
            if (array == null) {
                throw new JSONException("Invalid parsed item array" );
            }

            for (int i = 0; i < array.length(); i++) {
                ContentValues values = new ContentValues();
                JSONObject object = array.getJSONObject(i);
                values.put(ItemsContract.Items.SERVER_ID, object.getString("id" ));
                values.put(ItemsContract.Items.AUTHOR, object.getString("author" ));
                values.put(ItemsContract.Items.TITLE, object.getString("title" ));
                values.put(ItemsContract.Items.BODY, object.getString("body" ));
                values.put(ItemsContract.Items.THUMB_URL, object.getString("thumb" ));
                values.put(ItemsContract.Items.PHOTO_URL, object.getString("photo" ));
                values.put(ItemsContract.Items.ASPECT_RATIO, object.getString("aspect_ratio" ));
                time.parse3339(object.getString("published_date"));
                values.put(ItemsContract.Items.PUBLISHED_DATE, time.toMillis(false));
                cpo.add(ContentProviderOperation.newInsert(dirUri).withValues(values).build());
            }

            getContentResolver().applyBatch(ItemsContract.CONTENT_AUTHORITY, cpo);

        } catch (JSONException | RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating content.", e);
        }

        sendStickyBroadcast(
                new Intent(BROADCAST_ACTION_STATE_CHANGE).putExtra(EXTRA_REFRESHING, false));
    }
}
