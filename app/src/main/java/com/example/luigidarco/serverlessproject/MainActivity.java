package com.example.luigidarco.serverlessproject;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient mFusedLocationClient;
    private Button emergencyButton;

    private MqttAndroidClient clientMqtt;
    private MqttConnectOptions options;

    private String serverURI = "tcp://m15.cloudmqtt.com:10878";
    private String username = "pdqazret";
    private String password = "Ho1GRTbYFktu";
    private String clientID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emergencyButton = findViewById(R.id.buttonEmergency);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        //Initialize MQTTClient
        clientID = MqttClient.generateClientId();
        clientMqtt = new MqttAndroidClient(this.getApplicationContext(), serverURI, clientID);

        options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
        options.setCleanSession(false);
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        emergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                getLocation();
                sendMqtt();
            }
        });

    }

    public void getLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Please check the permission", Toast.LENGTH_SHORT).show();

        } else {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                double lat = location.getLatitude();
                                double lon = location.getLongitude();

                                String message = "http://maps.google.com/maps?saddr=" + lat + "," + lon;

                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("test", message);
                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void sendMqtt() {

        try {
            clientMqtt.connect(options).setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken mqttToken) {
                    Log.d("LOG", "Client connected");
                    Log.d("LOG", "Topics=" + mqttToken.getTopics());

                    MqttMessage message = new MqttMessage("Hello, I am Android Mqtt Client.".getBytes());
                    message.setQos(0);
                    message.setRetained(false);

                    try {
                        clientMqtt.publish("iot/messages", message);
                        Log.d("LOG", "Message published");

                        clientMqtt.disconnect();
                        Log.d("LOG", "client disconnected");

                    } catch (MqttPersistenceException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();

                    } catch (MqttException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("LOG", "Client connection failed: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

}