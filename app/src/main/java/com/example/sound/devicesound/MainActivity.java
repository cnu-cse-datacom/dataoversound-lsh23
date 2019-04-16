package com.example.sound.devicesound;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;

import calsualcoding.reedsolomon.EncoderDecoder;


public class MainActivity extends AppCompatActivity implements ToneThread.ToneCallback {
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int FEC_BYTES = 4;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;


    ArrayAdapter<String> adapter;
    ArrayList<String> listItems = new ArrayList<String>();
    ArrayAdapter<ListView> stringArrayAdapter;
    ListView listView;



    EditText text;
    View play_tone;
    View listen_tone;
    ProgressBar progress;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text = (EditText) findViewById(R.id.text);
        Log.e("에", text.toString());

        listView = (ListView) findViewById(R.id.message_view);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                listItems);
        listView.setAdapter(adapter);


        play_tone = findViewById(R.id.play_tone);
        listen_tone = findViewById(R.id.listen_tone);


        progress = (ProgressBar) findViewById(R.id.progress);

        play_tone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String message = text.getText().toString();
                if(message.matches("")) {
                    Toast.makeText(MainActivity.this, "메세지를 입력하세요!", Toast.LENGTH_SHORT).show();
                }
                else {
                    sendMessage(message);
                    listItems.add(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

                    adapter.notifyDataSetChanged();
                }
            }
        });

        listen_tone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Listen Start!", Toast.LENGTH_SHORT).show();
                requestAudioPermissions(); }
        }
        );
    }

    public void receiveMessage(){

        Thread receive = new Thread(new Runnable() {

            Listentone recv_tone = new Listentone();

            @Override
            public void run() {

                try {
                    recv_tone.PreRequest();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            }
        });
        Toast.makeText(MainActivity.this, "ThreadStart!", Toast.LENGTH_SHORT).show();

        receive.start();
    }

    public void sendMessage(String message){
        Log.d("Message", message);
        byte[] payload = new byte[0];
        payload = message.getBytes(Charset.forName("UTF-8"));
        Log.d("PayLoad", payload.toString());
        EncoderDecoder encoder = new EncoderDecoder();
        final byte[] fec_payload;
        Log.d("ENCODING", encoder.toString());
        try {
            fec_payload = encoder.encodeData(payload, FEC_BYTES);
        } catch (EncoderDecoder.DataTooLargeException e) {
            return;
        }
        Log.d("FEC_PAYLOAD", fec_payload.toString());
        ByteArrayInputStream bis = new ByteArrayInputStream(fec_payload);
        Log.d("BytpeArrayInputStream", bis.toString());
        play_tone.setEnabled(false);
        ToneThread.ToneIterator tone = new BitstreamToneGenerator(bis, 7);
        Log.d("TONE", tone.toString());
        Thread play_tone  = new ToneThread(tone, MainActivity.this);
        play_tone.start();
        //while(true){
        //try {
         //   Thread.sleep(3000);
          //  if(play_tone.isAlive()==false){
           //     Toast.makeText(this, "listenStart!", Toast.LENGTH_SHORT).show();

               // break;
           // }
       // }
        //catch(InterruptedException e) {
       //     e.printStackTrace();

        //}

      //  }



    }
    private void requestAudioPermissions(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            Toast.makeText(MainActivity.this, "ListenOn Class In Right Away", Toast.LENGTH_SHORT).show();
            receiveMessage();
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    receiveMessage();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    public void onProgress(int current, int total) {
        progress.setMax(total);
        progress.setProgress(current);
    }

    @Override
    public void onDone() {
        play_tone.setEnabled(true);
        progress.setProgress(0);
    }
}
