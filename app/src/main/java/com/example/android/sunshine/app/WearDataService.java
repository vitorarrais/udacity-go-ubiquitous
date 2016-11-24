package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by User on 27/05/2016.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class WearDataService extends WearableListenerService implements Loader.OnLoadCompleteListener<Cursor>, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    private final static String WEATHER_PATH = "/weather-path";
    private final static String HIGH = "high";
    private final static String LOW = "low";
    private final static String WEATHER_ID = "weather_id";

    protected CursorLoader mCursorLoader;
    private static final int FORECAST_LOADER = 0;
    protected Cursor mCursor;
    private GoogleApiClient mGoogleApiClient;
    private Boolean isPendingUpdate = Boolean.FALSE;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(WearDataService.class.getSimpleName(), "onCreate");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        mCursorLoader = getCursorLoader();
        mCursorLoader.registerListener(FORECAST_LOADER, this);
        mCursorLoader.startLoading();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if (mCursor != null) {
            Log.d(WearDataService.class.getSimpleName(), "message received");
            sendDataToWearable(mCursor);

        } else {
            isPendingUpdate = Boolean.TRUE;
            mCursorLoader = getCursorLoader();
            mCursorLoader.registerListener(FORECAST_LOADER, this);
            mCursorLoader.startLoading();
        }
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
        Log.d(WearDataService.class.getSimpleName(), "onLoadComplete");
        mCursor = data;
        if (isPendingUpdate) {
            isPendingUpdate = Boolean.FALSE;
            if (mCursor != null) {
                sendDataToWearable(mCursor);
            }
        }
    }

    @Override
    public void onDestroy() {

        // Stop the cursor loader
        if (mCursorLoader != null) {
            mCursorLoader.unregisterListener(this);
            mCursorLoader.cancelLoad();
            mCursorLoader.stopLoading();
        }

        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    private void sendDataToWearable(Cursor data) {
        Log.d(WearDataService.class.getSimpleName(), "sending data to wearable");
        data.moveToFirst();
        PutDataMapRequest map = PutDataMapRequest.create(WEATHER_PATH);
        //Random random = new Random();
        //int randomNum = random.nextInt((1000 - 1) + 1) + 1;
        double high = data.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
        String highString = Utility.formatTemperature(this, high);
        double low = data.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
        String lowString = Utility.formatTemperature(this, low);
        Log.d(WearDataService.class.getSimpleName(), "high: " + highString);
        Log.d(WearDataService.class.getSimpleName(), "low: " + lowString);
        map.getDataMap().putString(HIGH, highString);
        map.getDataMap().putString(LOW, lowString);
        int weatherId = mCursor.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);
        Log.d(WearDataService.class.getSimpleName(), "weather id: " + weatherId);
        map.getDataMap().putInt(WEATHER_ID, weatherId);

        Wearable.DataApi.putDataItem(mGoogleApiClient, map.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(ForecastFragment.class.getSimpleName(), "data result: " + dataItemResult.getStatus().isSuccess());
                    }
                });
    }

    protected CursorLoader getCursorLoader() {
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());

        return new android.support.v4.content.CursorLoader(this,
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };
}
