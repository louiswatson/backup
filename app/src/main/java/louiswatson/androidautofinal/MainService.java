package louiswatson.androidautofinal;

//imports needed
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class MainService extends Service implements AudioManager.OnAudioFocusChangeListener{
    private static final String TAG = "MainService";
    private long SMS_delay = 2000;
    private int MAX_MESSAGE_LENGTH = 350;
    private int[] originalVolume;

    private boolean myTTSReady = true;
    private boolean clearedTTS = false;
    private static boolean inProcess = false;
    private static String phonenumber = "";
    private static int stateID = 0;
    private static String messageresponse = "";
    private static String messageincoming = "";
    private static String contactName = "";

    private static SharedPreferences preferences;
    private static boolean notification;
    private static boolean readtexts;

    private static Application myApplication;
    private TextToSpeech myTTS;
    private AudioManager am;
    private TelephonyManager tm;
    private SpeechRecognizer sr;
    private SyncSpeak ss;
    private Intent ri;
    private NotificationManager nm;

    @Override
    //Return communication channel to the service
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onAudioFocusChange(int input) {
        Log.d(TAG, "onAudioFocusChange code: " + input);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return START_STICKY;
    }

    @Override
    //Called by system when first created
    public void onCreate() {
        myApplication = this.getApplication();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        notification = preferences.getBoolean("pref_key_notification_preference", true);
        readtexts = preferences.getBoolean("pref_key_readtexts_preference", false);
        Log.d(TAG, "Preferences are, notif: " + notification +  " readtexts: " + readtexts);

        if(notification) {
            nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            Notification mNotification = new Notification.Builder(myApplication)

                    .setContentIntent(contentIntent)
                    .setContentTitle("Android Auto Text Messaging")
                    //Set textview to say the following
                    .setContentText("Android Messaging is Currently On")
                    .setPriority(Notification.PRIORITY_LOW).build();
            nm.notify(1, mNotification);
        }

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        myTTS = new TextToSpeech(myApplication, listenerStarted);
        myTTS.setSpeechRate((float) 1);
        myTTS.setPitch((float)1);

        ss = new SyncSpeak();

        //Creating the speech recogniser and listener
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new SpeechListener());

        ri = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        ri.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
        ri.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        //Register receiver to SMS_Received, broadcast receiver will recognise if a text has come in
        myApplication.registerReceiver(SMScatcher, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
    }

    @Override
    //class for removing all information for previous message when onDestroy is called
    public void onDestroy() {
        clearFields();
        try {
            myApplication.unregisterReceiver(SMScatcher);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (notification) {
            nm.cancelAll();
        }
        super.onDestroy();
    }


    //Broadcast receiver code
    //If the action equals 'SMS_Received' this means that a text has been received

    //If a text message is received the broadcast receiver will notice.
    //this will initiate the main function of the app, being able to reply etc

    private final BroadcastReceiver SMScatcher = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED") && tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                if(inProcess){
                    return;
                }
                else {
                    //If the inProcess is true then start getting contact information, text content information
                    inProcess = true;
                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        Object[] pdusObj = (Object[]) bundle.get("pdus");
                        SmsMessage[] messages = new SmsMessage[pdusObj.length];
                        String strMessage = "";
                        messageincoming = "";
                        for (int i = 0; i < messages.length; i++) {
                            messages[i] = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                            //SMS message from..
                            strMessage += "SMS From: " + messages[i].getOriginatingAddress();
                            strMessage += " : ";
                            //Getting text message body content
                            strMessage += messages[i].getMessageBody();
                            messageincoming += messages[i].getMessageBody();
                            strMessage += "\n";
                            phonenumber = messages[i].getOriginatingAddress();
                            Log.v(TAG, messages[i].getOriginatingAddress() + " " + messages[i].getMessageBody());
                        }
                        messageincoming = messageincoming.trim();
                        //Get contact name by searching contact book with the phone number
                        contactName = getContactDisplayNameByNumber(phonenumber);
                        stateID = 1;



                        //Changing volume if text has been received
                        //Means that message wont be missed if volume is low

                        //Changing the volume incase the application volume is low, will always be able to hear what the text says then

                        originalVolume = new int[2];
                        originalVolume[0] = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                        originalVolume[1] = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                        am.requestAudioFocus(MainService.this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                        CountDownTimer connectTimer = new CountDownTimer(SMS_delay, SMS_delay/2) {
                            @Override
                            public void onTick(long millisUntilFinished) {}

                            @Override
                            public void onFinish() {
                                ss.textReader("", 1, new String[]{contactName, messageincoming});
                                startVoiceRecognition();
                                return;
                            }
                        };
                        connectTimer.start();
                        return;
                    }
                }
            }
        }

    };

    class SyncSpeak {
        private final Object lock = new Object();

        public void signal() {
            synchronized (lock) {
                lock.notify();
            }
        }

        public void await() throws InterruptedException {
            synchronized (lock) {
                lock.wait();
            }
        }

        public void textReader(String rawinput, final int caseNumber, String[] params) {
            if (myTTSReady) {
                final HashMap myHash = new HashMap<String, String>();
                myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Android Auto");

                if(rawinput == null){

                    return;
                }
                String input = rawinput.replaceAll("http.*? ", ", URL, ");;

                // trim off very long strings
                if (input.length() > MAX_MESSAGE_LENGTH){
                    input = input.substring(0, MAX_MESSAGE_LENGTH);
                    input += " , , , message truncated";
                }

                myHash.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_VOICE_CALL));

                final String str = input;
                final int inputNumber = caseNumber;
                final String[] parameters = Arrays.copyOf(params, 2);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    //If a command isn't recognised it will loop back round
                    try {
                        if(inputNumber == -1){
                            //if the command was't recognised, say this message
                            myTTS.speak("Not a recognized command. Please try again.", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                        }
                        if(inputNumber == 0){
                            //Error handling if there is no speech detected
                            myTTS.speak("No speech was detected. Please try again", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                        }
                        else if(inputNumber == 1){
                            //Letting the user know that they have received a message
                            String contactName = parameters[0];
                            String message = parameters[1];
                            //Message from
                            //TEXT TO SPEECH
                            myTTS.speak("Message from", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            //Reads out the contact name, if the contact name isn't available then read out the mobile number
                            myTTS.speak(contactName, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            //Play silence so that the information is being split up
                            myTTS.playSilence(250, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            myTTS.speak(message, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            myTTS.playSilence(100, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            //Giving the user options on what to do
                            myTTS.speak("Would you like to Reply, Repeat, or Quit?", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                        }
                        else if(inputNumber == 2){
                            //if the driver said 'reply then say "Say your reply" out loud
                            myTTS.speak("Say your reply", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                        }
                        else if(inputNumber == 3){
                            //Once user has said their reply
                            //App prompts the user asking if they would like to send the message
                            //Try the message again, or try again
                            String speechInput = parameters[0];
                            //Text message that the driver created is then read out
                            myTTS.speak(speechInput, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            myTTS.playSilence(250, TextToSpeech.QUEUE_ADD, myHash);
                            await();
                            //Message has been created by the driver, driver is prompted with this question
                            myTTS.speak("Would you like to send, try again, or quit?", TextToSpeech.QUEUE_ADD, myHash);
                            await();
                        }
                        else {
                            if(str.equals("")) return;
                            myTTS.speak(str, TextToSpeech.QUEUE_ADD, myHash);
                            Log.d(TAG, "SyncSpeak awaiting, ambiguous message. " + str);
                            await();
                        }
                    } catch (Exception e) {

                    }
                }
            }
        }
    }

    //if voice recognition isn't available
    public void startVoiceRecognition(){
        if (!sr.isRecognitionAvailable(this)) {
            ss.textReader("Voice recognition not available", -5, new String[] {});
        }
        else {
            sr.startListening(ri);
        }
    }

    public TextToSpeech.OnInitListener listenerStarted = new TextToSpeech.OnInitListener() {
        // TTS engine now running so start the message receivers

        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                myTTSReady = true;
                myTTS.setOnUtteranceProgressListener(ul);
            }
        }
    };

    public android.speech.tts.UtteranceProgressListener ul = new UtteranceProgressListener() {

        @Override
        public void onDone(String uttId) {

            if (!clearedTTS) {
                Intent c = new Intent();
                c.setAction("MainService.CLEAR");
                myApplication.sendBroadcast(c);
            }

            ss.signal();
        }

        @Override
        public void onError(String utteranceId) {

        }

        @Override
        public void onStart(String utteranceId) {

        }

    };

    class SpeechListener implements RecognitionListener {
        private static final String TAG = "SpeechListener";

        public void onBeginningOfSpeech() {

        }

        public void onBufferReceived(byte[] buffer) {

        }

        public void onEndOfSpeech() {
            ;
        }

        public void onError(int error) {
            Log.d(TAG, "onError " + error);
            onSpeechError(error);
        }

        public void onEvent(int eventType, Bundle params) {;
        }

        public void onPartialResults(Bundle partialResults) {;
        }

        public void onReadyForSpeech(Bundle params) {
            ;
        }

        public void onResults(Bundle results) {
            String str = "";
            Log.d(TAG, "onResults " + results);
            ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            for (int i = 0; i < data.size(); i++){
                Log.d(TAG, "result " + data.get(i));
            }
            //Speech results is what the driver said, data put to an array
            String[] speechResults = new String[data.size()];
            //Speech results checked to see if reply, repeat or quit was said
            speechResults = data.toArray(speechResults);

            //catching words that sound similar to the options given
            if(stateID == 1){
                //If the speech result is similar to the word 'ply'
                //Call 'onReply'
                if (existsInArray("ply", speechResults)){
                    onReply();
                }
                //If the speech result is similar to the word 'repeat'
                //Call 'onRepeat'
                else if(existsInArray("repeat", speechResults)){
                    onRepeat();
                }
                //If the speech result is similar to the word 'quit'
                //Call 'onQuit'
                else if(existsInArray("quit", speechResults)){
                    onQuit();
                }
                else {
                    onUnrecognized(speechResults[0]);
                }
            }
            else if(stateID == 2){
                onSpeechResult(speechResults[0]);
            }
            else if(stateID == 3){
                if (existsInArray("again", speechResults)) {
                    onTryAgain();
                }
                else if(existsInArray("end", speechResults)) {
                    onSend();
                }
                else if(existsInArray("quit", speechResults)) {
                    onQuit();
                }
                else {
                    onUnrecognized(speechResults[0]);
                }
            }
            else {
            }
        }

        public void onRmsChanged(float rmsdB) {
        }
    }


    private void onUnrecognized(String result) {
        Log.d(TAG, "onUnrecognized " + result);
        ss.textReader("", -1, new String[] {});
        startVoiceRecognition();
        return;
    }

    private void onReply(){
        //When onReply is called the text reader will read voice results
        //and input into a new string which will be sent later
        stateID = 2;
        ss.textReader("", 2, new String[] {});
        startVoiceRecognition();
        return;
    }

    private void onRepeat(){
        //Function for repeating the text message
        ss.textReader("", 1, new String[]{contactName, messageincoming});
        //Once message is repeated, voice recognition starts up
        startVoiceRecognition();
        return;
    }

    private void onSend(){
        //When the onSend method is called it will send the message response
        //That the driver has created using voice recognition
        sendSMS(messageresponse);
        return;
    }

    private void onTryAgain(){
        //Try again will be used if the driver wants to do the reply again
        //If the onTryAgain method is called it will listen for voice reply
        //and insert the response into a new message
        stateID = 2;
        ss.textReader("", 2, new String[] {});
        startVoiceRecognition();
        return;
    }

    private void onQuit(){
        //On Quit will be used if the driver doesn't want to do anything with the text
        //All fields will be cleared and ready for any new messages received.
        //clearfields is called which will set all the used variables back to 0
        clearFields();
        return;
    }

    //Speech error
    private void onSpeechError(int error){
        Log.d(TAG, "onSpeechError " + error);
        sr.cancel();
        if(error == 7) {
            startVoiceRecognition();
        }
        else {
            ss.textReader("", 0, new String[] {});
            startVoiceRecognition();
        }
        return;
    }

    private void onSpeechResult(String result){
        Log.d(TAG, "onSpeechResult " + result);
        stateID = 3;
        messageresponse = result;
        ss.textReader("", 3, new String[]{result});
        startVoiceRecognition();
        return;
    }
    //Function for finding the contacts name by searching contact book with number
    public String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String name = "unknown number";

        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID, ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return name;
    }

    public void sendSMS(String message){
        Log.d(TAG, "Sending SMS to phonenumber: " + phonenumber);
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phonenumber, null, message, null, null);

        ss.textReader("Sent Successfully", -5, new String[]{});
        //clear fields once message has been sent
        clearFields();
    }

    //setting all fields back to nothing
    //so if another message is received it won't have previous information
    private void clearFields(){
        //Inprocess set back to false
        inProcess = false;
        //Phone number reset
        phonenumber = "";
        //stateID reset
        stateID = 0;
        //Message response reset
        messageresponse = "";
        messageincoming = "";
        contactName = "";

        if (originalVolume != null) {
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalVolume[0], 0);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume[1], 0);
        }
        am.abandonAudioFocus(MainService.this);
        am.setMode(AudioManager.MODE_NORMAL);
    }

    private boolean existsInArray(String query, String[] array){
        for (String entry:array){
            if (entry.contains(query)){
                return true;
            }
        }
        return false;
    }
}
