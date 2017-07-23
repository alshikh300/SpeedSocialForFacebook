package com.dev.alshikh.fbsocial;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import com.dev.alshikh.fbsocial.settings.SettingsActivity;
import com.dev.alshikh.fbsocial.utility.Dimension;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.NativeExpressAdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import im.delight.android.webview.AdvancedWebView;


public class MainActivity extends Activity implements AdvancedWebView.Listener {

	private SwipeRefreshLayout swipeRefreshLayout;//the layout that allows the swipe refresh
	private AdvancedWebView webViewFacebook;//the main webView where is shown facebook

	private SharedPreferences savedPreferences;//contains all the values of saved preferences

	private boolean noConnectionError = false;//flag: is true if there is a connection error. It should reload the last useful page

	private boolean isSharer = false;//flag: true if the app is called from sharer
	private String urlSharer = "";//to save the url got from the sharer

	// create link handler (long clicked links)
	private final MyHandler linkHandler = new MyHandler(this);

	//full screen video variables
	private FrameLayout mTargetView;
	private WebChromeClient myWebChromeClient;
	private WebChromeClient.CustomViewCallback mCustomViewCallback;
	private View mCustomView;

	private FirebaseAnalytics mFirebaseAnalytics;

	//*********************** ACTIVITY EVENTS ****************************
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		savedPreferences = PreferenceManager.getDefaultSharedPreferences(this); // setup the sharedPreferences

		SetTheme();//set the activity theme

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		// Obtain the FirebaseAnalytics instance.
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

		// if the app is being launched for the first time
		if (savedPreferences.getBoolean("first_run", true)) {
			savedPreferences.edit().putBoolean("first_run", false).apply();
		}


		SetupRefreshLayout();// setup the refresh layout

		ShareLinkHandler();//handle a link shared (if there is)

		SetupWebView();//setup webview


		if (isSharer) {//if is a share request
			Log.d("MainActiviy.OnCreate", "Loading shared link");
			webViewFacebook.loadUrl(urlSharer);//load the sharer url
			isSharer = false;
		} else if (getIntent() != null && getIntent().getDataString() != null) {
			//if the app is opened by fb link
			webViewFacebook.loadUrl(FromDestopToMobileUrl(getIntent().getDataString()));
		} else {
			if (savedPreferences.getBoolean("pref_recentNewsFirst", false)) {
				webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_chr");
			} else
				webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_nor");

			//GoHome();//load homepage

		}

		SetupFullScreenVideo();

		SetupOnLongClickListener();



		int	Count =0;
		Count = savedPreferences.getInt("count", 0);
		++Count;
		savedPreferences.edit().putInt("count", Count).apply();


		final NativeExpressAdView adView = (NativeExpressAdView)findViewById(R.id.adView);

		// Set an AdListener for the AdView, so the Activity can take action when an ad has finished
		// loading.
		final int finalCount = Count;
		adView.setAdListener(new AdListener() {
			@Override
			public void onAdLoaded() {
				if(finalCount > 1){
					adView.setVisibility(View.VISIBLE);
				}
			}
		});
		adView.loadAd(new AdRequest.Builder().build());
	}

	@SuppressLint("NewApi")
	@Override
	protected void onResume() {
		super.onResume();
		webViewFacebook.onResume();
	}

	@SuppressLint("NewApi")
	@Override
	protected void onPause() {
		webViewFacebook.onPause();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		Log.e("Info", "onDestroy()");
		webViewFacebook.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		webViewFacebook.onActivityResult(requestCode, resultCode, intent);
	}

	// app is already running and gets a new intent (used to share link without open another activity)
	@Override
	protected void onNewIntent(Intent intent) {

		super.onNewIntent(intent);
		setIntent(intent);

		// grab an url if opened by clicking a link
		String webViewUrl = getIntent().getDataString();

		/** get a subject and text and check if this is a link trying to be shared */
		String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
		String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
		Log.d("sharedUrl", "onNewIntent() - sharedUrl: " + sharedUrl);
		// if we have a valid URL that was shared by us, open the sharer
		if (sharedUrl != null) {
			if (!sharedUrl.equals("")) {
				// check if the URL being shared is a proper web URL
				if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
					// if it's not, let's see if it includes an URL in it (prefixed with a message)
					int startUrlIndex = sharedUrl.indexOf("http:");
					if (startUrlIndex > 0) {
						// seems like it's prefixed with a message, let's trim the start and get the URL only
						sharedUrl = sharedUrl.substring(startUrlIndex);
					}
				}
				// final step, set the proper Sharer...
				webViewUrl = String.format("https://m.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
				// ... and parse it just in case
				webViewUrl = Uri.parse(webViewUrl).toString();
			}
		}

		if (webViewUrl != null)
			webViewFacebook.loadUrl(FromDestopToMobileUrl(webViewUrl));


		// recreate activity when something important was just changed
		if (getIntent().getBooleanExtra("settingsChanged", false)) {
			finish(); // close this
			Intent restart = new Intent(MainActivity.this, MainActivity.class);
			startActivity(restart);//reopen this
		}
	}

	//*********************** SETUP ****************************

	private void SetupWebView() {
		webViewFacebook = (AdvancedWebView) findViewById(R.id.webView);
		webViewFacebook.setListener(this, this);

		webViewFacebook.clearPermittedHostnames();
		webViewFacebook.addPermittedHostname("facebook.com");
		webViewFacebook.addPermittedHostname("fbcdn.net");
/*webViewFacebook.addPermittedHostname("m.facebook.com");
        webViewFacebook.addPermittedHostname("h.facebook.com");
        webViewFacebook.addPermittedHostname("mbasic.facebook.com");
      webViewFacebook.addPermittedHostname("touch.facebook.com");
	  webViewFacebook.addPermittedHostname("messenger.com");*/

		webViewFacebook.requestFocus(View.FOCUS_DOWN);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);//remove the keyboard issue

		WebSettings settings = webViewFacebook.getSettings();

		webViewFacebook.setDesktopMode(true);
		//settings.setUserAgentString("Mozilla/5.0 (BB10; Kbd) AppleWebKit/537.10+ (KHTML, like Gecko) Version/10.1.0.4633 Mobile Safari/537.10+");
		settings.setJavaScriptEnabled(true);

		//set text zoom
		int zoom = Integer.parseInt(savedPreferences.getString("pref_textSize", "100"));
		settings.setTextZoom(zoom);

		//set Geolocation
		settings.setGeolocationEnabled(savedPreferences.getBoolean("pref_allowGeolocation", true));

		// Use WideViewport and Zoom out if there is no viewport defined
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);

		// better image sizing support
		settings.setSupportZoom(true);
		settings.setDisplayZoomControls(false);
		settings.setBuiltInZoomControls(true);

		// set caching
		settings.setAppCachePath(getCacheDir().getAbsolutePath());
		settings.setAppCacheEnabled(true);

		settings.setLoadsImagesAutomatically(!savedPreferences.getBoolean("pref_doNotDownloadImages", false));//to save data

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
			// Hide the zoom controls for HONEYCOMB+
			settings.setDisplayZoomControls(false);
		}
	}

	private void SetupOnLongClickListener() {
		// OnLongClickListener for detecting long clicks on links and images
		webViewFacebook.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {

				WebView.HitTestResult result = webViewFacebook.getHitTestResult();
				int type = result.getType();
				if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
						|| type == WebView.HitTestResult.IMAGE_TYPE) {
					Message msg = linkHandler.obtainMessage();
					webViewFacebook.requestFocusNodeHref(msg);
					//final String linkUrl = (String) msg.getData().get("url");
					final String imgUrl = (String) msg.getData().get("src");
//                    if (linkUrl != null && imgUrl != null) {
//                        activity.longClickDialog(linkUrl, imgUrl);
//                    } else
// if (linkUrl != null) {
//                        activity.longClickDialog(linkUrl);
//                    } else
					if (imgUrl != null) {
						new AlertDialog.Builder(MainActivity.this)
								.setTitle(getResources().getString(R.string.askDownloadPhoto))
								//.setMessage("Do you want download this photo?")
								.setCancelable(true)
								.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										//check permission
										if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(MainActivity.this,
												android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
											//ask permission
											Toast.makeText(getApplicationContext(), getString(R.string.acceptPermissionAndRetry),
													Toast.LENGTH_LONG).show();
											int requestResult = 0;
											ActivityCompat.requestPermissions(MainActivity.this,
													new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestResult
											);
										} else {


											//share photo
											// Intent sharingIntent = new Intent(Intent.ACTION_SEND);
//												sharingIntent.setType("image/jpeg");
//												sharingIntent.putExtra(Intent.EXTRA_STREAM, imgUrl);
//												startActivity(Intent.createChooser(sharingIntent, "Share image using"));

											//open photo with gallery
//												Intent intent = new Intent();
//												intent.setAction(Intent.ACTION_VIEW);
//												intent.setDataAndType(Uri.parse("file://" + imgUrl), "image/*");
//												startActivity(intent);

											//download photo
											DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imgUrl));
											request.setTitle("SpeedSocial Download");
											// in order for this if to run, you must use the android 3.2 to compile your app
											if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
												request.allowScanningByMediaScanner();
												request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
											}


											String path = Environment.DIRECTORY_DOWNLOADS;
											if (savedPreferences.getBoolean("pref_useSpeedSocialSubfolderToDownloadedFiles", false)) {
												path += "/SpeedSocial";
											}
											request.setDestinationInExternalPublicDir(path, "Photo.jpg");

											// get download service and enqueue file
											DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
											manager.enqueue(request);
											Toast.makeText(getApplicationContext(), getString(R.string.downloadingPhoto),
													Toast.LENGTH_LONG).show();
										}
									}
								}).create().show();
					}
					return true;
				}
				return false;
			}
		});

		webViewFacebook.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_UP:
						if (!v.hasFocus()) {
							v.requestFocus();
						}
						break;
				}
				return false;
			}
		});
	}

	private void SetupFullScreenVideo() {
		//full screen video
		mTargetView = (FrameLayout) findViewById(R.id.target_view);
		myWebChromeClient = new WebChromeClient() {
			//this custom WebChromeClient allow to show video on full screen
			@Override
			public void onShowCustomView(View view, CustomViewCallback callback) {
				mCustomViewCallback = callback;
				mTargetView.addView(view);
				mCustomView = view;
				swipeRefreshLayout.setVisibility(View.GONE);
				mTargetView.setVisibility(View.VISIBLE);
				mTargetView.bringToFront();
			}

			@Override
			public void onHideCustomView() {
				if (mCustomView == null)
					return;

				mCustomView.setVisibility(View.GONE);
				mTargetView.removeView(mCustomView);
				mCustomView = null;
				mTargetView.setVisibility(View.GONE);
				mCustomViewCallback.onCustomViewHidden();
				swipeRefreshLayout.setVisibility(View.VISIBLE);
			}

			@Override
			public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
				callback.invoke(origin, true, false);
			}
		};
		webViewFacebook.setWebChromeClient(myWebChromeClient);
	}

	private void ShareLinkHandler() {
		/** get a subject and text and check if this is a link trying to be shared */
		String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
		String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
		Log.d("sharedUrl", "ShareLinkHandler() - sharedUrl: " + sharedUrl);

		// if we have a valid URL that was shared by us, open the sharer
		if (sharedUrl != null) {
			if (!sharedUrl.equals("")) {
				// check if the URL being shared is a proper web URL
				if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
					// if it's not, let's see if it includes an URL in it (prefixed with a message)
					int startUrlIndex = sharedUrl.indexOf("http:");
					if (startUrlIndex > 0) {
						// seems like it's prefixed with a message, let's trim the start and get the URL only
						sharedUrl = sharedUrl.substring(startUrlIndex);
					}
				}
				// final step, set the proper Sharer...
				urlSharer = String.format("https://m.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
				// ... and parse it just in case
				urlSharer = Uri.parse(urlSharer).toString();
				isSharer = true;
			}
		}

	}

	private void SetTheme() {
		switch (savedPreferences.getString("pref_theme", "default")) {
			case "DarkTheme": {
				setTheme(R.style.DarkTheme);
				break;
			}
			default: {
				setTheme(R.style.DefaultTheme);
				break;
			}
		}
	}

	private void SetupRefreshLayout() {
		swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
		swipeRefreshLayout.setColorSchemeResources(R.color.officialBlueFacebook, R.color.darkBlueSlimFacebookTheme);// set the colors
		swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				RefreshPage();//reload the page
			}
		});
	}


	//*********************** WEBVIEW FACILITIES ****************************
	private void GoHome() {
		if (savedPreferences.getBoolean("pref_recentNewsFirst", false)) {
			webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_chr");
		} else
			webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_nor");
	}

	private void RefreshPage() {
		if (noConnectionError) {
			webViewFacebook.goBack();
			noConnectionError = false;
		} else webViewFacebook.reload();
	}


	//*********************** WEBVIEW EVENTS ****************************


	@Override
	public void onPageStarted(String url, Bitmap favicon) {
		swipeRefreshLayout.setRefreshing(true);

		if (Uri.parse(url).getHost()!= null && Uri.parse(url).getHost().endsWith("fbcdn.net"))
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.holdImageToDownload),
					Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onPageFinished(String url) {
		ApplyCustomCss();

		if (savedPreferences.getBoolean("pref_enableMessagesShortcut", false)) {
			webViewFacebook.loadUrl(getString(R.string.fixMessages));
		}

		swipeRefreshLayout.setRefreshing(false);

	}

	@Override
	public void onPageError(int errorCode, String description, String failingUrl) {
		// refresh on connection error (sometimes there is an error even when there is a network connection)
		if(isInternetAvailable()) return;
			//  if (!isInternetAvailable() && !failingUrl.contains("edge-chat") && !failingUrl.contains("akamaihd")                && !failingUrl.contains("atdmt") && !noConnectionError)
		else  {
			Log.i("onPageError link" , failingUrl);
			String summary = "<h1 style='text-align:center; padding-top:15%; font-size:70px;'>" +
					getString(R.string.titleNoConnection) +
					"</h1> <h3 style='text-align:center; padding-top:1%; font-style: italic;font-size:50px;'>" +
					getString(R.string.descriptionNoConnection) +
					"</h3>  <h5 style='font-size:30px; text-align:center; padding-top:80%; opacity: 0.3;'>" +
					getString(R.string.awards) +
					"</h5>";
			webViewFacebook.loadData(summary, "text/html; charset=utf-8", "utf-8");//load a custom html page
			//to allow to return at the last visited page
			noConnectionError = true;
		}
	}


	public boolean isInternetAvailable() {
		NetworkInfo newtworkInfo = ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
		if (newtworkInfo != null && newtworkInfo.isAvailable() && newtworkInfo.isConnected()) return true;
		else return false;
	}

	@Override
	public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {

		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		startActivity(i);

	}


	@Override
	public void onExternalPageRequest(String url) {//if the link doesn't contain 'facebook.com', open it using the browser
		if (Uri.parse(url).getHost() != null ? Uri.parse(url).getHost().endsWith("SpeedSocial.leo") : false) {
			//he clicked on messages
			startActivity(new Intent(this, MessagesActivity.class));
		} else {
			try {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			} catch (ActivityNotFoundException e) {//this prevents the crash
				Log.e("shouldOverrideUrlLoad", "" + e.getMessage());
				e.printStackTrace();
			}
		}
	}


	//*********************** BUTTON ****************************
	// handling the back button
	boolean doubleBackToExitPressedOnce = false;
	@Override
	public void onBackPressed() {
		if (mCustomView != null) {
			myWebChromeClient.onHideCustomView();//hide video player
		} else {
			if (webViewFacebook.canGoBack()) {
				webViewFacebook.goBack();
			} else {
				if (doubleBackToExitPressedOnce) {
					super.onBackPressed();
					return;
				}
				this.doubleBackToExitPressedOnce = true;
				Toast.makeText(this,"Please click BACK again to exit", Toast.LENGTH_SHORT).show();
				new Handler().postDelayed(new Runnable() {

					@Override
					public void run() {
						doubleBackToExitPressedOnce=false;
					}
				}, 2000);
			}
		}
	}


	//*********************** MENU ****************************
	//add my menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	//handling the tap on the menu's items
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case R.id.top: {//scroll on the top of the page
				webViewFacebook.scrollTo(0, 0);
				break;
			}
			case R.id.openInBrowser: {//open the actual page into using the browser
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webViewFacebook.getUrl())));
				break;
			}
			case R.id.messages: {//open messages
				startActivity(new Intent(this, MessagesActivity.class));
				break;
			}
			case R.id.refresh: {//refresh the page
				RefreshPage();
				break;
			}
			case R.id.home: {//go to the home
				GoHome();
				break;
			}
			case R.id.shareLink: {//share this page
				Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
				sharingIntent.setType("text/plain");
				sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, MyHandler.cleanUrl(webViewFacebook.getUrl()));
				startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.shareThisLink)));

				break;
			}
			case R.id.share: {//share this app
				Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
				sharingIntent.setType("text/plain");
				sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, getResources().getString(R.string.downloadThisApp));
				startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share)));

				Toast.makeText(getApplicationContext(), getResources().getString(R.string.thanks),
						Toast.LENGTH_SHORT).show();
				break;
			}
			case R.id.settings: {//open settings
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			}

			case R.id.exit: {//open settings
				android.os.Process.killProcess(android.os.Process.myPid());
				System.exit(1);
				return true;
			}

			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}


	//*********************** OTHER ****************************

	String FromDestopToMobileUrl(String url) {
		if (Uri.parse(url).getHost()!=null && Uri.parse(url).getHost().endsWith("www.facebook.com")) {
			url = url.replace("www.facebook.com", "touch.facebook.com");
		}
		return url;
	}

	private void ApplyCustomCss() {
		String css = "";
		if (savedPreferences.getBoolean("pref_centerTextPosts", false)) {
			css += getString(R.string.centerTextPosts);
		}
		if (savedPreferences.getBoolean("pref_addSpaceBetweenPosts", false)) {
			css += getString(R.string.addSpaceBetweenPosts);
		}
		if (savedPreferences.getBoolean("pref_hideSponsoredPosts", false)) {
			css += getString(R.string.hideAdsAndPeopleYouMayKnow);
		}
		if (savedPreferences.getBoolean("pref_fixedBar", true)) {//without add the barHeight doesn't scroll
			css += (getString(R.string.fixedBar).replace("$s", "" + Dimension.heightForFixedFacebookNavbar(getApplicationContext())));
		}
		if (savedPreferences.getBoolean("pref_removeMessengerDownload", true)) {
			css += getString(R.string.removeMessengerDownload);
		}

		switch (savedPreferences.getString("pref_theme", "standard")) {
			case "DarkTheme":
			case "DarkNoBar": {
				css += getString(R.string.blackTheme);
			}
			default:
				break;
		}

		//apply the customizations
		webViewFacebook.loadUrl(getString(R.string.editCss).replace("$css", css));
	}

	// handle long clicks on links, an awesome way to avoid memory leaks
	private static class MyHandler extends Handler {
		MainActivity activity;
		//thanks to FaceSlim
		private final WeakReference<MainActivity> mActivity;

		public MyHandler(MainActivity activity) {
			this.activity = activity;
			mActivity = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			SharedPreferences savedPreferences = PreferenceManager.getDefaultSharedPreferences(activity); // setup the sharedPreferences
			if (savedPreferences.getBoolean("pref_enableFastShare", true)) {
				MainActivity activity = mActivity.get();
				if (activity != null) {

					// get url to share
					String url = (String) msg.getData().get("url");

					if (url != null) {
                    /* "clean" an url to remove Facebook tracking redirection while sharing
					and recreate all the special characters */
						url = decodeUrl(cleanUrl(url));

						// create share intent for long clicked url
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType("text/plain");
						intent.putExtra(Intent.EXTRA_TEXT, url);
						activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.shareThisLink)));
					}
				}
			}
		}

		// "clean" an url and remove Facebook tracking redirection
		private static String cleanUrl(String url) {
			return url.replace("http://lm.facebook.com/l.php?u=", "")
					.replace("https://m.facebook.com/l.php?u=", "")
					.replace("http://0.facebook.com/l.php?u=", "")
					.replaceAll("&h=.*", "").replaceAll("\\?acontext=.*", "") + "&SharedWith=SpeedSocial";
		}

		// url decoder, recreate all the special characters
		private static String decodeUrl(String url) {
			return url.replace("%3C", "<").replace("%3E", ">").replace("%23", "#").replace("%25", "%")
					.replace("%7B", "{").replace("%7D", "}").replace("%7C", "|").replace("%5C", "\\")
					.replace("%5E", "^").replace("%7E", "~").replace("%5B", "[").replace("%5D", "]")
					.replace("%60", "`").replace("%3B", ";").replace("%2F", "/").replace("%3F", "?")
					.replace("%3A", ":").replace("%40", "@").replace("%3D", "=").replace("%26", "&")
					.replace("%24", "$").replace("%2B", "+").replace("%22", "\"").replace("%2C", ",")
					.replace("%20", " ");
		}
	}


}
