package com.example.david.balanace_bt_hmi;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by david on 1/15/2018.
 */

public class PreferencesActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_settings);
    }
}