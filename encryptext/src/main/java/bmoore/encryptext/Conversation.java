package bmoore.encryptext;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.Profile;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.crypto.SecretKey;

/**
 * This class handles the selection of a person to send a text to, the sending of texts, and the display of sent
 * and received texts. Only one instance of this class is ever created, and so static methods are used alongside
 * regular methods for bookkeeping purposes.
 * 
 * @author Benjamin Moore
 *
 */
public class Conversation extends ListActivity
{
    private static final String TAG = "Conversation";
    private static final int HALF = 50;
    private static final int FULL = 100;
	private static boolean active = false;
	private static boolean created = false;
	private static boolean newData = false;
    private static boolean newConfs = false;
    private long lastReadPosition;
    private boolean conversationChanged;
	private String name = "";
	private static String number = "";
	private Bitmap me;
	private Bitmap other;
    private Files manager;
    private Cryptor cryptor;
    private SenderSvc senderSvc;
    private PublicKey publicKey;
    private SecretKey secretKey;
    private Thread loader;
    private boolean shouldLoaderWait;
    private ArrayList<Contact> suggestions;
    private String query;
	private ConversationAdapter adapter;
	private ContactAdapter contacts;
	private AutoCompleteTextView to;
    private EditText messageBox;
    private Context context;



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

    static void setCreated()
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
		manager = app.getFileManager();
        cryptor = app.getCryptor();
		adapter = new ConversationAdapter(this, R.layout.convitem, new ArrayList<ConversationEntry>());
		//list = (ListView) findViewById(android.R.id.list); //how you reference that pesky bitch
		to = (AutoCompleteTextView) findViewById(R.id.phone);
        messageBox = (EditText) findViewById(R.id.message);
        context = this;

		contacts = new ContactAdapter(this, R.layout.contact, new ArrayList<Contact>());
		to.setAdapter(contacts);

        shouldLoaderWait = true;
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

                            secretKey = cryptor.loadSecretKey(formatNumber(number));
                            publicKey = cryptor.loadPublicKey(formatNumber(number));

                            if(secretKey == null) //might have to re-engineer contact to store secret key
                                suggestions.add(new Contact(name, number, thumb, HALF));
                            else
                                suggestions.add(new Contact(name, number, thumb, FULL));
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

        loader.start();
		
		to.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            /**
             * Method to update the respective class variables and fill the contact selection box when
             * a suggestion has been tapped on. Calls formatNumber, updateTo, and setBitmap.
             */
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Contact c = contacts.getItem(pos);
                Conversation.number = formatNumber(c.getNumber());
                updateTo(c.getName());
                setBitmap(Conversation.number);

                if (c.getAlpha() == HALF) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Negotiate Keys");
                    builder.setMessage("No key found for " + c.getName() + ". Begin key negotiation?");
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Bundle b = new Bundle();
                            b.putSerializable(EncrypText.KEY, cryptor.getMyPublicKey());
                            b.putString(EncrypText.ADDRESS, number);
                            senderSvc.addJob(b);
                        }
                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                //if alpha is half, present popup window and negotiate key
                //else load key
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
		
		setBitmap(null); //Get user's photo
		
		Bundle b = getIntent().getExtras();
		
		if (b != null && b.containsKey(EncrypText.ADDRESS) && b.containsKey(EncrypText.NAME)) //if reading in existing conv
		{
			number = b.getString(EncrypText.ADDRESS);
			setBitmap(number); //get other's photo
			adapter.addAll(manager.readConv(this, number, 0, me, other));
			
			name = b.getString(EncrypText.NAME);
			to.setVisibility(View.GONE);

            secretKey = cryptor.loadSecretKey(number);
		}
		
		setListAdapter(adapter);
		
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
        number = "";

        manager.resetPointer(number + ".dat");
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
            Log.v(TAG, "Bundle passed to Conversation was null");
            return;
        }

        ConversationEntry item = b.getParcelable(EncrypText.THREAD_ITEM);
		ArrayList<ConversationEntry> messages = b.getParcelableArrayList(EncrypText.MULTIPLE_THREAD_ITEMS);
		String time = b.getString(EncrypText.TIME);
		String address = b.getString(EncrypText.ADDRESS);
        String name = b.getString(EncrypText.NAME);
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
			ArrayList<ConversationEntry> conv = manager.readConv(this, address, 0, me, other);
			adapter.addAll(conv);
			AutoCompleteTextView To = (AutoCompleteTextView)findViewById(R.id.phone);
			number = address;
            this.name = name;
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

        lastReadPosition = manager.getLastReadPosition(number);

		if (adapter.getCount() > 0 && conversationChanged)
		{	
			ConversationEntry item = adapter.getItem(adapter.getCount() - 1);
			manager.writePreview(new ConversationEntry(item.getMessage(), number, name,
                    item.getDate(), null), this);
			Main.setNewData();
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
			ArrayList<ConversationEntry> newMessages = manager.readConv(this,
                    number, lastReadPosition, me, other);

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

            ConversationEntry item = new ConversationEntry(text, number, "Me", "Sending************", me);

            Bundle b = new Bundle();
            b.putParcelable(EncrypText.THREAD_ITEM, item);
            b.putInt(EncrypText.THREAD_POSITION, adapter.getCount());
            b.putSerializable(EncrypText.KEY, secretKey);

            senderSvc.addJob(b);

            adapter.add(item);
            conversationChanged = true;
		}
	}




	/**
	 * Method to load a contact picture given a phone number. If passed a null String, queries the content
	 * resolver for the user profile ID, using the ContactsContract.Profile constants, and then uses the
	 * Contacts' class method to open a photo stream. If passed a non-null String, uses the PhoneLookup
	 * class together with the number passed to construct a Uri to search for and retrieve a contact ID,
	 * performing the same steps afterward.
     *
	 * @param number - the phone number to retrieve a picture for
	 */
	public void setBitmap(String number)
	{
		Uri path;
		long ID = -1L;
		Cursor c;
		if (number == null)
		{
			path = Profile.CONTENT_URI;

            if(path == null) //do nothing if we couldn't access system services
            {
                Log.v(TAG, "Could not read Profile.CONTENT_URI");
                return;
            }

			c = getContentResolver().query(path, new String[] { Profile._ID }, null, null, null);

            if(c == null)
            {
                Log.v(TAG, "Content resolver returned no results for personal contact card query");
                return;
            }

			if (c.moveToFirst())
			{
				do
					ID = c.getLong(0);
				while (c.moveToNext());
			}
			if (ID > Profile.MIN_ID)
			{
                Uri temp = Contacts.CONTENT_URI;

                if(temp == null) //do nothing if we couldn't access system services
                {
                    Log.v(TAG, "Could not read Contacts.CONTENT_URI");
                    return;
                }

				path = Uri.withAppendedPath(temp, "" + ID);

                if(path == null) //if construction failed, do nothing
                {
                    Log.v(TAG, "Could not construct path with supplied ID & URI");
                    return;
                }

				this.me = BitmapFactory.decodeStream(Contacts.openContactPhotoInputStream(
                        getContentResolver(), path));
			}
		}
		else
		{
            Uri temp = PhoneLookup.CONTENT_FILTER_URI;

            if(temp == null)
            {
                Log.v(TAG, "Could not read PhoneLookup.CONTENT_FILTER_URI");
                return;
            }

			path = Uri.withAppendedPath(temp, Uri.encode(number));

            if(path == null)
            {
                Log.v(TAG, "Could not construct Uri from PhoneLookup.CONTENT_FILTER_URI and " +
                        "phone number provided");
                return;
            }

			c = getContentResolver().query(path, new String[] { Contacts._ID }, null, null, null);

            if(c == null)
            {
                Log.v(TAG, "Content resolver returned no results for target contact card query");
                return;
            }

			c.moveToFirst();
			ID = c.getLong(0);

            temp = Contacts.CONTENT_URI;

            if(temp == null) //do nothing if we couldn't access system services
            {
                Log.v(TAG, "Could not read Contacts.CONTENT_URI");
                return;
            }

			path = Uri.withAppendedPath(temp, "" + ID);

            if(path == null)
            {
                Log.v(TAG, "Could not construct Uri from Contacts.CONTENT_URI and ID provided");
                return;
            }

			this.other = BitmapFactory.decodeStream(Contacts.openContactPhotoInputStream(
                    getContentResolver(), path));
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
		this.name = name;
	}
}