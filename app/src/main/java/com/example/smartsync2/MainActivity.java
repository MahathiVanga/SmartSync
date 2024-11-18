package com.example.smartsync2;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference databaseReference;
    private Handler handler = new Handler();
    private TextView logs; // Declare logs as a class-level field

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Database Reference
        databaseReference = FirebaseDatabase.getInstance().getReference("device");

        // Initialize UI elements
        TextView deviceStatus = findViewById(R.id.tv_device_status);
        SwitchCompat deviceSwitch = findViewById(R.id.switch_device);
        TimePicker startTimePicker = findViewById(R.id.time_picker_start);
        TimePicker endTimePicker = findViewById(R.id.time_picker_end);
        logs = findViewById(R.id.tv_logs); // Initialize logs
        Button setScheduleButton = findViewById(R.id.btn_set_schedule);

        // Enable 12-hour format for TimePickers
        startTimePicker.setIs24HourView(false);
        endTimePicker.setIs24HourView(false);

        // Real-time Firebase listener for device status
        databaseReference.child("status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.getValue(String.class);
                    deviceStatus.setText(String.format("Device Status: %s", status != null ? status : "UNKNOWN"));
                    deviceSwitch.setChecked("ON".equals(status));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                logs.append("Error: " + error.getMessage() + "\n");
            }
        });

        // Toggle device state manually
        deviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String status = isChecked ? "ON" : "OFF";
            updateDeviceStatusInFirebase(status);
        });

        // Set schedule functionality
        setScheduleButton.setOnClickListener(v -> {
            // Get Start and End times
            int startHour = startTimePicker.getHour();
            int startMinute = startTimePicker.getMinute();
            int endHour = endTimePicker.getHour();
            int endMinute = endTimePicker.getMinute();

            // Save schedule in Firebase
            saveScheduleInFirebase(startHour, startMinute, endHour, endMinute);

            // Start monitoring the schedule
            monitorSchedule(startHour, startMinute, endHour, endMinute);
        });

        // Start monitoring the schedule at app launch
        startScheduleMonitoring();
    }

    private void updateDeviceStatusInFirebase(String status) {
        databaseReference.child("status").setValue(status)
                .addOnSuccessListener(aVoid -> logs.append("Device status updated to " + status + "\n"))
                .addOnFailureListener(e -> logs.append("Failed to update device status: " + e.getMessage() + "\n"));
    }

    private void saveScheduleInFirebase(int startHour, int startMinute, int endHour, int endMinute) {
        String startTime = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute);
        String endTime = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute);

        databaseReference.child("schedule").child("start").setValue(startTime);
        databaseReference.child("schedule").child("end").setValue(endTime);

        logs.append("Schedule set: Start - " + startTime + ", End - " + endTime + "\n");
        Toast.makeText(this, "Schedule set successfully!", Toast.LENGTH_SHORT).show();
    }

    private void monitorSchedule(int startHour, int startMinute, int endHour, int endMinute) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Calendar calendar = Calendar.getInstance();
                int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
                int currentMinute = calendar.get(Calendar.MINUTE);

                int totalStartMinutes = (startHour * 60) + startMinute;
                int totalEndMinutes = (endHour * 60) + endMinute;
                int totalCurrentMinutes = (currentHour * 60) + currentMinute;

                if (totalCurrentMinutes == totalStartMinutes) {
                    updateDeviceStatusInFirebase("ON");
                } else if (totalCurrentMinutes == totalEndMinutes) {
                    updateDeviceStatusInFirebase("OFF");
                }

                // Schedule the next check after 1 minute
                handler.postDelayed(this, 60000);
            }
        });
    }

    private void startScheduleMonitoring() {
        databaseReference.child("schedule").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String startTime = snapshot.child("start").getValue(String.class);
                    String endTime = snapshot.child("end").getValue(String.class);

                    if (startTime != null && endTime != null) {
                        String[] startParts = startTime.split(":");
                        String[] endParts = endTime.split(":");

                        int startHour = Integer.parseInt(startParts[0]);
                        int startMinute = Integer.parseInt(startParts[1]);
                        int endHour = Integer.parseInt(endParts[0]);
                        int endMinute = Integer.parseInt(endParts[1]);

                        monitorSchedule(startHour, startMinute, endHour, endMinute);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load schedule.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
