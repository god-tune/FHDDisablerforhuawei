package com.masterisk.disablefld;

import android.content.Context;
import android.view.View;

public class FullscreenMonitoringView extends View {


	OnSizeChangedListener sizeChangedListener;

	public interface OnSizeChangedListener{
		public void onSizeChanged(int w, int h, int oldw, int oldh);
	}

	public FullscreenMonitoringView(Context context) {
		super(context);
	}

	public void SetOnSizeChangedListener(OnSizeChangedListener listener){
		sizeChangedListener=listener;
	}


	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		sizeChangedListener.onSizeChanged(w, h, oldw,oldh );
	}


}
