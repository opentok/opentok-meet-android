package com.opentok.android.meet;

import android.os.Bundle;

import meet.android.opentok.com.opentokmeet.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


public class HomeActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private static final String LOGTAG = "meet.tokbox";

    private static final String LAST_CONFERENCE_DATA = "LAST_CONFERENCE_DATA";

    private String roomName;
    private String username;
    private EditText roomNameInput;
    private EditText usernameInput;
    private String mCapturerResolution;
    private String mCapturerFps;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //restore last used conference data
        restoreConferenceData();

        setContentView(R.layout.main_layout);

        roomNameInput = (EditText) findViewById(R.id.input_room_name);
        roomNameInput.setText(this.roomName);

        usernameInput = (EditText) findViewById(R.id.input_username);
        usernameInput.setText(this.username);


        Spinner capturerResolutionSpinner = (Spinner) findViewById(R.id.combo_capturer_resolution);
        capturerResolutionSpinner.setOnItemSelectedListener(this);
        String[] capturerResolutionValues  = getResources().getStringArray(R.array.pub_capturer_resolution);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.simple_spinner_item, capturerResolutionValues);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        capturerResolutionSpinner.setAdapter(dataAdapter);

        Spinner capturerFpsSpinner =  (Spinner) findViewById(R.id.combo_capturer_fps);
        capturerFpsSpinner.setOnItemSelectedListener(this);
        String[] capturerFpsValues  = getResources().getStringArray(R.array.pub_capturer_fps);
        dataAdapter = new ArrayAdapter<>(this, R.layout.simple_spinner_item, capturerFpsValues);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        capturerFpsSpinner.setAdapter(dataAdapter);

        // OpenTokConfig.setOTKitLogs(true);
        // OpenTokConfig.setJNILogs(true);
        // OpenTokConfig.setWebRTCLogs(true);
    }

    public void joinRoom(View v) {
        Log.i(LOGTAG, "join room button clicked.");

        roomName = roomNameInput.getText().toString();
        username = usernameInput.getText().toString();

        Intent enterChatRoomIntent = new Intent(this, ChatRoomActivity.class);
        enterChatRoomIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, roomName);
        enterChatRoomIntent.putExtra(ChatRoomActivity.ARG_USERNAME_ID, username);
        enterChatRoomIntent.putExtra(ChatRoomActivity.PUB_CAPTURER_RESOLUTION, mCapturerResolution);
        enterChatRoomIntent.putExtra(ChatRoomActivity.PUB_CAPTURER_FPS, mCapturerFps);
        //save room name and username
        saveConferenceData();

        startActivity(enterChatRoomIntent);
    }

    private void saveConferenceData() {

        SharedPreferences settings = getApplicationContext()
                .getSharedPreferences(LAST_CONFERENCE_DATA, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("roomName", roomName);
        editor.putString("username", username);

        editor.apply();
    }

    private void restoreConferenceData() {
        SharedPreferences settings = getApplicationContext()
                .getSharedPreferences(LAST_CONFERENCE_DATA, 0);
        roomName = settings.getString("roomName", "");
        username = settings.getString("username", "");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        Spinner spinner = (Spinner) parent;
        switch (spinner.getId()) {
            case R.id.combo_capturer_resolution:
                mCapturerResolution = parent.getItemAtPosition(position).toString();
                Toast.makeText(parent.getContext(), "Selected: " + mCapturerResolution, Toast.LENGTH_LONG).show();
                break;
            case R.id.combo_capturer_fps:
                mCapturerFps = parent.getItemAtPosition(position).toString();
                Toast.makeText(parent.getContext(), "Selected: " + mCapturerFps, Toast.LENGTH_LONG).show();
                break;
            default: break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
