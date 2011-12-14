package com.grsuuu.instagramble;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grsuuu.instagramble.util.MyLog;

public class Main extends Activity {
	public static String APP_NAME;

	private static final String OAUTH_AUTHORIZE_URL = "https://api.instagram.com/oauth/authorize/";
	private static final String OAUTH_CALLBACK_URL = "instagramble://callback";
	private static final String CLIENT_ID = "あなたのクライアントIDを入れてね";

	private static SharedPreferences pref = null;
	private static String token;

	private static List<String> imageUrls;
	private int imageUrlPointer = 0;
	private boolean imageUpdating = false;
	
	private ImageView mImageView;
	private TextView mTextView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MyLog.d("Main / onCreate");

		APP_NAME = getString(R.string.app_name);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		pref = PreferenceManager.getDefaultSharedPreferences(this);

		token = pref.getString("INSTAGRAM_OAUTH_TOKEN", "");

		if (token.length() > 0) { // 認証済み
			mImageView = (ImageView) findViewById(R.id.imageView);
			mTextView = (TextView) findViewById(R.id.textView);
			try {
				URL url = new URL("https://api.instagram.com/v1/media/popular?"
						+ token + "&count=4");
				HttpURLConnection connect = connect(url);
				BufferedReader br = new BufferedReader(new InputStreamReader(
						connect.getInputStream()));
				StringBuilder sb = new StringBuilder();
				String line = null;
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}

				JSONObject jo = new JSONObject(sb.toString());
				MyLog.i("" + jo.toString());
				JSONArray ja = jo.getJSONArray("data");

				List<String> ius = new ArrayList<String>();

				for (int i = 0; i < ja.length(); i++) {
					// フィードから画像URLを取り出しリストに格納
					String imageUrl = ja.getJSONObject(i)
							.getJSONObject("images")
							.getJSONObject("standard_resolution")
							.getString("url");
					MyLog.i("" + imageUrl);
					ius.add(imageUrl);
				}

				imageUrls = ius;

			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}

			// GUI更新のためのハンドラ
			final Handler handler = new Handler();

			// 画像を更新するタイマータスク
			TimerTask imageUpdateTimerTask = new TimerTask() {
				@Override
				public void run() {
					final Bitmap bm = nextImage();
					if (bm != null) {
						handler.post(new Runnable() {
							@Override
							public void run() {
								mImageView.setImageBitmap(bm);
								mImageView.invalidate();
								mTextView.setText(imageUrls.get(imageUrlPointer));
							}
						});
					}
					imageUpdating = false;
				}
			};

			Timer imageUpdateTimer = new Timer();
			imageUpdateTimer.scheduleAtFixedRate(imageUpdateTimerTask, 0,
					10 * 1000);

		} else { // 認証へ
			String oauthUrl = OAUTH_AUTHORIZE_URL + "?client_id=" + CLIENT_ID
					+ "&redirect_uri=" + URLEncoder.encode(OAUTH_CALLBACK_URL)
					+ "&response_type=token";
			MyLog.d("oauthUrl = " + oauthUrl);
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl)));
		}
	}
	
	/**
	 * 認証完了時に呼び出し
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		MyLog.d("Main / onNewIntent");

		super.onNewIntent(intent);

		Uri uri = intent.getData();

		if (uri != null && uri.toString().startsWith(OAUTH_CALLBACK_URL)) {
			MyLog.d("Main / uri=" + uri);

			token = (uri.toString()).split("#")[1];
			MyLog.i("Main / token=" + token);
			Editor editor = pref.edit();
			editor.putString("INSTAGRAM_OAUTH_TOKEN", token).commit();
		}
	}

	/**
	 * 次に表示する画像を取得
	 * 
	 * @return
	 */
	private Bitmap nextImage() {
		if (imageUrls == null) {
			return null;
		}
		try {
			String u = imageUrls.get(imageUrlPointer);
			Bitmap bmp = BitmapFactory
					.decodeStream(connect(u).getInputStream());
			imageUrlPointer = (imageUrlPointer + 1) % imageUrls.size();

			return bmp;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private HttpURLConnection connect(URL url) throws IOException {
		HttpURLConnection http = null;

		if (url.getProtocol().toLowerCase().equals("https")) {
			trustAllHosts();
			HttpsURLConnection https = (HttpsURLConnection) url
					.openConnection();
			https.setHostnameVerifier(DO_NOT_VERIFY);
			http = https;
		} else {
			http = (HttpURLConnection) url.openConnection();
		}

		return http;
	}

	private HttpURLConnection connect(String u) {
		try {
			return connect(new URL(u));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	private static void trustAllHosts() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}