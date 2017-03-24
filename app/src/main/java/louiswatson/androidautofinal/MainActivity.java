package louiswatson.androidautofinal;

//imports required
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static boolean serviceOn;
    private static Button button;
    private static TextView textServiceStatus;
    private static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //checking if service is running to proceed
                serviceOn = isMyServiceRunning(MainService.class);
        Log.d(TAG, "first onCreate: serviceOn is " + serviceOn);
        button = (Button)findViewById(R.id.button);
        textServiceStatus = (TextView)findViewById(R.id.serviceStatus);
        updateInfo();

        //On button click, the messaging service is turned on
        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if(!serviceOn) {
                    //if service isn't on, call startMainService which will begin service
                    startMainService();
                }
                else {
                    //If service is on, then stop service
                    stopMainService();
                }
                //Logging that the service is on
                serviceOn = !serviceOn;
                Log.d(TAG, "serviceOn is " + serviceOn);
                updateInfo();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        updateInfo();
    }

    private void startMainService(){
        startService(new Intent(this, MainService.class));
    }

    private void stopMainService(){
        stopService(new Intent(this, MainService.class));
    }

    private void updateInfo(){
        if(!serviceOn) {
            //if ServiceOn is called, change the button to the start button
            //Also change the textview box to say the following
            button.setBackgroundResource(R.drawable.start_button);
            textServiceStatus.setText("Android Auto Messaging is off");
        }
        else {
            //If else, change the button to stop_button
            //Also change text to the following
            button.setBackgroundResource(R.drawable.stop_button);
            textServiceStatus.setText("Android Auto Messaging is on");
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }}

