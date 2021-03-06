package edu.chalmers.sikkr.frontend;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

import edu.chalmers.sikkr.R;
import edu.chalmers.sikkr.backend.calls.CallLog;
import edu.chalmers.sikkr.backend.contact.Contact;
import edu.chalmers.sikkr.backend.contact.ContactBook;
import edu.chalmers.sikkr.backend.messages.TheInbox;
import edu.chalmers.sikkr.backend.util.ProgressListener;
import edu.chalmers.sikkr.backend.util.ServerInterface;
import edu.chalmers.sikkr.backend.util.SpeechRecognitionHelper;
import edu.chalmers.sikkr.backend.util.SystemData;
import edu.chalmers.sikkr.backend.util.TextToSpeechUtility;
import edu.chalmers.sikkr.backend.util.VoiceMessagePlayer;
import edu.chalmers.sikkr.backend.util.VoiceMessageRecorder;
import edu.chalmers.sikkr.backend.util.VoiceMessageSender;

import static edu.chalmers.sikkr.backend.util.FuzzySearchUtility.getDifference;





public class StartActivity extends Activity {
    private final static int MY_TTS_CHECK_CODE = 1337;

    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        new Initializer().execute(this);
    }

    /**
     * Actionhandler for this activity
     *
     * @param view
     */
    public void clickedButton(View view) {

        switch (view.getId()) {
            case R.id.contactBook:
                intent = new Intent(this, ContactBookActivity.class);
                startActivity(intent);
                break;
            case R.id.message:
                intent = new Intent(this, MessagesActivity.class);
                startActivity(intent);
                break;
            case R.id.fav_contacts:
                intent = new Intent(this, ContactGridActivity.class);
                startActivity(intent);
                break;
            case R.id.lastCall:
                intent = new Intent(this, LatestCallsActivity.class);
                startActivity(intent);
                break;
            case R.id.microphone:
                SpeechRecognitionHelper.run(this);
                break;
        }
    }

    /**
     * A method to retrieve results from finished speech recognition.
     * Will trigger methods to match certain keywords
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SystemData.VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches.size() > 0) {
                String firstLine = matches.get(0);
                String firstWord = firstLine.split(" ")[0];

                if (getDifference(firstWord, getString(R.string.call)) <= 2 && firstLine.split(" ").length > 1) {
                    callContactByName(firstLine.split(" ", 2)[1]);

                } else if (getDifference(firstWord, getString(R.string.show)) <= 2) {
                    showContactByName(firstLine.split(" ", 2)[1]);
                } else {
                    selectFunctionality(firstLine);
                }
            }
        } else if (requestCode == MY_TTS_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                TextToSpeechUtility.setupTextToSpeech(this);
            } else {
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    /**
     * Method to check if voice recognition was used to select functionality
     * Will redirect user to the selected activity
     */
    private void selectFunctionality(String text) {

        if (getDifference(text, getString(R.string.one)) <= 1 || getDifference(text, getString(R.string.calls)) <= 2) {
            TextToSpeechUtility.readAloud(getString(R.string.entering) + " " + getString(R.string.calls));
            intent = new Intent(this, LatestCallsActivity.class);
            startActivity(intent);
        } else if (getDifference(text, getString(R.string.two)) <= 1 || getDifference(text, getString(R.string.favorites)) <= 2) {
            TextToSpeechUtility.readAloud(getString(R.string.entering) + " " + getString(R.string.favorites));
            intent = new Intent(this, ContactGridActivity.class);
            startActivity(intent);
        } else if (getDifference(text, getString(R.string.three)) <= 1 || getDifference(text, getString(R.string.messages)) <= 2) {
            TextToSpeechUtility.readAloud(getString(R.string.entering) + " " + getString(R.string.messages));
            intent = new Intent(this, MessagesActivity.class);
            startActivity(intent);
        } else if (getDifference(text, getString(R.string.four)) <= 1 || getDifference(text, getString(R.string.contacts)) <= 2) {
            String[] words = text.split(" ");
            if (words.length > 1) {
                final char firstChar = words[1].toLowerCase().charAt(0);

                if(ContactBook.getSharedInstance().getContacts(firstChar).size() > 0) {
                    intent = new Intent(this, ContactGridActivity.class);
                    intent.putExtra("initial_letter", firstChar);
                    startActivity(intent);
                } else {
                    TextToSpeechUtility.readAloud(getString(
                            R.string.found_no_contacts_initial_letter) + " " + firstChar);
                }
            } else {
                TextToSpeechUtility.readAloud(getString(R.string.entering) + " " + getString(R.string.contacts));
                intent  = new Intent(this, ContactBookActivity.class);
                startActivity(intent);
            }

        } else {
            TextToSpeechUtility.readAloud(getString(R.string.unknown_command));
        }

    }

    /**
     * Method to check if voice recognition was used to make a call
     * Will try to call contact that best matches the input.
     */
    private void callContactByName(String text) {
        final ContactBook cb = ContactBook.getSharedInstance();
        try {
            intent = new Intent(Intent.ACTION_CALL);
            final Contact contact = cb.getClosestMatch(text);

            if (contact == null) {
                TextToSpeechUtility.readAloud(getString(R.string.found_no_contact));
            } else {
                if (contact.getDefaultNumber() != null && contact.getName() != null) {
                    intent.setData(Uri.parse("tel:" + contact.getDefaultNumber()));
                    TextToSpeechUtility.readAloud(getString(R.string.calling) + " " + contact.getName());
                    while (TextToSpeechUtility.isSpeaking()) {
                        Thread.sleep(100);
                    }
                    startActivity(intent);
                    finish();
                }
            }
        } catch (Throwable t) {
        }

    }

    private void showContactByName(String name) {
        final ContactBook cb = ContactBook.getSharedInstance();
        final Contact contact = cb.getClosestMatch(name);

        if (contact != null) {
            final Intent intent = new Intent(this, ContactActivity.class);
            intent.putExtra("contact_id", contact.getID());
            startActivity(intent);
        } else {
            TextToSpeechUtility.readAloud(getString(R.string.found_no_contact));
        }
    }

    void updateProgress(double progress, String taskMsg) {

        ProgressBar initBar = (ProgressBar) findViewById(R.id.initProgressBar);
        TextView initText = (TextView) findViewById(R.id.initTextView);

        if (initBar != null && initText != null) {
            initBar.setProgress((int) (progress * initBar.getMax()));
            initText.setText(taskMsg + "...");
        }
    }

    private class Initializer extends AsyncTask<StartActivity, String, Boolean> implements ProgressListener {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setContentView(R.layout.init_screen);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                setContentView(R.layout.activity_start);
            } else {
                throw new RuntimeException("Initialization failed!");
            }
        }

        @Override
        protected Boolean doInBackground(StartActivity... params) {
            Intent checkIntent = new Intent();
            ServerInterface.setupSingleton(params[0]);
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkIntent, MY_TTS_CHECK_CODE);
            ContactBook.setupSingleton(params[0], this);
            TextToSpeechUtility.setupTextToSpeech(params[0]);
            TheInbox.setupInbox(params[0]);
            CallLog.setUpCallLog(params[0]);
            VoiceMessagePlayer.setupSingleton(params[0]);
            VoiceMessageRecorder.setupSingleton(params[0]);
            VoiceMessageSender.setupSingleton(params[0]);
            return true;
        }

        @Override
        public void onProgressUpdate(String... values) {
            updateProgress(Double.parseDouble(values[0]), values[2]);
        }

        @Override
        public void notifyProgress(double progress, String senderTag, String taskMsg) {
            publishProgress(progress + "", senderTag, taskMsg);
        }
    }
}

