package bmoore.encryptext.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.CheckBox;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;

/**
 * Created by Benjamin Moore on 12/27/2015.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.settings_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar bar = getSupportActionBar();
        bar.setDisplayHomeAsUpEnabled(true);

        CheckBox useHeadsUpVal = (CheckBox) findViewById(R.id.settings_use_heads_up_val);

        SharedPreferences prefs = getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE);

        if(!prefs.contains(EncrypText.USE_HEADS_UP_NOTIFICATIONS)) {
            useHeadsUpVal.setChecked(true);
            prefs.edit().putBoolean(EncrypText.USE_HEADS_UP_NOTIFICATIONS, true).apply();
        } else {
            boolean checked = prefs.getBoolean(EncrypText.USE_HEADS_UP_NOTIFICATIONS, true);
            useHeadsUpVal.setChecked(checked);
        }
    }

    public void onCheckBoxClicked(View view) {
        if(view.getId() == R.id.settings_use_heads_up_val) {
            SharedPreferences prefs = getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE);
            prefs.edit().putBoolean(EncrypText.USE_HEADS_UP_NOTIFICATIONS, ((CheckBox) view).isChecked()).apply();
        }
    }

}
