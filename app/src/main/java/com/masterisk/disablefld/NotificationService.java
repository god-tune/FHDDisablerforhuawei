package com.masterisk.disablefld;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class NotificationService extends Service {
	//今使ってない

	public static void start(Context context){
		Intent intent = new Intent(context, NotificationService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Log.d("OverlayService", "NotificationService::start()");
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}
	public static void stop(Context context){
		Intent intent = new Intent(context, NotificationService.class);
		context.stopService(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
			Log.d("OverlayService", "NotificationService::Build.VERSION_CODES.O");
			String channelId = "notification"; // 通知チャンネルのIDにする任意の文字列
			String name = "通知"; // 通知チャンネル名
			int importance = NotificationManager.IMPORTANCE_DEFAULT; // デフォルトの重要度
			NotificationChannel channel = new NotificationChannel(channelId, name, importance);
			channel.setDescription("通知チャンネルの説明"); // 必須ではない


			// NotificationManagerCompatにcreateNotificationChannel()は無い。
			NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
			nm.createNotificationChannel(channel);


			NotificationCompat.Builder first = new NotificationCompat.Builder(getApplicationContext(),channelId);
			first.setSmallIcon(R.mipmap.ic_launcher_round)
					//.setLargeIcon(largeIcon)
					.setContentTitle("FHD+ Disabler NotificationService")
					.setTicker("")
					.setAutoCancel(true)
					//.setVibrate(new long[]{0,100})
					.setContentIntent(
							PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)
					)
			;
			startForeground(3, first.build());//foregroundにする

		}else{

		}


		return START_STICKY;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		stopForeground(Service.STOP_FOREGROUND_REMOVE);
		super.onDestroy();
	}
}
