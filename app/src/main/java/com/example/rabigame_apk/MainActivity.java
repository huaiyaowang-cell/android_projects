package com.example.rabigame_apk;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private int clickCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView subtitle = findViewById(R.id.subtitle);
        MaterialButton button = findViewById(R.id.primaryButton);

        button.setOnClickListener(v -> {
            clickCount += 1;
            subtitle.setText(getString(R.string.subtitle_clicks, clickCount));
            Toast.makeText(this, "Clicked " + clickCount, Toast.LENGTH_SHORT).show();
        });
    }
}
