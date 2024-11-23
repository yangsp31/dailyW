package com.Yproject.dailyw.ui.notifications;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.Yproject.dailyw.util.weightStructure;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class backWork extends Worker {
    public backWork(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("Bluetooth", Context.MODE_PRIVATE);
        String macAddress = sharedPreferences.getString("device_address", null);
        Context context = getApplicationContext();

        if (macAddress == null) {
            return Result.retry();
        }

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            return Result.failure();
        }

        if (isDeviceConnected(bluetoothManager, bluetoothAdapter, macAddress)) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

            long startTime = System.currentTimeMillis();
            boolean responseReceived = false;

            while (System.currentTimeMillis() - startTime < 5 * 60 * 1000) {
                responseReceived = sendBluetoothDataAndWaitForResponse(device, "A");
                if (responseReceived) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (responseReceived) {
                try {
                    String response = waitForDMessage(4000, device);

                    if ("D".equals(response)) {
                        sendToBluetooth("W", device);

                        String weightData = "";

                        while(Float.parseFloat(weightData) < 3.0) {
                            weightData = readFromBluetooth(device);
                        }

                        float weight = Float.parseFloat(weightData);
                        saveWeightData(weight);

                        return Result.success();
                    } else {
                        return Result.retry();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return Result.retry();
                }
            } else {
                return Result.retry();
            }
        } else {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            boolean isConnected = attemptBluetoothConnection(device);

            if (isConnected) {
                long startTime = System.currentTimeMillis();
                boolean responseReceived = false;

                while (System.currentTimeMillis() - startTime < 5 * 60 * 1000) {
                    responseReceived = sendBluetoothDataAndWaitForResponse(device, "A");
                    if (responseReceived) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (responseReceived) {
                    try {
                        String response = waitForDMessage(4000, device);

                        if ("D".equals(response)) {
                            sendToBluetooth("W", device);

                            String weightData = "";

                            while(Float.parseFloat(weightData) < 3.0) {
                                weightData = readFromBluetooth(device);
                            }

                            float weight = Float.parseFloat(weightData);
                            saveWeightData(weight);

                            return Result.success();
                        } else {
                            return Result.retry();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return Result.retry();
                    }
                } else {
                    return Result.retry();
                }
            } else {
                return Result.retry();
            }
        }
    }

    private boolean isDeviceConnected(BluetoothManager bluetoothManager, BluetoothAdapter bluetoothAdapter, String macAddress) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false; //
        }

        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(macAddress)) {
                int connectionState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT);

                return connectionState == BluetoothAdapter.STATE_CONNECTED;
            }
        }

        return false;
    }

    private boolean attemptBluetoothConnection(BluetoothDevice device) {
        BluetoothSocket bluetoothSocket = null;

        try {
            if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            UUID uuid = device.getUuids()[0].getUuid();
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);

            bluetoothSocket.connect();

            return true;
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        } finally {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean sendBluetoothDataAndWaitForResponse(BluetoothDevice device, String data) {
        BluetoothSocket bluetoothSocket = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        boolean responseReceived = false;

        try {
            if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            UUID uuid = device.getUuids()[0].getUuid();
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);

            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();

            outputStream.write(data.getBytes());
            outputStream.flush();

            long startTime = System.currentTimeMillis();
            byte[] buffer = new byte[1024];
            int bytesRead;

            while (System.currentTimeMillis() - startTime < 100) {
                if ((bytesRead = inputStream.read(buffer)) != -1) {
                    String response = new String(buffer, 0, bytesRead);
                    if (response.equals("A")) {
                        responseReceived = true;

                        break;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return responseReceived;
    }

    private String waitForDMessage(long timeout, BluetoothDevice device) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        String response = null;

        while (System.currentTimeMillis() - startTime < timeout) {
            response = readFromBluetooth(device);
            if ("D".equals(response)) {
                return response;
            }
            Thread.sleep(100);
        }

        return response;
    }

    private String readFromBluetooth(BluetoothDevice device) {
        try {
            if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }

            UUID uuid = device.getUuids()[0].getUuid();
            BluetoothSocket bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);

            InputStream inputStream = bluetoothSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }

    private void sendToBluetooth(String message, BluetoothDevice device) {
        try {
            if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            UUID uuid = device.getUuids()[0].getUuid();
            BluetoothSocket bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);

            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveWeightData(float weight) {
        Gson gson = new Gson();
        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        String currentDateStr = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);

        SharedPreferences sharedPreferencesWeight = getApplicationContext().getSharedPreferences("WeightData", Context.MODE_PRIVATE);

        String json = sharedPreferencesWeight.getString(String.valueOf(calendar.get(Calendar.MONTH + 1)), "[]");
        Type type = new TypeToken<List<weightStructure>>(){}.getType();
        List<weightStructure> weights = gson.fromJson(json, type);

        if(weights != null) {
            weights.add(new weightStructure(weight, currentDate, currentDateStr));

            sharedPreferencesWeight.edit().remove(String.valueOf(calendar.get(Calendar.MONTH + 1))).apply();
            sharedPreferencesWeight.edit().putString(String.valueOf(calendar.get(Calendar.MONTH + 1)), gson.toJson(weights)).apply();
        }
        else {
            weightStructure newRecord = new weightStructure(weight, currentDate, currentDateStr);
            Objects.requireNonNull(weights).add(newRecord);

            sharedPreferencesWeight.edit().putString(String.valueOf(calendar.get(Calendar.MONTH + 1)), gson.toJson(weights)).apply();
        }
    }
}
