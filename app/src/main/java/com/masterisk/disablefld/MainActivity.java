package com.masterisk.disablefld;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

	static final int OVERLAY_PERMISSION_REQ_CODE=2;
	static final int USAGESTATS_PERMISSION_REQ_CODE=3;

	Switch switchService;//サービスのオンオフ

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		switchService=(Switch) findViewById(R.id.switch1);

		SharedPreferences prefs= PreferenceManager.getDefaultSharedPreferences(this);
		switchService.setChecked(prefs.getBoolean("service",false));

		switchService.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Log.d("MainActivity", "onCheckedChanged()");

				PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
						.edit().putBoolean("service", isChecked).apply();

				if(isChecked){
					OverlayService.start(getApplicationContext());
				}else{
					OverlayService.stop(getApplicationContext());
				}
			}
		});

		createAppList();

		if(!checkOverlayPermission()){
			requestOverlayPermission();
		}else if(!checkUsageStatsPermission()){
			requestUsageStatsPermission();
		}
	}


	void createAppList(){
		final LinearLayout list=findViewById(R.id.list_app);
		final Map<Switch,String> packageNameMap=new HashMap<>();
		final Handler handler=new Handler();
		final CompoundButton.OnCheckedChangeListener listener=new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SharedPreferences prefs= PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				SharedPreferences.Editor editor=prefs.edit();
				Set<String> appSet=new HashSet<>(prefs.getStringSet("enabledApp", new HashSet<String>()));
				Log.d(getLocalClassName()+"onCheckedChanged", packageNameMap.get(buttonView));
				if(isChecked){
					appSet.add(packageNameMap.get(buttonView));//パッケージ名の取得方法？？？
				}else{
					appSet.remove(packageNameMap.get(buttonView));
				}
				editor.putStringSet("enabledApp", appSet);
				editor.apply();
			}
		};

		new Thread(new Runnable() {
			@Override
			public void run() {
				final Set<String> enabledApp=PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("enabledApp", new HashSet<String>());
				//インストールされているアプリ一覧取得
				PackageManager packageManager=getPackageManager();
				List<ResolveInfo> appInfo=packageManager.queryIntentActivities(
						new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER),0);

				if(appInfo!=null){
					for(final ResolveInfo info : appInfo){
						if(info.activityInfo.packageName.equals(getPackageName()))	continue;

						final Switch sw=new Switch(getApplicationContext());
						sw.setText(info.loadLabel(packageManager));
						//sw.setChecked(enabledApp.contains(info.activityInfo.packageName));//UIスレッドでないと動かない
						sw.setOnCheckedChangeListener(listener);
						packageNameMap.put(sw, info.activityInfo.packageName);

						handler.post(new Runnable() {
							@Override
							public void run() {
								sw.setChecked(enabledApp.contains(info.activityInfo.packageName));
							}
						});
					}
				}

				final List<Map.Entry> switchList=new ArrayList(packageNameMap.entrySet());
				Collections.sort(switchList, new Comparator<Map.Entry>() {
					@Override
					public int compare(Map.Entry o1, Map.Entry o2) {
						return ((Switch)o1.getKey()).getText().toString().compareTo(((Switch)o2.getKey()).getText().toString());
					}
				});
				handler.post(new Runnable() {
					@Override
					public void run() {
						for(Map.Entry entry : switchList) {
							list.addView((Switch) entry.getKey());
						}
					}
				});
			}
		}).start();

	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(getPackageName(), "onResume()");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(getPackageName(), "onActivityResult()...checkOverlayPermission()="+checkOverlayPermission());
		if(requestCode == OVERLAY_PERMISSION_REQ_CODE && !checkOverlayPermission()){
			requestOverlayPermission();
		}else if(requestCode == OVERLAY_PERMISSION_REQ_CODE && checkOverlayPermission() && !checkUsageStatsPermission()){
			requestUsageStatsPermission();
		} else if(requestCode==USAGESTATS_PERMISSION_REQ_CODE && !checkUsageStatsPermission()){
			requestUsageStatsPermission();
		}

	}

	boolean checkOverlayPermission(){
		return Settings.canDrawOverlays(this);
	}
	void requestOverlayPermission() {
		Toast.makeText(this, "オーバーレイを許可してください", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
			}
		}).start();
	}

	boolean checkUsageStatsPermission(){
		// AppOpsManagerを取得
		AppOpsManager aom = (AppOpsManager)getSystemService(Context.APP_OPS_SERVICE);
		// GET_USAGE_STATSのステータスを取得
		int mode = aom.checkOp(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
				this.getPackageName());
		//LogUtil.d(TAG, "check:" + mode);
		if (mode == AppOpsManager.MODE_DEFAULT) {
			// AppOpsの状態がデフォルトなら通常のpermissionチェックを行う。
			// 普通のアプリならfalse
			return checkPermission("android.permission.PACKAGE_USAGE_STATS",
					Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED;
		}
		// AppOpsの状態がデフォルトでないならallowedのみtrue
		return mode == AppOpsManager.MODE_ALLOWED;
	}
	void requestUsageStatsPermission(){
		Toast.makeText(this, "使用履歴へのアクセスを許可してください", Toast.LENGTH_LONG).show();
		Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
		startActivityForResult(intent, USAGESTATS_PERMISSION_REQ_CODE);
	}
/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
			case R.id.app_select:
				startActivity(new Intent(this, AppSelectActivity.class));
				//return true;
		}

		return super.onOptionsItemSelected(item);
	}*/
}
