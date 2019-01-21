package com.example.luigidarco.serverlessproject;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient mFusedLocationClient;

    private SharedPreferences sp;

    private Button emergencyButton;
    private LinearLayout nameLayout;
    private EditText editName;
    private Button saveName;

    private MqttAndroidClient clientMqtt;
    private MqttConnectOptions options;

    //CloudMQTT
    /*
    private String serverURI = "tcp://m15.cloudmqtt.com:10878";
    private String username = "pdqazret";
    private String password = "Ho1GRTbYFktu";
    private String clientID;
    */

    //RabbitMQ
    private String serverURI = "tcp://172.19.24.138:1883";
    private String username = "guest";
    private String password = "guest";
    private String clientID;

    public String memberName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nameLayout = findViewById(R.id.nameLayout);
        emergencyButton = findViewById(R.id.buttonEmergency);
        editName = findViewById(R.id.editName);
        saveName = findViewById(R.id.saveName);

        saveName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               String insertedName = editName.getText().toString();
               if (insertedName == "") {
                   Toast.makeText(MainActivity.this, "Please insert a valid name", Toast.LENGTH_SHORT).show();
               } else {
                   sp.edit().putString("username", insertedName).commit();
                   finish();
                   startActivity(getIntent());
               }
            }
        });


        //Get the member name
        sp = getSharedPreferences("SavedName", Context.MODE_PRIVATE);
        memberName = sp.getString("username", "");
        if (memberName == "") {
            nameLayout.setVisibility(View.VISIBLE);
        } else {
            emergencyButton.setVisibility(View.VISIBLE);
        }

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

                getLocationAndPublishMessage();
            }
        });

    }

    public void getLocationAndPublishMessage() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Please check the permission", Toast.LENGTH_SHORT).show();

        } else {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                double lat = location.getLatitude();
                                double lon = location.getLongitude();

                                String message = "http://maps.google.com/maps?saddr=" + lat + "," + lon;

                                //String address = getAddress(lat, lon);

                                sendMqtt(memberName, message);

                            }
                        }
                    });
        }
    }


    private void sendMqtt(String name, String address) {

        //final String jsonMessage = "{\"value1\":\""+name+"\", \"value2\": \""+location+"\"}";

        final JSONObject jsonMessage = new JSONObject();
        try {
            jsonMessage.put("value1", name);
            jsonMessage.put("value2", address);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }


        try {
            clientMqtt.connect(options).setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken mqttToken) {
                    Log.d("LOG", "Client connected");
                    Log.d("LOG", "Topics=" + mqttToken.getTopics());

                    MqttMessage message = new MqttMessage(jsonMessage.toString().getBytes());
                    message.setQos(0);
                    message.setRetained(false);

                    try {
                        IMqttDeliveryToken tokenInvio = clientMqtt.publish("iot/messages", message);

                        Log.d("LOG", "Message published");
                        Toast.makeText(MainActivity.this, "Message published", Toast.LENGTH_SHORT).show();

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