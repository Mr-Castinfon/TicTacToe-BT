package com.example.tictactoe_bt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION = 2;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ArrayAdapter<String> deviceListAdapter;
    private ListView listView;
    private Button connectButton, sendButton;
    private EditText messageInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listView = findViewById(R.id.device_list);
        connectButton = findViewById(R.id.connect_button);
        sendButton = findViewById(R.id.send_button);
        messageInput = findViewById(R.id.message_input);
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(deviceListAdapter);

        checkBluetoothSupport();
        listBondedDevices();
        discoverDevices();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = (String) parent.getItemAtPosition(position);
            String address = deviceInfo.substring(deviceInfo.length() - 17); // Extract MAC address
            selectedDevice = bluetoothAdapter.getRemoteDevice(address);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Toast.makeText(this, "Selected: " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
        });

        connectButton.setOnClickListener(v -> connectToDevice());
        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void checkBluetoothSupport() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void listBondedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            deviceListAdapter.add(device.getName() + "\n" + device.getAddress());
        }
    }

    private void discoverDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_PERMISSION);
            return;
        }
        bluetoothAdapter.startDiscovery();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    deviceListAdapter.add(device.getName() + "\n" + device.getAddress());
                    deviceListAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    private void connectToDevice() {
        if (selectedDevice == null) {
            Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothSocket = selectedDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to " + selectedDevice.getName(), Toast.LENGTH_SHORT).show());
                listenForMessages();
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendMessage() {
        if (outputStream == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String message = messageInput.getText().toString();
            outputStream.write(message.getBytes());
            Toast.makeText(this, "Sent: " + message, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show();
        }
    }

    private void listenForMessages() {
        byte[] buffer = new byte[1024];
        int bytes;
        while (true) {
            try {
                bytes = inputStream.read(buffer);
                String receivedMessage = new String(buffer, 0, bytes);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Received: " + receivedMessage, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }
}
