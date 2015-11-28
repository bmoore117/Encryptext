package bmoore.encryptext.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.crypto.SecretKey;

import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ContactAdapter;
import bmoore.encryptext.model.ConversationAdapter;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.utils.ContactUtils;
import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.EncrypText;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.R;
import bmoore.encryptext.services.SenderSvc;
import bmoore.encryptext.utils.InvalidKeyTypeException;

/**
 * This class handles the selection of a person to send a text to, the sending of texts, and the display of sent
 * and received texts. Only one instance of this class is ever created, and so static methods are used alongside
 * regular methods for bookkeeping purposes.
 * 
 * @author Benjamin Moore
 *
 */
public class ConversationActivity extends ListActivity
{
    private static final String TAG = "ConversationActivity";
    private static final int HALF = 50;
    private static final int FULL = 100;
	private static boolean active = false;
	private static boolean created = false;
	private static boolean newData = false;
    private static boolean newConfs = false;
    private boolean conversationChanged;
	private static String number = "";
	private Bitmap me;
	private Bitmap other;
    private DBUtils dbUtils;
    private Cryptor cryptor;
    private SenderSvc senderSvc;
    private SecretKey secretKey;
    private boolean shouldLoaderWait;
    private ArrayList<Contact> suggestions;
    private String query;
	private ConversationAdapter adapter;
	private ContactAdapter contacts;
	private AutoCompleteTextView to;
    private EditText messageBox;
    private Context context;

    private final Thread loader = new Thread("Suggestions Loader")
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
                Cursor c = cr.query(person, projection, null, null, null);

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
                        Uri picPath = Uri.withAppendedPath(Contacts.CONTENT_URI, c.getString(k));
                        Bitmap thumb = BitmapFactory.decodeStream(
                                Contacts.openContactPhotoInputStream(getContentResolver(), picPath));

                        try {
                            secretKey = cryptor.loadSecretKey(formatNumber(number));

                            if (secretKey == null) //might have to re-engineer contact to store secret key
                                suggestions.add(new Contact(name, number, thumb, HALF));
                            else
                                suggestions.add(new Contact(name, number, thumb, FULL));
                        } catch (InvalidKeyTypeException e) {
                            Log.e(TAG, "Unable to load secret key", e);
                            Toast.makeText(ConversationActivity.this, "Unable to load secret key", Toast.LENGTH_SHORT).show();
                        }
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
    private String formatNumber(String number) {
        String result = number.replace("(", "").replace(")", "").replace(" ", "").replace("-", "");
        if (!result.contains("+1"))
            result = "+1" + result;
        return result;
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

    /*private ServiceConnection receiverConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            receiverSvc = ((ReceiverSvc.ReceiverBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            receiverSvc = null;
        }
    };*/

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

        //Toast.makeText(this, "Creating", Toast.LENGTH_SHORT).show();

		active = true;
		created = true;
        conversationChanged = false;
		
		EncrypText app = ((EncrypText)getApplication());
		dbUtils = app.getDbUtils();
        cryptor = app.getCryptor();
		adapter = new ConversationAdapter(this, R.layout.conversation_item, new ArrayList<ConversationEntry>());
		//list = (ListView) findViewById(android.R.id.list); //how you reference that pesky bitch
		to = (AutoCompleteTextView) findViewById(R.id.phone);
        messageBox = (EditText) findViewById(R.id.message);
        context = this;

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
                ConversationActivity.number = formatNumber(c.getNumber());
                updateTo(c.getName());
                other = ContactUtils.getBitmap(getContentResolver(), number);

                if (c.getAlpha() == HALF) {

                    SharedPreferences prefs = getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE);

                    if (!prefs.contains("firstTimeExchange"))
                    {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Send key exchange request to " + c.getName() + "?");
                        builder.setMessage("Like a friend request, this action will have to be confirmed or denied by the the other person. If they respond to the request, " +
                                "you will both exchange your public keys and create a shared private key which will be used to encrypt your actual messages. " +
                                "Tap Yes to send the other person a key exchange request with your public key and return to the home screen. For subsequent conversations this dialog will not be shown");

                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                                getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE).edit()
                                        .putBoolean("firstTimeExchange", false).apply();
                                        //.putString(number, "inNegotiation").apply();
                                startKeyExchange();
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
                    }
                    else
                    {
                        Toast.makeText(ConversationActivity.this, "Sending public key", Toast.LENGTH_SHORT).show();
                        startKeyExchange();
                    }
                }
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
		
		me = ContactUtils.getBitmap(getContentResolver(), null); //Get user's photo
		
		Bundle b = getIntent().getExtras();
		
		if (b != null && b.containsKey(EncrypText.ADDRESS) && b.containsKey(EncrypText.NAME)) //if reading in existing conv
		{
			number = b.getString(EncrypText.ADDRESS);
			other = ContactUtils.getBitmap(getContentResolver(), number);
			adapter.addAll(dbUtils.loadConversation(number, 0));
			
			to.setVisibility(View.GONE);

            try {
                secretKey = cryptor.loadSecretKey(number);
            } catch (InvalidKeyTypeException e) {
                Log.e(TAG, "Unable to load secret key", e);
                Toast.makeText(this, "Unable to load secret key", Toast.LENGTH_SHORT).show();
            }
		}
		
		setListAdapter(adapter);
		
		if (newData)
			newData = false;
	}

    private void startKeyExchange()
    {
        senderSvc.sendKey(cryptor.getMyPublicKey(), number);
        finish();
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
        }
        else if(item != null)
        {
            item.setPhoto(other);
            adapter.add(item);
            conversationChanged = true;
        }
		else if (messages != null)
		{	
			for (ConversationEntry entry : messages) //do not need times. Have been assigned in svc
            {
                entry.setPhoto(this.other);
				adapter.add(entry);
            }

            conversationChanged = true;
		}
		else if (address != null) //for jumping to new conv via notification
		{
			adapter.clear();
			List<ConversationEntry> conv = dbUtils.loadConversation(number, 0);
			adapter.addAll(conv);
			AutoCompleteTextView To = (AutoCompleteTextView)findViewById(R.id.phone);
			number = address;
			To.setVisibility(View.GONE);
            conversationChanged = false;
            created = true;
		}
		else if (time != null)
		{
			int pos = intent.getIntExtra(EncrypText.THREAD_POSITION, -1);

            ConversationEntry temp = adapter.getItem(pos);
            temp.setDate(time);

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
            //int shift = findShift();
			List<ConversationEntry> newMessages = dbUtils.loadConversation(number, adapter.getData().get(adapter.getData().size() - 1).getMessageId());

			this.adapter.addAll(newMessages);

            ((NotificationManager)getSystemService(
                    Context.NOTIFICATION_SERVICE)).cancel(number.hashCode());
			//receiverSvc.removeHeldTexts(number); on a theory, dont need this

            newData = false;
		}
	}

	/**
	 * Method called by the GUI when the send message button is pressed. Hides the contact selection
	 * box, as a contact has been selected, displays the message to send, and calls the sendText method
	 * to handle the packetization. Writes the sent message to file.
	 * 
	 * Note: do something to show message sent: confirmation check?
	 * 
	 * @param v The button triggering the send
	 */
	public void sendMessage(View v)
	{
		if (!"".equals(number))
		{
			to.setVisibility(View.GONE);
            Editable editable = messageBox.getText();

            if(editable == null)
            {
                Log.v(TAG, "Could not access messageBox Editable");
                return;
            }

            String text = editable.toString();

            ConversationEntry item = new ConversationEntry(text, number, "Me", "Sending", me);

            senderSvc.sendMessage(item, adapter.getCount(), secretKey);

            adapter.add(item);
            conversationChanged = true;
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
}