package bmoore.encryptext;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ListView;
import java.util.ArrayList;


/**
 * This class is the main activity for this app. Only one instance of it is ever created at a time. It handles
 * the conversation thread list, and starts the conversation view
 * 
 * @author Benjamin Moore
 *
 */
public class Main extends ListActivity
{
	private static boolean active = false;
    private static boolean created = false;
	private static boolean newData = false;
	private EncrypText app;
	private ConversationAdapter adapter;
	private Files manager;
    private static final String TAG = "Main";

	/**
	 * Returns whether the activity is running or paused
	 * @return Run state of the activity
	 */
	public static boolean isActive()
	{
		return active;
    }

    public static boolean isCreated()
    {
        return created;
    }

    static void setCreated()
    {
        created = true;
    }


	/**
	 * Method for app service to inform the activity there is new data to read from file
	 */
	public static void setNewData()
	{
		newData = true;
	}

	/**
	 * Method invoked by android when the app is first started - this is the root activity for the app.
	 * Takes in a Bundle to restore state from, but currently the app does not save or restore state - this
	 * parameter is not used.
	 * 
	 * This method sets the static variables active and created to true, to mark instance creation, and sets
	 * the GUI layout associated with this activity. It also reads all the preview files and displays their
	 * contents
	 * 
	 * @param savedInstanceState to restore previous states from.
	 */
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_encryp_text);
		
		active = true;
        created = true;
		
		app = ((EncrypText)getApplication());
		manager = app.getFileManager();
		adapter = new ConversationAdapter(this, R.layout.activity_encryp_text,
                new ArrayList<ConversationEntry>());
		
		
		adapter.addAll(manager.readPreviews(this, getContentResolver()));
		
		if(adapter.getCount() == 0)
			adapter.add(new ConversationEntry("No conversations. Talk to someone!",
                    null, null, null, null));
		
		setListAdapter(adapter);

		
		if (newData)
			newData = false;
	}

	/**
	 * Auto-generated method to show the settings menu. Currently not in use
	 * 
	 * @param menu instance
	 */
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.activity_encryp_text, menu);
		return true;
	}

	/**
	 * Sets the static variable created to false, to indicate that there
	 * are no more instances of this activity running. Also prompts the service to exit, if possible
	 */
	public void onDestroy()
	{
        created = false;
        if(ReceiverSvc.isCreated())
        {
            Log.i(TAG, "Bumping service for quit check");
            Intent in = new Intent(ReceiverSvc.class.getName());
            //in.putExtra("a", app.getPhoneNumber());
            startService(in);
        }
        else
            Log.i(TAG, "Service not running");

		super.onDestroy();
	}

	/**
	 * Event handler causing the load of a conversation. When the user taps on a thread item from the
	 * main screen, this method is called, the data behind the thread entry retrieved, and fed into 
	 * a method which starts the thread view.
	 * 
	 * @param l - the item clicked
	 * @param v - a less generic version of the item clicked
	 * @param pos - an int specifying which item in the list this is. Corresponds with underlying data's position in
	 * adapter.
	 * @param id - a long specifying the ID of I don't know what. Unused.
	 */
	public void onListItemClick(ListView l, View v, int pos, long id)
	{
		ConversationEntry item = (ConversationEntry)l.getAdapter().getItem(pos);

        String number = item.getNumber();
        String name = item.getName();

        if(number != null && name != null)
            startConversationView(number, name);
	}

	
	/**
	 * Method allowing this activity to receive messages from the rest of the app, via an Intent.
	 * The only message this activity is passed is an updated thread preview. This method retrieves
	 * the new preview from the intent and updates the thread listing with it.
	 * 
	 * @param intent carrying the new thread preview
	 */
	public void onNewIntent(Intent intent)
	{
		active = true;
		ConversationEntry item = intent.getExtras().getParcelable("M");
		if (item != null)
		{
			updateList(item);
			Intent in = new Intent(ReceiverSvc.class.getName());
			in.putExtra("d", item.getNumber());
			startService(in);
		}
	}

	/**
	 * Bookkeeping method for the class. Sets active running marker to false.
	 */
	public void onPause()
	{
		active = false;
		super.onPause();
	}

	/**
	 * This method reads in any new data when coming back to this app via the launcher, if the service
	 * has marked new data for this activity
	 */
	public void onResume()
	{
		super.onResume();
		active = true;
		if (newData)
		{
			ArrayList<ConversationEntry> previews = manager.readPreviews(this, getContentResolver());
			adapter.clear();
			adapter.addAll(previews);
			newData = false;
		}
	}

	/**
	 * Method called by the "Write" button in the GUI. This method starts the conversation thread activity.
	 * To have android know to call this method from GUI xml only, the View parameter must be present. The view
	 * instance is not needed to start the activity however and is unused.
	 * 
	 * @param v - an instance of View that is unused
	 */
	public void startConversationView(View v)
	{
		startActivity(new Intent(this, Conversation.class));
	}

	/**
	 * Method called by onListItemClick to start the conversation view with a previous conversation to load in.
	 * 
	 * @param number - A String with the phone number of the person in the thread to load
	 * @param name - A String with the name of the person in the thread to load
	 */
	public void startConversationView(String number, String name)
	{
		Intent in = new Intent(this, Conversation.class);
		in.putExtra("a", number);
		in.putExtra("n", name);
		startActivity(in);
	}

	/**
	 * Method called by onNewIntent to handle the replacement of an item in the thread list.
	 * Iterates through the list until it finds an entry which matches the phone number of its input
	 * parameter, then removes that item and inserts the new one. Removal and re-insert is done because
	 * android will not update the screen otherwise
	 * 
	 * 
	 * @param item - A ConversationEntry instance to insert
	 */
	public void updateList(ConversationEntry item)
	{
		String toReplace = item.getNumber();

		for(int i = 0; i < adapter.getCount(); i++)
		{
			if (toReplace.equals(adapter.getItem(i).getNumber()))
			{
				item.setPhoto(adapter.data.get(i).getPhoto());
				adapter.remove(adapter.data.get(i));
				adapter.add(item);

				return;
			}
		}	
	}
}