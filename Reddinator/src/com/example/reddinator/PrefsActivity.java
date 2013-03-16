package com.example.reddinator;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.Menu;

public class PrefsActivity extends PreferenceActivity {

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	   super.onCreate(savedInstanceState);
	   addPreferencesFromResource(R.xml.prefs);
	}

}
