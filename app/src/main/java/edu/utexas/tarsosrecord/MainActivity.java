package edu.utexas.tarsosrecord;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import be.tarsos.dsp.writer.WriterProcessor;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    final int REQUEST_PERMISSION_CODE = 1000;
    private Button btn_record, btn_file;
    public boolean isRecording = false;

    AudioDispatcher dispatcher;

    final List<float[]> mfccList = new ArrayList<>();
    public float[][] mfccArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!checkPermissionFromDevice())
            requestPermission();

        btn_record = (Button)this.findViewById(R.id.btn_record);
        btn_file = (Button)this.findViewById(R.id.btn_file);
        btn_record.setOnClickListener(this);
        btn_file.setOnClickListener(this);
    }


    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        }, REQUEST_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();

            }
            break;
        }
    }

    private boolean checkPermissionFromDevice() {
        int write_external_storage_result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int record_audio_result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return write_external_storage_result == PackageManager.PERMISSION_GRANTED &&
                record_audio_result == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_record:
                if(!isRecording) {
                    isRecording = true;
                    btn_record.setText("Stop");
                    startAudio();
                }else {
                    isRecording = false;
                    btn_record.setText("Start");
                    stopAudio();
                }
                break;
            case R.id.btn_file:
                try {
                    WriteToFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

        }
    }

    private void WriteToFile() throws IOException {
        mfccArray = new float[mfccList.size()][mfccList.get(0).length];
        int x = mfccArray[0].length-1;
        for(int k = 0; k < mfccList.size(); k++) {
            mfccArray[k] = mfccList.get(k).clone();
        }
        File file = new File(Environment.getExternalStorageDirectory().getPath() + "/AudioTemp/", "mfcc.arff");
        BufferedWriter out = new BufferedWriter(new FileWriter(file));
        out.write("@RELATION sound\n\n");
        for(int f = 0; f < mfccArray[0].length; f++){
            out.write("@ATTRIBUTE f" + f + " NUMERIC\n");
        }
        out.write("\n");
        out.write("@DATA\n");
        for(int i = 0; i < mfccArray.length; i++){
            for(int j = 0; j < mfccArray[0].length-1; j++){
                out.write(mfccArray[i][j] + ",");
            }
            out.write(mfccArray[i][x]+"\n");
        }

        out.close();
    }

    private void stopAudio() {
        dispatcher.stop();

    }

    private void startAudio() {
        int sampleRate = 22050;
        int bufferSize = 2048;
        int bufferOverlap = 0;
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, bufferOverlap);
        TarsosDSPAudioFormat outputFormat = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        File wavfile = new File(Environment.getExternalStorageDirectory().getPath()
                + "/AudioTemp/", "test.wav");
        try {
            RandomAccessFile outputFile = new RandomAccessFile(wavfile, "rw");
            AudioProcessor p1 = new WriterProcessor(outputFormat,outputFile);
            dispatcher.addAudioProcessor(p1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final MFCC mfcc = new MFCC(bufferSize, sampleRate, 20, 50, 300, 3000);


        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                mfccList.add(mfcc.getMFCC());
                return true;
            }

            @Override
            public void processingFinished() {
                System.out.println(Arrays.deepToString(mfccList.toArray()));
                System.out.println(mfccList.size());
            }
        });
        new Thread(dispatcher,"Audio").start();
    }
}
