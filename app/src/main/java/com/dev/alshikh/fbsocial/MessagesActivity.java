package com.dev.alshikh.fbsocial;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;


import im.delight.android.webview.AdvancedWebView;


public class MessagesActivity extends Activity implements 	AdvancedWebView.Listener {

	private SwipeRefreshLayout swipeRefreshLayout;//the layout that allows the swipe refresh
	private AdvancedWebView webViewMessages;//the main webView where is shown facebook
	private SharedPreferences savedPreferences;//contains all the values of saved preferences

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		savedPreferences = PreferenceManager.getDefaultSharedPreferences(this); // setup the sharedPreferences

		SetTheme();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_messages);

		SetupRefreshLayout();

		SetupMessagesWebView();//setup messages webview

		webViewMessages.loadUrl(getString(R.string.urlMessages));
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

	private void SetupMessagesWebView() {
		webViewMessages = (AdvancedWebView) findViewById(R.id.webViewMessages);
		webViewMessages.setListener(this, this);
		webViewMessages.addPermittedHostname("mbasic.facebook.com");

		WebSettings settings = webViewMessages.getSettings();
		webViewMessages.setDesktopMode(false);

		webViewMessages.requestFocus(View.FOCUS_DOWN);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);//remove the keyboard issue

		//set text zoom
		int zoom = Integer.parseInt(savedPreferences.getString("pref_textSize", "100"));
		settings.setTextZoom(zoom);


		// Use WideViewport and Zoom out if there is no viewport defined
		settings.setUseWideViewPort(false);
		settings.setLoadWithOverviewMode(false);


		// better image sizing support
		settings.setSupportZoom(false);
		settings.setDisplayZoomControls(false);
		settings.setBuiltInZoomControls(false);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
			// Hide the zoom controls for HONEYCOMB+
			settings.setDisplayZoomControls(false);
		}
	}

	private void SetupRefreshLayout() {
		swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
		swipeRefreshLayout.setColorSchemeResources(R.color.officialBlueFacebook, R.color.darkBlueSlimFacebookTheme);// set the colors
		swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				webViewMessages.reload();
			}
		});
	}

	//*********************** WEBVIEW EVENTS ****************************
	@Override
	public void onPageStarted(String url, Bitmap favicon) {
		swipeRefreshLayout.setRefreshing(true);
	}

	@Override
	public void onPageFinished(String url) {
		webViewMessages.loadUrl(getString(R.string.hideHeaderFooterMessages));//apply the customizations

		swipeRefreshLayout.setRefreshing(false);
	}

	@Override
	public void onPageError(int errorCode, String description, String failingUrl) {

	}

	@Override
	public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {

	}

	@Override
	public void onExternalPageRequest(String url) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (ActivityNotFoundException e) {//this prevents the crash
			Log.e("shouldOverrideUrlLoad", "" + e.getMessage());
			e.printStackTrace();
		}
	}

	//*********************** MENU ****************************
	//add my menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.messages_menu, menu);
		return true;
	}

	//handling the tap on the menu's items
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case R.id.close: {
				finish();// close view
				break;
			}
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}


	//*********************** BUTTON ****************************
	// handling the back button
	@Override
	public void onBackPressed() {
		if (webViewMessages.canGoBack()) {
			webViewMessages.goBack();
		} else {
			finish();// close view
		}
	}
}
