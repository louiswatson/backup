package louiswatson.androidautofinal;

//imports required
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static boolean serviceOn;
    private static Button button;
    private static TextView textServiceStatus;
    private static final String TAG = "MainActivity";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //checking if service is running to proceed
                serviceOn = isMyServiceRunning(MainService.class);
        Log.d(TAG, "first onCreate: serviceOn is " + serviceOn);
        button = (Button)findViewById(R.id.button);
        textServiceStatus = (TextView)findViewById(R.id.serviceStatus);

        updateViews();

        //On button click, the messaging service is turned on
        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Log.d(TAG, " Button Pressed!!");
                if(!serviceOn) {
                    //'Start' button clicked, a toast is made which says 'Service started'
                    startMainService();
                    Toast.makeText(MainActivity.this, R.string.start_service, Toast.LENGTH_SHORT).show();
                }
                else {
                    //If 'Stop' button is clicked, the messaging service will be turned off
                    //A toast is made which says 'Service stopped'
                    stopMainService();
                    Toast.makeText(MainActivity.this, R.string.stop_service, Toast.LENGTH_SHORT).show();
                }
                //Logging that the service is on
                serviceOn = !serviceOn;
                Log.d(TAG, "serviceOn is " + serviceOn);
                updateViews();
            }
        });


    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        updateViews();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        updateViews();
    }

    private void startMainService(){
        startService(new Intent(this, MainService.class));
    }

    private void stopMainService(){
        stopService(new Intent(this, MainService.class));
    }

    private void updateViews(){
        if(!serviceOn) {
            button.setBackgroundResource(R.drawable.start_button);
            textServiceStatus.setText("Android Auto Messaging is off");
        }
        else {
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

