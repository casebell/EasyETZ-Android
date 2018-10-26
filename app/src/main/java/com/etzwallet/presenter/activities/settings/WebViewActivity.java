package com.etzwallet.presenter.activities.settings;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.Toolbar;

import com.etzwallet.R;
import com.etzwallet.presenter.activities.util.ActivityUTILS;
import com.etzwallet.presenter.activities.util.BRActivity;
import com.etzwallet.presenter.customviews.BRText;
import com.etzwallet.presenter.customviews.MyLog;
import com.etzwallet.tools.animation.BRAnimator;
import com.etzwallet.tools.util.BRConstants;
import com.etzwallet.tools.util.Utils;
import com.etzwallet.wallet.WalletsMaster;
import com.platform.HTTPServer;
import com.platform.middlewares.plugins.GeoLocationPlugin;
import com.platform.middlewares.plugins.LinkPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebViewActivity extends BRActivity {
    private static final String TAG = WebViewActivity.class.getName();
    WebView webView;
    String theUrl;
    public static boolean appVisible = false;
    private static WebViewActivity app;
    private String onCloseUrl;

    private static final int REQUEST_CHOOSE_IMAGE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 29;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 3;

    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;

    public static WebViewActivity getApp() {
        return app;
    }

    private Toolbar topToolbar;
    private Toolbar bottomToolbar;
    private BRText mCloseButton;
    private ImageButton mReloadButton;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private RelativeLayout mRootView;

    private boolean keyboardListenersAttached = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        webView = findViewById(R.id.web_view);
        webView.setBackgroundColor(0);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                MyLog.e( "onPageStarted: url:" + url);
                String trimmedUrl = rTrim(url, '/');
                Uri toUri = Uri.parse(trimmedUrl);
                if (closeOnMatch(toUri) || toUri.toString().contains("_close")) {
                    MyLog.e( "onPageStarted: close Uri found: " + toUri);
                    onBackPressed();
                    onCloseUrl = null;
                }

                if (view.canGoBack()) {
                    mBackButton.setBackground(getDrawable(R.drawable.ic_webview_left));
                } else {
                    mBackButton.setBackground(getDrawable(R.drawable.ic_webview_left_inactive));

                }
                if (view.canGoForward()) {
                    mForwardButton.setBackground(getDrawable(R.drawable.ic_webview_right));

                } else {
                    mForwardButton.setBackground(getDrawable(R.drawable.ic_webview_right_inactive));

                }

            }


        });

        topToolbar = findViewById(R.id.toolbar);
        bottomToolbar = findViewById(R.id.toolbar_bottom);
        mCloseButton = findViewById(R.id.close);
        mReloadButton = findViewById(R.id.reload);
        mForwardButton = findViewById(R.id.webview_forward_arrow);
        mBackButton = findViewById(R.id.webview_back_arrow);
        mRootView = findViewById(R.id.webview_parent);

        String articleId = getIntent().getStringExtra("articleId");

        WebSettings webSettings = webView.getSettings();

        if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);

        theUrl = getIntent().getStringExtra(BRConstants.EXTRA_URL);
        String json = getIntent().getStringExtra("json");

        if (json == null) {
            if (theUrl != null & !setupServerMode(theUrl)) {
                webView.loadUrl(theUrl);

                return;
            }

            if (articleId != null && !articleId.isEmpty())
                theUrl = theUrl + "/" + articleId;

            MyLog.d( "onCreate: theUrl: " + theUrl + ", articleId: " + articleId);
            if (!theUrl.contains("checkout")) {
                bottomToolbar.setVisibility(View.INVISIBLE);
            }
            if (theUrl.endsWith("/buy"))
                theUrl = theUrl + "?currency=" + WalletsMaster.getInstance(this).getCurrentWallet(this).getIso().toLowerCase();
            webView.loadUrl(theUrl);
            if (articleId != null && !articleId.isEmpty())
                navigate(articleId);
        } else {
            request(webView, json);

        }

        webView.setWebChromeClient(new BRWebChromeClient());


    }

    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            int heightDiff = mRootView.getRootView().getHeight() - mRootView.getHeight();
            int contentViewTop = getWindow().findViewById(Window.ID_ANDROID_CONTENT).getHeight();

            if (heightDiff <= contentViewTop) {
                onHideKeyboard();
                MyLog.d( "Hiding keyboard");

            } else {
                int keyboardHeight = heightDiff - contentViewTop;
                onShowKeyboard(keyboardHeight);
                MyLog.d( "Showing keyboard");


            }
        }
    };


    protected void onShowKeyboard(int keyboardHeight) {
        bottomToolbar.setVisibility(View.INVISIBLE);
    }

    protected void onHideKeyboard() {
        bottomToolbar.setVisibility(View.VISIBLE);

    }

    protected void attachKeyboardListeners() {
        if (keyboardListenersAttached) {
            return;
        }

        mRootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);

        keyboardListenersAttached = true;
    }


    private void request(final WebView webView, final String jsonString) {

        try {
            JSONObject json = new JSONObject(jsonString);

            String url = json.getString("url");
            MyLog.d( "Loading -> " + url);
            if (url != null && url.contains("checkout")) {


                attachKeyboardListeners();

                // Make the top and bottom toolbars visible for Simplex flow
                topToolbar.setVisibility(View.VISIBLE);
                bottomToolbar.setVisibility(View.VISIBLE);

                // Position the webview below the top toolbar
                RelativeLayout.LayoutParams webviewParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                webviewParams.addRule(RelativeLayout.BELOW, R.id.toolbar);
                webView.setLayoutParams(webviewParams);


                // Make the reload/refresh button functional
                mReloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        request(webView, jsonString);

                    }
                });

                // Make the close button functional
                mCloseButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        onBackPressed();
                    }
                });

                // Make the back button functional
                mBackButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if (webView.canGoBack()) {
                            webView.goBack();
                        }
                    }
                });


                // Make the forward button functional
                mForwardButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (webView.canGoForward()) {
                            webView.goForward();
                        }
                    }
                });
            }
            String method = json.getString("method");
            String strBody = json.getString("body");
            String headers = json.getString("headers");
            String closeOn = json.getString("closeOn");
            if (Utils.isNullOrEmpty(url) || Utils.isNullOrEmpty(method) ||
                    Utils.isNullOrEmpty(strBody) || Utils.isNullOrEmpty(headers) || Utils.isNullOrEmpty(closeOn)) {
                MyLog.e( "request: not enough params: " + jsonString);
                return;
            }
            onCloseUrl = closeOn;

            Map<String, String> httpHeaders = new HashMap<>();
//            JSONObject jsonHeaders = new JSONObject(headers);
//            while (jsonHeaders.keys().hasNext()) {
//                String key = jsonHeaders.keys().next();
//                jsonHeaders.put(key, jsonHeaders.getString(key));
//            }
            byte[] body = strBody.getBytes();

            if (method.equalsIgnoreCase("get")) {
                webView.loadUrl(url, httpHeaders);
            } else if (method.equalsIgnoreCase("post")) {
                MyLog.e( "request: POST:" + body.length);
                webView.postUrl(url, body);

            } else {
                throw new NullPointerException("unexpected method: " + method);
            }
        } catch (JSONException e) {
            MyLog.e( "request: Failed to parse json or not enough params: " + jsonString);
            e.printStackTrace();
        }

    }

    private void navigate(String to) {
        String js = String.format("window.location = \'%s\';", to);
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                MyLog.e( "onReceiveValue: " + value);
            }
        });
    }

    private boolean setupServerMode(String url) {
        if (url.equalsIgnoreCase(HTTPServer.URL_BUY)) {
            HTTPServer.mode = HTTPServer.ServerMode.BUY;
        } else if (url.equalsIgnoreCase(HTTPServer.URL_SUPPORT)) {
            HTTPServer.mode = HTTPServer.ServerMode.SUPPORT;
        } else if (url.equalsIgnoreCase(HTTPServer.URL_EA)) {
            HTTPServer.mode = HTTPServer.ServerMode.EA;
        } else {
            MyLog.e( "setupServerMode: " + "unknown url: " + url);
            return false;
        }
        return true;
    }

    private boolean closeOnMatch(Uri toUri) {
        if (onCloseUrl == null) {
            MyLog.e( "closeOnMatch: onCloseUrl is null");
            return false;
        }
        Uri savedCloseUri = Uri.parse(rTrim(onCloseUrl, '/'));
        MyLog.e( "closeOnMatch: toUrl:" + toUri + ", savedCloseUri: " + savedCloseUri);
        return toUri.getScheme() != null && toUri.getHost() != null && toUri.getScheme().equalsIgnoreCase(savedCloseUri.getScheme()) && toUri.getHost().equalsIgnoreCase(savedCloseUri.getHost())
                && toUri.toString().toLowerCase().contains(savedCloseUri.toString().toLowerCase());

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }


    private class BRWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            MyLog.e( "onConsoleMessage: consoleMessage: " + consoleMessage.message());
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            MyLog.e( "onJsAlert: " + message + ", url: " + url);
            return super.onJsAlert(view, url, message, result);
        }

        @Override
        public void onCloseWindow(WebView window) {
            super.onCloseWindow(window);
            MyLog.e( "onCloseWindow: ");
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            MyLog.e( "onReceivedTitle: view.getUrl:" + view.getUrl());
            String trimmedUrl = rTrim(view.getUrl(), '/');
            MyLog.e( "onReceivedTitle: trimmedUrl:" + trimmedUrl);
            Uri toUri = Uri.parse(trimmedUrl);

//                MyLog.d( "onReceivedTitle: " + request.getMethod());
            if (closeOnMatch(toUri) || toUri.toString().toLowerCase().contains("_close")) {
                MyLog.e( "onReceivedTitle: close Uri found: " + toUri);
                onBackPressed();
                onCloseUrl = null;
            }
        }

        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePath, FileChooserParams fileChooserParams) {

            MyLog.d( "onShowFileChooser");


            // Double check that we don't have any existing callbacks
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePath;

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                MyLog.d("Image Capture Activity FOUND");
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                } catch (IOException ex) {
                    // Error occurred while creating the File
                    MyLog.e("Unable to create Image File Exception:"+ ex);
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    MyLog.d( "Image File NOT null");
                    mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(photoFile));
                } else {
                    MyLog.d( "Image File IS NULL");

                    takePictureIntent = null;
                }
            } else {
                MyLog.d( "Image Capture Activity NOT FOUND");

            }


            requestImageFilePermission();
            requestCameraPermission();


            return true;
        }
    }

    // Get URI to image received from capture by camera.
    private Uri getCaptureImageOutputUri() {
        Uri outputFileUri = null;
        File getImage = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        if (getImage != null) {
            outputFileUri = Uri.fromFile(new File(getImage.getPath(), "_kyc.jpg"));
        }
        return outputFileUri;
    }


    public Intent getPickImageChooserIntent() {

        // Determine Uri of camera image to save.
        //Uri outputFileUri = getCaptureImageOutputUri();

        List<Intent> allIntents = new ArrayList();
        PackageManager packageManager = getPackageManager();

        // collect all camera intents
        Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);

        for (ResolveInfo res : listCam) {
            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            //if (outputFileUri != null) {
            //  intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            //}
            allIntents.add(intent);
        }

        // Collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            allIntents.add(intent);
        }

        // The main intent is the last in the list so pickup the last one
        Intent mainIntent = allIntents.get(allIntents.size() - 1);
        for (Intent intent : allIntents) {
            if (intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, "Select Image Source");

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }


    @Override
    public void onBackPressed() {
        if (ActivityUTILS.isLast(this)) {
            BRAnimator.startBreadActivity(this, false);
        } else {
            super.onBackPressed();
        }
        overridePendingTransition(R.anim.fade_up, R.anim.exit_to_bottom);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MyLog.d( "onActivityResult");
        MyLog.d( "requestCode -> " + requestCode);
        MyLog.d( "resultCode -> " + resultCode);

        //if (requestCode == BRConstants.UPLOAD_FILE_REQUEST) {

        if (requestCode != REQUEST_CHOOSE_IMAGE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        } else if (requestCode == REQUEST_CHOOSE_IMAGE) {
        }

        Uri[] results = null;

        // Check that the response is a good one
        if (resultCode == Activity.RESULT_OK) {
            MyLog.d( "Photo Path -> " + mCameraPhotoPath);

            if (data != null && data.getDataString() != null && !data.getDataString().isEmpty()) {
                MyLog.d( "Data string -> " + data.getDataString());
                results = new Uri[]{Uri.parse(data.getDataString())};


            } else if (mCameraPhotoPath != null) {
                results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                Toast.makeText(WebViewActivity.this, "Error getting selected image!", Toast.LENGTH_SHORT).show();
            }

            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;


        }

    }

    private void requestCameraPermission() {
        MyLog.d( "requestCameraPermission");


        // Camera permission is NOT granted, request it
        if (ContextCompat.checkSelfPermission(app,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            MyLog.d( "CAMERA permission NOT granted");
            android.support.v4.app.ActivityCompat.requestPermissions(app,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);

        }

        // Camera permission already granted, start the camera
        else {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            app.startActivityForResult(intent, REQUEST_CAMERA_PERMISSION);
            app.overridePendingTransition(R.anim.fade_up, R.anim.fade_down);

        }
    }


    private void requestImageFilePermission() {
        MyLog.d( "requestImageFilePermission");

        // Image File permission is not granted, request it now
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);


        } else {

            startActivityForResult(getPickImageChooserIntent(), REQUEST_CHOOSE_IMAGE);

        }
    }


    private File createImageFile() throws IOException {

        // ActivityCompat.requestPermissions(WebViewActivity.this,
        //new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                "_kyc.jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        MyLog.d( "onRequestPermissionResult");
        MyLog.d( "Request Code -> " + requestCode);

        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted, open camera
                    MyLog.d( "Camera permission GRANTED");
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    app.startActivityForResult(intent, REQUEST_CAMERA_PERMISSION);
                    app.overridePendingTransition(R.anim.fade_up, R.anim.fade_down);

                } else {
                    Toast.makeText(WebViewActivity.this, app.getString(R.string.Send_cameraUnavailabeMessage_android), Toast.LENGTH_SHORT).show();
                }
                break;
            }

            case REQUEST_WRITE_EXTERNAL_STORAGE: {

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    MyLog.d( "Storage permission GRANTED");

                    startActivityForResult(getPickImageChooserIntent(), REQUEST_CHOOSE_IMAGE);


                }


                break;
            }


            case BRConstants.GEO_REQUEST_ID: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted
                    MyLog.d( "Geo permission GRANTED");
                    GeoLocationPlugin.handleGeoPermission(true);
                } else {
                    GeoLocationPlugin.handleGeoPermission(false);
                }
                break;
            }
        }

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        appVisible = true;
        app = this;


    }

    private String rTrim(String s, char c) {
//        if (s == null) return null;
        String result = s;
        if (result.length() > 0 && s.charAt(s.length() - 1) == c)
            result = s.substring(0, s.length() - 1);
        return result;
    }

    @Override
    protected void onPause() {
        super.onPause();
        appVisible = false;
        LinkPlugin.hasBrowser = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (keyboardListenersAttached) {
            mRootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
    }

}
