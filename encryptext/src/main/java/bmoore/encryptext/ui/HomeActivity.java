package bmoore.encryptext.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;
import bmoore.encryptext.model.ConversationAdapter;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.services.ReceiverSvc;
import bmoore.encryptext.services.SenderSvc;
import bmoore.encryptext.utils.ContactUtils;
import bmoore.encryptext.utils.DBUtils;


/**
 * This class is the main activity for this app. Only one instance of it is ever created at a time. It handles
 * the conversation thread list, and starts the conversation view
 * 
 * @author Benjamin Moore
 *
 */
public class HomeActivity extends AppCompatActivity
{
	private static boolean active = false;
    private static boolean created = false;
	private static boolean newPreviews = false;

    private static boolean newKeyRequests = false;

	private EncrypText app;
	private ConversationAdapter adapter;
	private DBUtils dbUtils;
    private static final String TAG = "HomeActivity";

    private Menu menu;

    private static final int RECEIVE_SMS_REQUEST_CODE = 3;
    private static final String NO_CONVERSATIONS_MESSAGE = "No conversations. Talk to someone!";


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

    public static void setCreated()
    {
        created = true;
    }


	/**
	 * Method for app service to inform the activity there is new data to read from file
	 */
	public static void setNewPreviews()
	{
		newPreviews = true;
	}

    public static void setNewKeyRequests() { newKeyRequests = true; }

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
        setContentView(R.layout.home_screen);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.home_screen_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar bar = getSupportActionBar();
        bar.setIcon(R.mipmap.ic_stat_notification);

        active = true;
        created = true;

        app = ((EncrypText)getApplication());
        dbUtils = app.getDbUtils();

        adapter = new ConversationAdapter(this, R.layout.home_screen,
                new ArrayList<ConversationEntry>());


        new LoadPreviewsTask().execute();

        ListView list = (ListView) findViewById(R.id.home_screen_list);

        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ConversationEntry item = (ConversationEntry)parent.getAdapter().getItem(position);

                String number = item.getNumber();
                String name = item.getName();

                if(number != null && name != null)
                    startConversationView(number, name);
            }
        });


        if (newPreviews)
            newPreviews = false;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS},
                    RECEIVE_SMS_REQUEST_CODE);
        }
	}

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RECEIVE_SMS_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_DENIED) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Permissions Denied");
                    builder.setMessage("This app requires the ability to receive sms messages to function. Exiting the application.");

                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                            finish();
                        }
                    });
                }
            }
        }
    }

	/**
	 * Auto-generated method to show the settings menu. For use with action bar
	 * 
	 * @param menu instance
	 */
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.home_menu_options, menu);

        this.menu = menu;

        if(dbUtils.getKeyRequestsCount() > 0)
        {
            menu.getItem(0).setIcon(R.drawable.ic_person_black_24dp);
            newKeyRequests = false;
        } else {
            menu.getItem(0).setIcon(R.drawable.ic_person_outline_black_24dp);
        }

		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_view_requests:
                startActivity(new Intent(this, KeyRequestsActivity.class));
                return true;

            case R.id.action_settings:
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
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
            Log.i(TAG, "Bumping receiver service for quit check");
            Intent in = new Intent(this, ReceiverSvc.class);
            //in.putExtra("a", app.getPhoneNumber());
            startService(in);
        }
        else
            Log.i(TAG, "Receiver not running");

        if(SenderSvc.isCreated())
        {
            Log.i(TAG, "Bumping sender service for quit check");
            Intent in = new Intent(this, SenderSvc.class);
            in.putExtra(EncrypText.QUIT_FLAG, true);
            startService(in);
        }

		super.onDestroy();
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

        int flags = intent.getIntExtra(EncrypText.FLAGS, -1);

        if(flags == EncrypText.FLAG_UPDATE_KEY_REQUESTS_ICON)
        {
            menu.getItem(0).setIcon(R.drawable.ic_person_black_24dp);
            newKeyRequests = false;
        }
        else {
            ConversationEntry item = intent.getExtras().getParcelable(EncrypText.THREAD_ITEM);
            if (item != null) {
                updateList(item);
                Intent in = new Intent(this, ReceiverSvc.class);
                in.putExtra(EncrypText.ADDRESS, item.getNumber());
                startService(in);
            }
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
		if (newPreviews)
		{
            adapter.clear();
            new LoadPreviewsTask().execute();
			newPreviews = false;
		}

        if(newKeyRequests)
        {
            menu.getItem(0).setIcon(R.drawable.ic_person_black_24dp);
            newKeyRequests = false;
        }

        if(menu != null && dbUtils.getKeyRequestsCount() == 0)
            menu.getItem(0).setIcon(R.drawable.ic_person_outline_black_24dp);
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
		startActivity(new Intent(this, ConversationActivity.class));
	}

	/**
	 * Method called by onListItemClick to start the conversation view with a previous conversation to load in.
	 * 
	 * @param number - A String with the phone number of the person in the thread to load
	 * @param name - A String with the name of the person in the thread to load
	 */
	public void startConversationView(String number, String name)
	{
		Intent in = new Intent(this, ConversationActivity.class);
		in.putExtra(EncrypText.ADDRESS, number);
		in.putExtra(EncrypText.NAME, name);
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
        if(adapter.getCount() == 1 && adapter.getData().get(0).getMessage().equals(NO_CONVERSATIONS_MESSAGE)) {
            adapter.clear();
            adapter.add(item);
        } else {
            String toReplace = item.getNumber();

            for(int i = 0; i < adapter.getCount(); i++)
            {
                if (toReplace.equals(adapter.getItem(i).getNumber()))
                {
                    item.setPhoto(adapter.getData().get(i).getPhoto());
                    adapter.remove(adapter.getData().get(i));
                    adapter.add(item);

                    return;
                }
            }
        }
	}

    private class LoadPreviewsTask extends AsyncTask<Void, Void, List<ConversationEntry>> {

        @Override
        protected List<ConversationEntry> doInBackground(Void... params) {
            return dbUtils.readPreviews();
        }

        @Override
        protected void onPostExecute(List<ConversationEntry> previews) {
            adapter.addAll(previews);

            if(adapter.getCount() == 0)
                adapter.add(new ConversationEntry(NO_CONVERSATIONS_MESSAGE,
                        null, null, null, null));
        }
    }
}