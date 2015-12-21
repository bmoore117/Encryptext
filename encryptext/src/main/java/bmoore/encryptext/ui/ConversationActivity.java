package bmoore.encryptext.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.crypto.SecretKey;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;
import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ContactAdapter;
import bmoore.encryptext.model.ConversationAdapter;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.services.SenderSvc;
import bmoore.encryptext.utils.ContactUtils;
import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.utils.InvalidKeyTypeException;

/**
 * This class handles the selection of a person to send a text to, the sending of texts, and the display of sent
 * and received texts. Only one instance of this class is ever created, and so static methods are used alongside
 * regular methods for bookkeeping purposes.
 *
 * @author Benjamin Moore
 *
 */
public class ConversationActivity extends AppCompatActivity
{
    private static final String TAG = "ConversationActivity";
    private static final int HALF = 50;
    private static final int FULL = 100;

    private static final int READ_CONTACTS_REQUEST_CODE = 1;
    private static final int SEND_SMS_REQUEST_CODE = 2;

    private static boolean active = false;
    private static boolean created = false;
    private static boolean newData = false;
    private static boolean newConfs = false;
    private boolean conversationChanged;
    private static String number = "";
    private String name;
    private Bitmap me;
    //private Bitmap other;
    private boolean useDrawable;
    private SenderSvc senderSvc;
    private SecretKey secretKey;
    private ArrayList<Contact> suggestions;
    private String query;
    private ConversationAdapter adapter;
    private ContactAdapter contacts;
    private AutoCompleteTextView to;
    private EditText messageBox;
    private EncrypText app;
    private DBUtils dbUtils;
    private Cryptor cryptor;
    private PhoneNumberUtil phoneNumberUtil;
    private final Thread loader;
    private volatile boolean shouldLoaderWait;

    public ConversationActivity() {

        loader = new Thread("Suggestions Loader")
        {
            @Override
            public void run()
            {
                while(true)
                {
                    shouldLoaderWait = true;
                    while (shouldLoaderWait)
                    {
                        try
                        {
                            synchronized (loader)
                            {
                                loader.wait();
                            }
                        }
                        catch (InterruptedException e)
                        {
                            Log.i(TAG, "Suggestions loader woken");
                        }
                    }

                    suggestions = new ArrayList<>();

                    Uri temp = CommonDataKinds.Phone.CONTENT_FILTER_URI;

                    if(temp == null)
                    {
                        Log.v(TAG, "Could not read CommonDataKinds.Phone.CONTENT_FILTER_URI");
                        return;
                    }

                    Uri person = Uri.withAppendedPath(temp, query);

                    if(person == null)
                    {
                        Log.v(TAG, "Failed constructing Uri person with base Uri and contact name/number");
                        return;
                    }

                    String[] projection = {Contacts.DISPLAY_NAME, CommonDataKinds.Phone.NUMBER,
                            CommonDataKinds.Phone.CONTACT_ID};
                    ContentResolver cr = getContentResolver();
                    Cursor c = cr.query(person, projection, null, null, Contacts.DISPLAY_NAME + " ASC LIMIT 10");

                    if(c == null)
                    {
                        Log.v(TAG, "Query to contacts database with supplied args failed");
                        return;
                    }

                    int i = c.getColumnIndex(Contacts.DISPLAY_NAME);
                    int j = c.getColumnIndex(CommonDataKinds.Phone.NUMBER);
                    int k = c.getColumnIndex(CommonDataKinds.Phone.CONTACT_ID);

                    if (c.moveToFirst())
                    {
                        do
                        {
                            String name = c.getString(i);
                            String number = c.getString(j);

                            if(number == null) {
                                continue;
                            }

                            Uri picPath = Uri.withAppendedPath(Contacts.CONTENT_URI, c.getString(k));
                            Bitmap thumb = BitmapFactory.decodeStream(
                                    Contacts.openContactPhotoInputStream(getContentResolver(), picPath));

                            try {

                                /*String formattedNumber = formatNumber(number);

                                if(formattedNumber == null) {
                                    suggestions.add(new Contact(name, "Error", thumb, HALF));
                                    continue;
                                }*/

                                secretKey = cryptor.loadSecretKey(number);

                                if (secretKey == null) //might have to re-engineer contact to store secret key
                                    suggestions.add(new Contact(name, number, thumb, HALF));
                                else
                                    suggestions.add(new Contact(name, number, thumb, FULL));
                            } catch (InvalidKeyTypeException e) {
                                Log.e(TAG, "Unable to load secret key", e);
                                Toast.makeText(ConversationActivity.this, "Unable to load secret key", Toast.LENGTH_SHORT).show();
                            } /*catch (NumberParseException e) {
                                Log.e(TAG, "Error parsing entered phone number", e);
                                Toast.makeText(ConversationActivity.this, "Error parsing entered phone number", Toast.LENGTH_SHORT).show();
                            }*/
                        }
                        while (c.moveToNext());
                    }

                    c.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            contacts.clear();
                            contacts.addAll(suggestions);
                        }
                    });
                }
            }
        };
    }

    /**
     * Method provided for the service to poll this activity and find out the conversation it is in
     *
     * @return the phone number of the current conversation
     */
    public static String currentNumber()
    {
        return number;
    }

    /**
     * Bookkeeper method for the class
     *
     * @return whether the activity is running or paused
     */
    public static boolean isActive()
    {
        return active;
    }

    /**
     * Method to edit a String into the format Android likes for phone number use:
     * "+1555555555"
     * @param number - the string to be formatted
     * @return the formatted input
     */
    private String formatNumber(String number) throws NumberParseException {

        Phonenumber.PhoneNumber phNumberProto = null;

        for (String r : phoneNumberUtil.getSupportedRegions()) {
            // check if it's a possible number
            if (phoneNumberUtil.isPossibleNumber(number, r)) {
                phNumberProto = phoneNumberUtil.parse(number, r);

                // check if it's a valid number for the given region
                if (phoneNumberUtil.isValidNumberForRegion(phNumberProto, r))
                    return phoneNumberUtil.format(phNumberProto, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        }
        return null;
    }

        /**
         * Bookkeeper method for the class
         *
         * @return whether an instance has been created
         */
    public static boolean isCreated()
    {
        return created;
    }

    public static void setCreated()
    {
        created = true;
    }

    public static void markNewConfs()
    {
        newConfs = true;
    }

    /**
     * Method for the service to alert this activity that it has new data to read in from
     * file when resumed via the launcher
     */
    public static void setNewData()
    {
        newData = true;
    }

    private ServiceConnection senderConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            senderSvc = ((SenderSvc.SenderBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            senderSvc = null;
        }
    };

    /**
     * Performs the initial setup when creating an instance of this activity. Performs three key tasks. First,
     * it initializes a textChangedListener that retrieves contact suggestions based on what the user types, and
     * second, it initializes the contact selection box with an itemClickListener that loads in a contact
     * picture, name, and phone number when the user taps a contact suggestion provided by the listener.
     *
     * If this activity was started with a conversation to load, this method extracts the data needed to
     * perform the load from the intent used to start the activity, and handles the loading and display.
     *
     */
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.conversation_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar bar = getSupportActionBar();
        bar.setDisplayHomeAsUpEnabled(true);

        useDrawable = false;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    READ_CONTACTS_REQUEST_CODE);
        } else {
            me = ContactUtils.getBitmap(getContentResolver(), null); //Get user's photo

            if(me == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                useDrawable = true;
            } else {
                me = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
            }
        }

        active = true;
        created = true;
        conversationChanged = false;

        app = ((EncrypText) getApplication());
        dbUtils = app.getDbUtils();
        cryptor = app.getCryptor();
        phoneNumberUtil = app.getPhoneNumberUtil();

        adapter = new ConversationAdapter(this, R.layout.conversation_item, new ArrayList<ConversationEntry>());
        ListView list = (ListView) findViewById(R.id.conversation_list);
        list.setAdapter(adapter);

        //list = (ListView) findViewById(android.R.id.list); //how you reference that pesky bitch
        to = (AutoCompleteTextView) findViewById(R.id.phone);
        messageBox = (EditText) findViewById(R.id.message);

        contacts = new ContactAdapter(this, R.layout.contact, new ArrayList<Contact>());
        to.setAdapter(contacts);

        shouldLoaderWait = true;
        loader.start();

        to.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            /**
             * Method to update the respective class variables and fill the contact selection box when
             * a suggestion has been tapped on. Calls formatNumber, updateTo, and setBitmap.
             */
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Contact c = contacts.getItem(pos);
                String number = c.getNumber();

                if("Error".equals(number)) {
                    return;
                }

                //try {
                    ConversationActivity.number = "+18034043014";
                    name = c.getName();
                    updateTo(name);

                    setEncryptionStatus();

                    /*if(ContextCompat.checkSelfPermission(ConversationActivity.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        other = ContactUtils.getBitmap(getContentResolver(), number);
                    } else {
                        other = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
                    }*/

                    if (c.getAlpha() == HALF) {
                        checkPermissionOrShowDialog();
                    }
                /*} catch (NumberParseException e) {
                    Log.e(TAG, "Error parsing entered phone number", e);
                    Toast.makeText(ConversationActivity.this, "Error parsing entered phone number", Toast.LENGTH_SHORT).show();
                }*/
            }
        });

        to.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable field) {
                if (field.toString().equals("")) //if box cleared, do not try to show a list
                    return;
                else if (field.toString().contains(" ")) //do not run after we have made a selection
                    return;                                        //as marked by the space

                query = field.toString();
                shouldLoaderWait = false;

                synchronized (loader) {
                    loader.notify();
                }
            }

            public void beforeTextChanged(CharSequence c, int one, int two, int three) {
            }

            public void onTextChanged(CharSequence c, int one, int two, int three) {
            }

        });

        Bundle b = getIntent().getExtras();

        if (b != null && b.containsKey(EncrypText.ADDRESS) && b.containsKey(EncrypText.NAME)) //if reading in existing conv
        {
            number = b.getString(EncrypText.ADDRESS);
            /*if(ContextCompat.checkSelfPermission(ConversationActivity.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                other = ContactUtils.getBitmap(getContentResolver(), number);

                if(other == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    useDrawable = true;
                } else {
                    other = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
                }
            }*/

            new LoadConversationTask().execute(new LoadConversationArgs(number, 0));
            new LoadSecretKeyTask().execute(number);

            to.setVisibility(View.GONE);
        }

        if (newData)
            newData = false;
    }

    public void onStart()
    {
        super.onStart();

        //Toast.makeText(this, "Starting", Toast.LENGTH_SHORT).show();

        if(!SenderSvc.isCreated())
        {
            Intent sender = new Intent(this, SenderSvc.class);
            startService(sender);
        }

        if(senderSvc == null)
        {
            Intent intent = new Intent(this, SenderSvc.class);
            bindService(intent, senderConnection, Context.BIND_AUTO_CREATE);
        }

        /*if(receiverSvc == null)
        {
            Intent intent = new Intent(this, ReceiverSvc.class);
            bindService(intent, receiverConnection, BIND_AUTO_CREATE);
        }*/
    }

    /**
     * Bookkeeping method for the class. Sets the static variable created to false when
     * android is destroying this activity.
     */
    public void onDestroy()
    {
        //Toast.makeText(this, "Destroying", Toast.LENGTH_SHORT).show();
        created = false;
        newConfs = false;
        newData = false;

        if(secretKey != null) {
            cryptor.storeLastEncryptedBlock(secretKey, number);
        }

        number = "";

        //manager.resetPointer(number + ".dat");
        //unbindService(receiverConnection);
        unbindService(senderConnection);
        super.onDestroy();
    }

    /**
     * Hook method to handle new messages passed to this activity from the service.
     * If a single message has been passed, this method extracts it from the intent and
     * displays it. If multiple messages have been passed, this method extracts the ArrayList
     * they were in, and constructs full ConversationEntries out of them, then displaying them.
     * Third scenario use unclear.
     *
     * @param intent - the message this activity has been passed
     */
    public void onNewIntent(Intent intent)
    {
        active = true;
        Bundle b = intent.getExtras();

        if(b == null)
        {
            Log.v(TAG, "Bundle passed to ConversationActivity was null");
            return;
        }

        ConversationEntry item = b.getParcelable(EncrypText.THREAD_ITEM);
        ArrayList<ConversationEntry> messages = b.getParcelableArrayList(EncrypText.MULTIPLE_THREAD_ITEMS);
        String time = b.getString(EncrypText.TIME);
        String address = b.getString(EncrypText.ADDRESS);
        SecretKey key = (SecretKey) b.getSerializable(EncrypText.KEY);

        if(key != null)
        {
            secretKey = key;
            setEncryptionStatus();
        }
        else if(item != null)
        {
            /*if(useDrawable) {
                item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
            } else {
                item.setPhoto(other);
            }*/
            adapter.add(item);
            conversationChanged = true;
        }
        else if (messages != null)
        {
            for (ConversationEntry entry : messages) //do not need times. Have been assigned in svc
            {
                /*if(useDrawable) {
                    entry.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
                } else {
                    entry.setPhoto(other);
                }*/
                adapter.add(entry);
            }

            conversationChanged = true;
        }
        else if (address != null) //for jumping to new conv via notification
        {
            adapter.clear();
            number = address;
            new LoadConversationTask().execute(new LoadConversationArgs(number, 0));
            AutoCompleteTextView To = (AutoCompleteTextView)findViewById(R.id.phone);
            To.setVisibility(View.GONE);
            conversationChanged = false;
            created = true;
        }
        else if (time != null)
        {
            int pos = intent.getIntExtra(EncrypText.THREAD_POSITION, -1);

            ConversationEntry temp = adapter.getItem(pos);
            temp.setDate(time);
            adapter.notifyDataSetChanged();

            conversationChanged = true;
        }
        else
        {
            String error = b.getString(EncrypText.ERROR);
            adapter.getItem(adapter.getCount() - 1).setDate(error); //create fixed length
            conversationChanged = true;
        }

        newData = false;
    }

    /**
     * Method to handle updating the preview files when this activity is navigated away from.
     * Retrieves the last item in this conversation to use as the new preview, and writes it, notifying
     * the main activity to refresh itself.
     */
    public void onPause()
    {
        active = false;

        //lastReadPosition = manager.getLastReadPosition(number);

        if (adapter.getCount() > 0 && conversationChanged)
        {
			/*ConversationEntry item = adapter.getItem(adapter.getCount() - 1);
			manager.writePreview(new ConversationEntry(item.getMessage(), number, name,
                    item.getDate(), null), this);*/
            HomeActivity.setNewPreviews();
            conversationChanged = false;
        }
        super.onPause();
    }

    public void onStop()
    {
        //Toast.makeText(this, "Stopping", Toast.LENGTH_SHORT).show();

        super.onStop();
    }

    /**
     * Method to handle picking up any new messages when this activity is resumed from the launcher. Calls
     * findShift to figure out how many messages have been sent since the file was last read, and then calls
     * the files class to read the file with the shift amount passed in. Cancels any pending notifications
     * for this conversation and informs the service to release any memory held by messages for this
     * conversation, also setting the class newData marker to false
     */
    public void onResume()
    {
        super.onResume();
        active = true;
        //Toast.makeText(this, "Resuming", Toast.LENGTH_SHORT).show();

        if(newConfs)
        {
            //Toast.makeText(this, "Updating time", Toast.LENGTH_SHORT).show();
            TreeMap<Integer, String> confs = senderSvc.getConfs(number);

            for(Integer key : confs.keySet())
                adapter.getItem(key).setDate(confs.get(key));

            newConfs = false;
        }

        if (newData)
        {
            new LoadConversationTask().execute(new LoadConversationArgs(number, adapter.getData().get(adapter.getData().size() - 1).getMessageId()));

            ((NotificationManager)getSystemService(
                    Context.NOTIFICATION_SERVICE)).cancel(number.hashCode());
            //receiverSvc.removeHeldTexts(number); on a theory, dont need this

            newData = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case READ_CONTACTS_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    me = ContactUtils.getBitmap(getContentResolver(), null); //Get user's photo

                    if(me == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        useDrawable = true;
                    } else {
                        me = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
                    }

                } else {

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Permissions Denied");
                    builder.setMessage("Without the ability to read contacts, this app cannot display contact names or photos.");

                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                useDrawable = true;
                            } else {
                                me = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
                            }
                        }
                    });
                }
            }
            break;
            case SEND_SMS_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    showKeyRequestDialog(name);

                } else {

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Permissions Denied");
                    builder.setMessage("This app requires the ability to send sms messages to function properly. Returning to the home screen.");

                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                            finish();
                        }
                    });
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void checkPermissionOrShowDialog() {
        if (ContextCompat.checkSelfPermission(ConversationActivity.this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(ConversationActivity.this, new String[]{Manifest.permission.SEND_SMS},
                    SEND_SMS_REQUEST_CODE);
        } else {
            showKeyRequestDialog(name);
        }
    }

    /**
     * Method called by the GUI when the send message button is pressed. Hides the contact selection
     * box, as a contact has been selected, displays the message to send, and calls the sendText method
     * to handle the packetization. Writes the sent message to the database.
     *
     * Note: do something to show message sent: confirmation check?
     *
     * @param v The button triggering the send
     */
    public void sendMessage(View v)
    {
        Editable editable = messageBox.getText();

        if(editable == null)
        {
            Log.e(TAG, "Could not access message Editable");
            return;
        }

        String text = editable.toString();

        Editable contact = to.getText();

        if(contact == null)
        {
            Log.e(TAG, "Could not access contact Editable");
            return;
        }

        String address = contact.toString();

        if (secretKey != null && !"".equals(number)) { //contact with key selected
            performSend(text);
        } else if(secretKey == null && !"".equals(number)) { //contact with no key selected, still awaiting key exchange reply
            Toast.makeText(this, "Waiting for key exchange reply", Toast.LENGTH_SHORT).show();
        } else { //no contact so no loaded key
            try {
                number = formatNumber(address);
            } catch (NumberParseException e) {
                Log.e(TAG, "Error parsing entered phone number", e);
                Toast.makeText(ConversationActivity.this, "Error parsing entered phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            if(number != null) {
                //we have a number, so try loading a key
                try {
                    secretKey = cryptor.loadSecretKey(number);
                    setEncryptionStatus();
                } catch (InvalidKeyTypeException e) {
                    Log.e(TAG, "Error loading key for " + address, e);
                    Toast.makeText(ConversationActivity.this, "Error loading key for " + address, Toast.LENGTH_SHORT).show();
                    return;
                }

                name = address;
                if(secretKey != null) { //we have number and key
                    performSend(text);
                } else {
                    checkPermissionOrShowDialog();
                }
            }
        }
    }

    private void performSend(String text)
    {
        to.setVisibility(View.GONE);
        ConversationEntry item = new ConversationEntry(text, number, "Me", "Sending", me);

        if(useDrawable) {
            item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
        }

        new SendMessageTask().execute(new SendMessageArgs(item, adapter.getCount(), secretKey));
        conversationChanged = true;
        messageBox.getText().clear();
    }

    private void setEncryptionStatus(){
        if(secretKey != null) {
            TextView view = (TextView) findViewById(R.id.conversation_encryption_status);
            view.setText(getString(R.string.messages_encrypted));
            view.setTextColor(ContextCompat.getColor(ConversationActivity.this, R.color.lime_green));
        }
    }

    /**
     * Method to allow the external onItemClickListener to update the contact selection box text
     * and underlying class variable. Of note, places a space after the selected name, as a marker that
     * a name has been selected to the attached onTextChangedListener, which will be called after this change
     * is made. The space instructs the listener to return immediately.
     */
    public void updateTo(String name)
    {
        to.setText(name + " ");
    }

    private void showKeyRequestDialog(String name)
    {
        SharedPreferences prefs = getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE);

        if (!prefs.contains("firstTimeExchange")) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
            builder.setTitle("Send key exchange request to " + name + "?");
            builder.setMessage("Like a friend request, this action will have to be confirmed or denied by the the other person. If they respond to the request, " +
                    "you will both exchange your public keys and create a shared private key which will be used to encrypt your actual messages. " +
                    "Tap Yes to send the other person a key exchange request with your public key and return to the home screen. For subsequent conversations this dialog will not be shown");

            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int buttonClicked) {

                    getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE).edit()
                            .putBoolean("firstTimeExchange", false)
                            .putString(number, "inNegotiation").apply();

                    new SendKeyTask().execute();
                }
            });
            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                    dialogInterface.cancel();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            Toast.makeText(ConversationActivity.this, "Sending public key", Toast.LENGTH_SHORT).show();
            new SendKeyTask().execute();
        }
    }


    private class LoadConversationArgs {
        private final String number;
        private final long messageId;

        public LoadConversationArgs(String number, long messageId)
        {
            this.number = number;
            this.messageId = messageId;
        }

        public long getMessageId()
        {
            return messageId;
        }

        public String getNumber()
        {
            return number;
        }
    }


    private class LoadConversationTask extends AsyncTask<LoadConversationArgs, Void, List<ConversationEntry>> {

        @Override
        protected List<ConversationEntry> doInBackground(LoadConversationArgs... params) {
            return dbUtils.loadConversation(params[0].getNumber(), params[0].getMessageId());
        }

        @Override
        protected void onPostExecute(List<ConversationEntry> messages) {
            adapter.addAll(messages);
        }
    }

    private class LoadSecretKeyTask extends AsyncTask<String, Void, SecretKey> {

        @Override
        protected SecretKey doInBackground(String... params) {

            SecretKey key = null;
            try {
                key = cryptor.loadSecretKey(params[0]);
            } catch (InvalidKeyTypeException e) {
                Log.e(TAG, "Unable to load secret key", e);
                Toast.makeText(ConversationActivity.this, "Unable to load secret key", Toast.LENGTH_SHORT).show();
            }

            return key;
        }

        @Override
        protected void onPostExecute(SecretKey key) {
            secretKey = key;
            setEncryptionStatus();
        }
    }

    private class SendMessageArgs {
        public ConversationEntry getItem() {
            return item;
        }

        public int getMessageNumber() {
            return messageNumber;
        }

        public SecretKey getKey() {
            return key;
        }

        private final ConversationEntry item;
        private final int messageNumber;
        private final SecretKey key;

        public SendMessageArgs(ConversationEntry item, int messageNumber, SecretKey key) {
            this.item = item;
            this.messageNumber = messageNumber;
            this.key = key;
        }
    }

    private class SendMessageTask extends AsyncTask<SendMessageArgs, Void, ConversationEntry> {

        @Override
        protected ConversationEntry doInBackground(SendMessageArgs... params) {

            senderSvc.sendMessage(params[0].getItem(), params[0].getMessageNumber(), params[0].getKey());

            return params[0].getItem();
        }

        @Override
        protected void onPostExecute(ConversationEntry item) {
            adapter.add(item);
        }
    }

    private class SendKeyTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            senderSvc.sendKey(cryptor.getMyPublicKey(), number);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            ConversationActivity.this.finish();
        }
    }
}