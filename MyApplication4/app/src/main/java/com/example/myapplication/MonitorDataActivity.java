package com.example.myapplication;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MonitorDataActivity extends AppCompatActivity {

    private Button btnStartDataMonitor;
    private EditText etIntervalSeconds;
    private TextView tvLog;
    private MainActivity mainActivity;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor_data);

        btnStartDataMonitor = findViewById(R.id.btnStartDataMonitor);
        etIntervalSeconds = findViewById(R.id.etIntervalSeconds);
        tvLog = findViewById(R.id.tvLog);
        mainActivity = MainActivity.getInstance();

        btnStartDataMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isMonitoring) startDataMonitor();
                else showToast("There is already a monitoring process running, check your spreadsheet to verify its working!");
            }
        });

        Button btnExit = findViewById(R.id.btnExit);
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity.stopDataMonitoring();
                finish();
            }
        });

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "Here, you can set how often you want the app to collect data from the marked characteristics " +
                    "(in seconds), then just click monitor data, if you did everything correctly, data should start showing up in your spreadsheet!";
            Intent intent = new Intent(MonitorDataActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });
    }

    private void startDataMonitor() {
        String intervalStr = etIntervalSeconds.getText().toString().trim();

        if (intervalStr.isEmpty()) {
            showToast("Please enter an interval.");
            return;
        }
            try {
                int intervalSeconds = Integer.parseInt(intervalStr);
                isMonitoring = true;
                mainActivity.monitorData(intervalSeconds, tvLog);
            } catch (NumberFormatException e) {
                showToast("Invalid interval input.");
            }
        }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}