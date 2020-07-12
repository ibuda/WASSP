package eu.witness.polarh10data;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.UploadFileCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;


import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.errors.PolarInvalidArgument;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.FileMetadata;

public class HRActivity extends AppCompatActivity implements SensorEventListener{

    TextView textViewHR, textViewFW, textViewStatus;
    int reconnectRetries = 0;
    Button exportButton;
    private String TAG = "Polar_HRActivity";
    public PolarBleApi api;
    private Context classContext = this;
    private String DEVICE_ID;
    StringBuilder hrData =  new StringBuilder();
    final SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SensorManager sensorManager;
    SensorData sensorData = new SensorData();


    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sensorData.accelerometer = new Vector3D(event.values);
                break;
            case Sensor.TYPE_GRAVITY:
                sensorData.gravity = new Vector3D(event.values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                sensorData.gyroscope = new Vector3D(event.values);
                break;
            case Sensor.TYPE_LIGHT:
                sensorData.light = new Vector1D(event.values);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                sensorData.linearAcceleration = new Vector3D(event.values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                sensorData.magneticField = new Vector3D(event.values);
                break;
            case Sensor.TYPE_PROXIMITY:
                sensorData.proximity = new Vector1D(event.values);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                sensorData.rotationVector = new Vector3D(event.values);
                break;
            default:
                return;
        }
        // this code adds sensor data even if there is no hr band
        /*try {
            Date date = new Date();
            hrData.append("\n"+formatter.format(date) + "," + 0 + "," + 0 + "," + 0 +","+getParsedSensorData().accelerometer+","+getParsedSensorData().gravity+","+getParsedSensorData().gyroscope+","+getParsedSensorData().light+","+getParsedSensorData().linearAcceleration+","+getParsedSensorData().magneticField+","+getParsedSensorData().proximity+","+getParsedSensorData().rotationVector);
        }catch (NullPointerException e){
            e.printStackTrace();
        }*/

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private class Vector1D {
        public Vector1D(float[] values) {
            this.value = values[0];
        }

        Float value;
    }

    private class Vector3D {
        public Vector3D(float[] values) {
            value = new Float[3];
            for (int i = 0; i != 3; i++)
                this.value[i] = values[i];
        }

        Float[] value;
    }

    public class SensorData{
        Vector3D accelerometer;
        Vector3D gravity;
        Vector3D gyroscope;
        Vector1D light;
        Vector3D linearAcceleration;
        Vector3D magneticField;
        Vector1D proximity;
        Vector3D rotationVector;
    }

    public class ParsedSensorData{
        String accelerometer;
        String gravity;
        String gyroscope;
        String light;
        String linearAcceleration;
        String magneticField;
        String proximity;
        String rotationVector;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        DEVICE_ID = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info2);
        textViewStatus = findViewById(R.id.info3);
        textViewFW = findViewById(R.id.fw2);




        exportButton = findViewById(R.id.button);
        exportButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                exportData(hrData);
            }
        });

        hrData.append("TimeStamp,hr,rrs,rrsMs,accelerometer,gravity,gyroscope,light,linearAcceleration,magneticField,proximity,rotationVector");

        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);



        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "--------------------- BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(classContext, R.string.connected,
                        Toast.LENGTH_SHORT).show();
                textViewStatus.setText("Connected: " + reconnectRetries);
                appendData(1);
                reconnectRetries += 1;
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {
                appendData(0);
                textViewStatus.setText("Connecting");
            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG, "-----------------------Device disconnected " + s);
                Toast.makeText(classContext, R.string.disconnected,
                        Toast.LENGTH_SHORT).show();
                textViewStatus.setText("Disconnected");
//                appendData(-1);
//                try {
//                    reconnectRetries += 1;
//                    api.setAutomaticReconnection(true);
//                    api.connectToDevice(DEVICE_ID);
//                } catch (PolarInvalidArgument a){
//                    a.printStackTrace();
//                }
            }

            @Override
            public void ecgFeatureReady(String s) {
//                Log.d(TAG, "ECG Feature ready " + s);
            }

            @Override
            public void accelerometerFeatureReady(String s) {
//                Log.d(TAG, "ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(String s) {
//                Log.d(TAG, "PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(String s) {
//                Log.d(TAG, "PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
//                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void disInformationReceived(String s, UUID u, String s1) {
                if( u.equals(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    textViewFW.setText(msg + "\n");
                }
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
//                String msg = "ID: " + s + "\nBattery level: " + i;
//                Log.d(TAG, "Battery level " + s + " " + i);
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
//                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                List<Integer> rrsMs = polarHrData.rrsMs;
                appendData(polarHrData);

                String msg = polarHrData.hr + "\n";
                for (int i : rrsMs) {
                    msg += i + ",";
                }
                if (msg.endsWith(",")) {
                    msg = msg.substring(0, msg.length() - 1);
                }
                textViewHR.setText(msg);
            }

            @Override
            public void polarFtpFeatureReady(String s) {
//                Log.d(TAG, "Polar FTP ready " + s);
            }
        });

        try {
            api.connectToDevice(DEVICE_ID);
            api.setAutomaticReconnection(true);
        } catch (PolarInvalidArgument a){
            a.printStackTrace();
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        setupSensorListeners();
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
        api = null;
    }

    private void appendData(PolarHrData pData) {
        Date date = new Date();
        // Adding info to hrData
        List<Integer> rrsMs = pData.rrsMs;
        List<Integer> rrs = pData.rrs;
        hrData.append("\n" + formatter.format(date) + "," + pData.hr + "," + rrs.get(0) + "," + rrsMs.get(0)+","+getParsedSensorData().accelerometer+","+getParsedSensorData().gravity+","+getParsedSensorData().gyroscope+","+getParsedSensorData().light+","+getParsedSensorData().linearAcceleration+","+getParsedSensorData().magneticField+","+getParsedSensorData().proximity+","+getParsedSensorData().rotationVector);
    }

    private void appendData(int code) {
        Date date = new Date();
        // Adding info to hrData
        hrData.append("\n" + formatter.format(date) + "," + code +",,");
    }

    private ParsedSensorData getParsedSensorData(){
        ParsedSensorData parsedData = new ParsedSensorData();
        try {
            parsedData.accelerometer = "["+sensorData.accelerometer.value[0]+" "+sensorData.accelerometer.value[1]+" "+sensorData.accelerometer.value[2]+"]";
            parsedData.gravity = "["+sensorData.gravity.value[0]+" "+sensorData.gravity.value[1]+" "+sensorData.gravity.value[2]+"]";
            parsedData.gyroscope = "["+sensorData.gyroscope.value[0]+" "+sensorData.gyroscope.value[1]+" "+sensorData.gyroscope.value[2]+"]";
            parsedData.linearAcceleration = "["+sensorData.linearAcceleration.value[0]+" "+sensorData.linearAcceleration.value[1]+" "+sensorData.linearAcceleration.value[2]+"]";
            parsedData.magneticField = "["+sensorData.magneticField.value[0]+" "+sensorData.magneticField.value[1]+" "+sensorData.magneticField.value[2]+"]";
            parsedData.rotationVector = "["+sensorData.rotationVector.value[0]+" "+sensorData.rotationVector.value[1]+" "+sensorData.rotationVector.value[2]+"]";
            parsedData.proximity = sensorData.proximity.value.toString();
            parsedData.light = sensorData.proximity.value.toString();

        }catch (NullPointerException e){
            e.printStackTrace();
        }
        return parsedData;
    }
    private void  setupSensorListeners(){
        int period = 0;
        Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, s, period);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, s, period);
    }

    private void exportData(StringBuilder sb){
        try{
            // saving data to local file
            FileOutputStream out = openFileOutput("data.csv", Context.MODE_PRIVATE);
            out.write((sb.toString()).getBytes());
            out.close();
            File filelocation = new File(getFilesDir(), "data.csv");
            FileInputStream fileForUpload = new FileInputStream(filelocation);

            // sending file to box
//            BoxAPIConnection boxAPI = new BoxAPIConnection("k0x2bh4zhJuTD4onWwIIb17MIPdZBe8k");
//            BoxAPIConnection boxAPI = new BoxAPIConnection("36inmbc0hf6cv4t5mnjihq19gjc4z567", "HUQnc6Vy2VrVWe1f1pnwUi0ywH8wAMBj");
//            BoxFolder rootFolder = BoxFolder.getRootFolder(boxAPI);
//            Log.v("Api", String.valueOf(rootFolder));
            // Generating current timestamp
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyddmmHHmmss");
            Date date = new Date();
            String ts = formatter.format(date);
            String filename = "/app"+ts+".csv";
//
//            BoxFile.Info newFileInfo = rootFolder.uploadFile(fileForUpload, filename);
//            fileForUpload.close();

            // dropbox flie upload
            String ACCESS_TOKEN = "pUmTFL9YSeEAAAAAAAAA4G54K1isT3FzezFoJUYCzL0R1qbX5J3LNU_-OLxxeNj6";
            DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
            DbxClientV2 client = new DbxClientV2(config, ACCESS_TOKEN);

            try (InputStream in = new FileInputStream(filelocation)) {
                FileMetadata metadata = client.files().uploadBuilder(filename)
                        .uploadAndFinish(in);
            }


        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
