package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Get the help message from the intent
        String helpMessage = getIntent().getStringExtra("helpMessage");

        // Find the TextView in the layout and set the help message
        TextView helpTextView = findViewById(R.id.helpTextView);
        helpTextView.setText(helpMessage);

        // Set the click listener for the back button
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
    }
}

