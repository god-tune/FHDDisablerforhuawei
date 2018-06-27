package com.masterisk.disablefld;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class OverlayJobService extends JobService {
	WindowManager windowManager;
	View coveredView;

	Thread observeThread;

	AlarmManager alarmManager;
	PendingIntent pendingIntent;

	boolean screenOn=true;
	BroadcastReceiver screenActionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action != null) {
				if (action.equals(Intent.ACTION_SCREEN_ON)) {
					// 画面ON時
					Log.d("OverlayService", "SCREEN_ON");
					screenOn=true;
					startObservation();
				} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
					// 画面OFF時
					Log.d("OverlayService", "SCREEN_OFF");
					screenOn=false;
					stopObservation();
				}
			}
		}
	};

	boolean isFullscreen=false;
	FullscreenMonitoringView fullscreenMonitoringView;
	FullscreenMonitoringView.OnSizeChangedListener onSizeChangedListener=new FullscreenMonitoringView.OnSizeChangedListener() {
		@Override
		public void onSizeChanged(int w, int h, int oldw, int oldh) {
			Log.d("OverlayService", "OnSizeChangedListener.onSizeChanged("+w+","+h+","+oldw+","+oldh+")");

			if( (isFullscreen != (w>h && w==2160) || (w<h && h==2160)) && coveredView!=null){
				isFullscreen=(w>h && w==2160) || (w<h && h==2160);
				removeOverlay();
				setOverlay();
			}
		}
	};

	//portraitのときtrue;
	boolean orientation=true;
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if(orientation!=(newConfig.orientation==Configuration.ORIENTATION_PORTRAIT)){
			orientation=newConfig.orientation==Configuration.ORIENTATION_PORTRAIT;

			Log.d("OverlayService","onConfigurationChanged:"+orientation);

			if(coveredView!=null){
				removeOverlay();
				setOverlay();
			}
		}
	}

	@Override
	public boolean onStartJob(final JobParameters params) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				observe();
				jobFinished(params, true);
			}
		}).start();
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		jobFinished(params, false);
		return false;
	}


	void startObservation(){}
	void stopObservation(){}

	void observe() {
		Log.d("OverlayService", "observe()");
		final Handler handler = new Handler();
		String foregroundApp;

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "observation");
		wakeLock.acquire();

		foregroundApp = getForegroundApp(1000000);
		if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
				.getStringSet("enabledApp", new HashSet<String>()).contains(foregroundApp)) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplication(), "指定アプリの起動を検出", Toast.LENGTH_SHORT).show();
					setOverlay();
				}
			});
		}

		while (true) {
			if (observeThread == null) break;


			Log.d("OverlayService", "observing now:" + System.currentTimeMillis());

			String fgTmp = getForegroundApp(2000);//fgTmpはよくnullになるので注意（時間が短いため）
			if (fgTmp == null) {
			} else if (foregroundApp == null || !foregroundApp.equals(fgTmp)) {
				foregroundApp = fgTmp;
				Log.d("OverlayService", "fgTmp=" + fgTmp);
				Set<String> enabledApp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
						.getStringSet("enabledApp", new HashSet<String>());

				if (enabledApp.contains(foregroundApp)) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplication(), "指定アプリの起動を検出", Toast.LENGTH_SHORT).show();
							setOverlay();
						}
					});
				} else {
					handler.post(new Runnable() {
						@Override
						public void run() {
							removeOverlay();
						}
					});
				}
			}
		}
	}

	String getForegroundApp(long timeRange){
		//現在からtimeRange[ms]前までの使用履歴から探す
		long start=System.currentTimeMillis()-timeRange;
		long end=System.currentTimeMillis();

		UsageStatsManager stats = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE/*"usagestats"*/);
		UsageEvents usageEvents = stats.queryEvents(start, end);//usegeEventsのうち後ろにあるほど新しいevent
		UsageEvents.Event event=null;

		while (usageEvents.hasNextEvent()) {
			UsageEvents.Event tmp = new android.app.usage.UsageEvents.Event();
			usageEvents.getNextEvent(tmp);

			switch(tmp.getEventType()){
				case UsageEvents.Event.MOVE_TO_BACKGROUND:
					break;
				case UsageEvents.Event.MOVE_TO_FOREGROUND:
					event=tmp;
					break;
				default:
					break;
			}
		}
		return event==null?null:event.getPackageName();
	}

	void setOverlay(){
		Log.d("OverlayService", "setOverlay()");

		//フルスクリーン検出のView
		fullscreenMonitoringView=new FullscreenMonitoringView(this);
		WindowManager.LayoutParams fullscreenParams= new WindowManager.LayoutParams (
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);
		fullscreenMonitoringView.SetOnSizeChangedListener(onSizeChangedListener);
		windowManager.addView(fullscreenMonitoringView, fullscreenParams);


		//ボタンを隠すView
		LayoutInflater layoutInflater = LayoutInflater.from(this);
		coveredView=layoutInflater.inflate(R.layout.layout_overlay, null);

		WindowManager.LayoutParams params=null;
		switch (getResources().getConfiguration().orientation){
			case Configuration.ORIENTATION_PORTRAIT:
				params = new WindowManager.LayoutParams (
						WindowManager.LayoutParams.MATCH_PARENT,
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
				//フルスクリーン時のmargin
				if(isFullscreen){
					ViewGroup.MarginLayoutParams margin=(ViewGroup.MarginLayoutParams) coveredView.findViewById(R.id.textView).getLayoutParams();
					margin.setMargins(0,0,0,125);
					coveredView.findViewById(R.id.textView).setLayoutParams(margin);
				}
				break;
			case Configuration.ORIENTATION_LANDSCAPE:
				params = new WindowManager.LayoutParams (
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.MATCH_PARENT,
						WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
				//フルスクリーン時のmargin
				if(isFullscreen){
					ViewGroup.MarginLayoutParams margin=(ViewGroup.MarginLayoutParams) coveredView.findViewById(R.id.textView).getLayoutParams();
					margin.setMargins(0,0,125,0);
					coveredView.findViewById(R.id.textView).setLayoutParams(margin);
				}
				break;
		}
		params.gravity= Gravity.BOTTOM | Gravity.RIGHT;
		windowManager.addView(coveredView, params);
	}
	void removeOverlay(){
		Log.d("OverlayService", "removeOverlay()");
		if(coveredView!=null){
			windowManager.removeView(coveredView);
			coveredView=null;
		}
		if(fullscreenMonitoringView!=null){
			windowManager.removeView(fullscreenMonitoringView);
			fullscreenMonitoringView=null;
		}
	}
}
