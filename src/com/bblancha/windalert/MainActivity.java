package com.bblancha.windalert;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;


public class MainActivity extends Activity {

    public static final String EXTRA_MESSAGE = "com.bblancha.windalert.MESSAGE";
    public static final String CHART_DATA = "com.bblancha.windalert.DATA";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String CHART_HTML = 
            "    <html> " + 
            "      <head> " + 
            "        <script type=\"text/javascript\" src=\"https://www.google.com/jsapi\"></script> " + 
            "        <script type=\"text/javascript\"> " + 
            "          google.load(\"visualization\", \"1\", {packages:[\"corechart\"]}); " + 
            "          google.setOnLoadCallback(drawChart); " + 
            "          function drawChart() { " + 
            "            var data = google.visualization.arrayToDataTable( %s ); " + 
            "            var options = { " + 
            "              title: 'FBS1 Wind', " + 
            "              width: 1000, height: 500," +
            "              vAxis: {minValue:0, maxValue: 40}, " +
            "              chartArea: {width: '85%%', height: '75%%'}, " +
            "              legend: {position: 'top'}," +
            "              fontSize: 25, " +
            "              lineWidth: 6 " + 
            "            }; " + 
            "            var chart = new google.visualization.LineChart(document.getElementById('chart_div')); " + 
            "            chart.draw(data, options); " + 
            "          } " + 
            "        </script> " + 
            "      </head> " + 
            "      <body> " + 
            "        <div id=\"chart_div\"></div> " + 
            "      </body> " + 
            "    </html>";
    
    /**
     * Project number
     */
    String SENDER_ID = "259745403295";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "Wind Alert";

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    Context context;
    WebView myWebView;

    String regid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mDisplay = (TextView) findViewById(R.id.display);
        myWebView = (WebView) findViewById(R.id.webView);      

        context = getApplicationContext();

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);

            if (regid.isEmpty()) {
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
        
        Intent i = getIntent();
        if (i != null) {

            mDisplay.setText(i.getStringExtra(MainActivity.EXTRA_MESSAGE));
            String stringExtra = i.getStringExtra(MainActivity.CHART_DATA);
            loadWindChart(stringExtra);

        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void loadWindChart(String chartData) {

        String html = String.format(CHART_HTML, chartData);
        
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        
        myWebView.setWebChromeClient(new WebChromeClient(){

            ProgressBar mProgress = (ProgressBar) findViewById(R.id.progressBar);
            
            public void onProgressChanged(WebView view, int progress) {

                mProgress.setProgress(progress * 100);
                if(progress == 100)
                      mProgress.setVisibility(View.GONE);
                }
         });
        
        myWebView.loadData(html, "text/html", null);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Check device for Play Services APK.
        checkPlayServices();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

}
