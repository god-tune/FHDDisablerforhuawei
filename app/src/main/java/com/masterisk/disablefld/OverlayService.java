package com.masterisk.disablefld;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class OverlayService extends Service{
	/*
	細かく見た結果、
	ナビゲーションバーは125px
	全画面無効で横画面アプリを動かした際、横幅は1913px

	よって、「全画面表示」ボタンの幅は122px
	*/

	WindowManager windowManager;
	View coveredView;

	Thread observeThread;

	String channelId = "notification"; // 通知チャンネルのIDにする任意の文字列

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

	public static void start(Context context){
		Intent intent = new Intent(context, OverlayService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}
	public static void stop(Context context){
		Intent intent = new Intent(context, OverlayService.class);
		context.stopService(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		startNotification();

		if(!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("service", false)){
			stopNotification();
			stopSelf();
			return;
		}

		windowManager = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

		orientation=getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT;

		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));
		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));

		if(screenOn){
			startObservation();
		}

		//通知チャンネルの設定
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){

			String name = "通知"; // 通知チャンネル名
			int importance = NotificationManager.IMPORTANCE_DEFAULT; // デフォルトの重要度
			NotificationChannel channel = new NotificationChannel(channelId, name, importance);
			//channel.setDescription("通知チャンネルの説明"); // 必須ではない

			// NotificationManagerCompatにcreateNotificationChannel()は無い。
			NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
			nm.createNotificationChannel(channel);
		}
	}

	void startNotification(){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
			NotificationCompat.Builder first = new NotificationCompat.Builder(getApplicationContext(),channelId);
			first.setSmallIcon(R.mipmap.ic_launcher_round)
					//.setLargeIcon(largeIcon)
					.setContentTitle("FHD+ Disabler OverlayService")
					.setTicker("")
					.setAutoCancel(true)
					.setVibrate(new long[]{0})
					.setContentIntent(
							PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)
					);
			startForeground(1, first.build());//foregroundにする
		}
	}
	void stopNotification(){
		stopForeground(Service.STOP_FOREGROUND_REMOVE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("OverlayService", "onStartCommand()");
		//observe();
		return START_STICKY;
	}


	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	@Override
	public void onDestroy() {
		super.onDestroy();

		stopObservation();
		removeOverlay();
		unregisterReceiver(screenActionReceiver);
		stopNotification();
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


	//起動アプリの監視を開始
	synchronized void startObservation(){
		if(observeThread!=null)	return;

		final Handler handler=new Handler();
		observeThread=new Thread(new Runnable() {
			String foregroundApp;

			@Override
			public void run() {
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				PowerManager.WakeLock wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "observation");
				wakeLock.acquire();

				foregroundApp=getForegroundApp(1000000);
				if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
						.getStringSet("enabledApp", new HashSet<String>()).contains(foregroundApp)) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplication(),"指定アプリの起動を検出",Toast.LENGTH_SHORT).show();
							setOverlay();
						}
					});
				}
				long loop=0;
				NotificationCompat.Builder builder
						= new NotificationCompat.Builder(getApplicationContext(), channelId)
						.setContentTitle("FHD+ Disabler OverlayService")
						.setSmallIcon(R.mipmap.ic_launcher);
				while(true){
					if(observeThread==null)	break;


					if(loop++%10==0){
						//通知	threadを止めないため
						NotificationManagerCompat.from(getApplicationContext()).notify(1, builder.build());
					}

					Log.d("OverlayService", "observing now:"+System.currentTimeMillis());

					String fgTmp=getForegroundApp(10000);//fgTmpはよくnullになるので注意（時間が短いため）
					if(fgTmp==null){
					}else if(foregroundApp==null || !foregroundApp.equals(fgTmp)){
						foregroundApp=fgTmp;
						Log.d("OverlayService", "fgTmp="+fgTmp);
						Set<String> enabledApp=PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
								.getStringSet("enabledApp", new HashSet<String>());

						if(enabledApp.contains(foregroundApp)) {
							handler.post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(getApplication(),"指定アプリの起動を検出",Toast.LENGTH_SHORT).show();
									setOverlay();
								}
							});
						}
						else{
							handler.post(new Runnable() {
								@Override
								public void run() {
									removeOverlay();
								}
							});
						}
					}

					try {
						Thread.sleep(350);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				wakeLock.release();
				Log.d("OverlayService", "observing stopped");
			}
		});
		observeThread.setPriority(Thread.MAX_PRIORITY);
		observeThread.start();
/*
		alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
		Intent intent=new Intent(this,OverlayService.class);
		pendingIntent
				= PendingIntent.getService(this, 0, intent, 0);
		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000*10, pendingIntent);*/
	}
	//起動アプリの監視を停止
	void stopObservation(){
		observeThread=null;
		//alarmManager.cancel(pendingIntent);
	}


	synchronized void setOverlay(){
		Log.d("OverlayService", "setOverlay()");
		if(coveredView!=null)	return;

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
	synchronized void removeOverlay(){
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
