package bmoore.encryptext.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;
import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.ui.ConversationActivity;
import bmoore.encryptext.ui.HomeActivity;
import bmoore.encryptext.utils.ContactUtils;
import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.utils.DateUtils;
import bmoore.encryptext.utils.InvalidKeyTypeException;

public class ReceiverSvc extends Service
{
    private final IBinder binder = new ReceiverBinder();
	private final int MAX_DATA_BYTES = 133;
    private final int HEADER_LENGTH = 4;
    private LinkedList<Intent> intents;
    private DBUtils dbUtils;
    private Cryptor cryptor;
    private EncrypText app;
    private int processingStatus;
    private static boolean created;
    private TreeMap<String, TreeMap<Integer, byte[][]>> pendingTexts;
    private TreeMap<String, ArrayList<ConversationEntry>> finishedTexts;
    private static TreeMap<String, TreeMap<Integer, Timer>> messageExp;
    private int color;

    private final Thread worker = new Thread("Receiver Worker")
    {
        @Override
        public void run()
        {
            synchronized (worker)
            {
                while(true)
                {
                    handleIntents();
                    try
                    {
                        worker.wait();
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            }
        }
    };

    private static final String TAG = "ReceiverSvc";

	/**
	 * Method to handle slotting a new PDU into the service holding data structures, and checking
	 * if that PDU completes a logical message, as denoted by sequence number. 
	 * 
	 * First cancels any message expiration timer for the phone & sequence number combination that
	 * the incoming PDU belongs to, so as to prevent issues with concurrent access, and then checks to
	 * see if any relevant conversation thread exists at all for the number the PDU is from. If not,
	 * initializes it, and adds the message. If so, adds the message. Then checks whether the PDU is
	 * or completes a logical message. If so, passes to the activities or creates a notification. If
	 * not, starts a countdown. If no additional message fragments are received before the count reaches
	 * zero, the message fragment is thrown out.
	 * 
	 * Note: implement some maximum size on logical messages to prevent memory spam? Stop timers in buildPdus?
	 * Avoid writing ack to file?
	 * 
	 * @param address - A String phone number
	 * @param header - a byte array representing the header of the PDU (UDH)
	 * @param body - a byte array representing the body of a PDU
	 */
	private void addMsgFragment(String address, byte[] header, byte[] body)
	{
		//HEADER FMT: [msg type][msg seq #][frag #][part #] - [] denotes 1 byte
		int seq = header[1];

		//cancel timer when accessing message slots
		if(messageExp.containsKey(address) && messageExp.get(address).containsKey(seq))
		{
			messageExp.get(address).get(seq).cancel();
			messageExp.get(address).remove(seq);
		}

		int parts = header[2];
		int part = header[3];

        //init thread if not extant
        if(!pendingTexts.containsKey(address))
			pendingTexts.put(address, new TreeMap<Integer, byte[][]>());

        TreeMap<Integer, byte[][]> thread = pendingTexts.get(address); //retrieve reference

		if(!thread.containsKey(Integer.valueOf(seq))) //init slot if new logical message for thread
        {
			thread.put(seq, new byte[parts][MAX_DATA_BYTES - HEADER_LENGTH]);
            Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
            processingStatus++;
            Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
        }

        //allows us to still text ourselves: now we only put in message parts that we don't have
       // if(!Arrays.equals(thread.get(seq)[part], body))
        thread.get(seq)[part] = body;

		//check complete message & notify gui
		if(isCompleteMessage(thread.get(seq)))
		{
            //if receiving a public key
            if(header[0] == EncrypText.PUBLIC_KEY_PDU)
            {
                Log.i(TAG, "Received public key");

                String temp = "";
                for(byte b : body)
                    temp += b + " ";
                Log.i(TAG, temp);

                byte[] key = buildKey(thread.get(seq), seq);
                temp = "";
                for(byte b : key)
                    temp += b + " ";
                Log.i(TAG, temp);

                try {
                    if (cryptor.checkAndHold(key, address)) {
                        showKeyExchangeNotification(address);

                        thread.remove(Integer.valueOf(seq));
                        Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
                        processingStatus--;
                        Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyTypeException e) {
                    Log.e(TAG, "Could not store received public key", e);
                    Toast.makeText(this, "Could not store received public key", Toast.LENGTH_SHORT).show();
                }
            }
            else if(header[0] == EncrypText.AES_ENCRYPTED_PDU) //2 for regular aes encrypted message
            {
                Log.i(TAG, "Building complete message");
                try {
                    SecretKey key = cryptor.loadSecretKey(address);
                    String message = buildMessage(thread.get(seq), key, seq);

                    thread.remove(Integer.valueOf(seq));
                    Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
                    processingStatus--;
                    Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);

                    passOrNotify(buildThreadEntry(address, message));
                } catch (InvalidKeyTypeException e) {
                    Log.e(TAG, "Could not load secret key", e);
                    Toast.makeText(this, "Could not load secret key", Toast.LENGTH_SHORT).show();
                }
            }
		}
		else //add || update timer in slot
		{
			if(!messageExp.containsKey(address))
				messageExp.put(address, new TreeMap<Integer, Timer>());

			dumpMsg d = new dumpMsg(address, seq);
			messageExp.get(address).put(seq, new Timer());
			messageExp.get(address).get(seq).schedule(d, 30000);
		}
	}


	/**
	 * Method to construct a string message out of an array of PDUs. Loops through and
	 * constructs a byte array that is the concatenation of the PDUs, and then constructs
	 * a new String out of that array.
	 * 
	 * @param message - a two layer byte array
	 * @return - a String
	 */
	private String buildMessage(byte[][] message, SecretKey key, int sequenceNo)
	{
		byte[] buffer = new byte[message.length*(MAX_DATA_BYTES - HEADER_LENGTH)];


		for(int i = 0; i < message.length; i++)
		{
            byte[] pdu = message[i];
            System.arraycopy(pdu, 0, buffer, i*pdu.length, pdu.length);
		}

        // If a message is split over multiple pdus, it will probably have trailing 0s at the end which will
        //produce garbage during decryption
        buffer = trimTrailingNulls(buffer);

        byte[] decryptedBuffer = new byte[] {0};

        try {
            decryptedBuffer = cryptor.decryptMessage(buffer, key, sequenceNo);
        }
        catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "While decrypting message", e);
            Toast.makeText(this, "Error decrypting message", Toast.LENGTH_SHORT).show();
        }

        return new String(decryptedBuffer);
	}

    private byte[] trimTrailingNulls(byte[] buffer)
    {
        int i = buffer.length - 1;
        while(buffer[i] == 0)
            --i;
        // now buffer[i] is the last non-zero byte
        byte[] trimmed = new byte[i+1];
        System.arraycopy(buffer, 0, trimmed, 0, i+1);
        return trimmed;
    }

    private byte[] buildKey(byte[][] parts, int length)
    {
        byte[] key = new byte[length];

        int k = 0;
        for(int i = 0; i < parts.length || k < length; i++)
        {
            System.arraycopy(parts[i], 0, key, k, length - k);// length - k leads to overflow?
            k = length - k + 1;
        }

        return key;
    }

	/**
	 * Method to process all incoming PDUs in a bundle. Loops through bundle content,
	 * creating an SmsMessage instance for each PDU so as to be able to read the originating
	 * phone number from, as well as reading a byte header and body for each PDU and then 
	 * calling addMsgFragment on each part.
	 * 
	 * @param bundle - PDUs wrapped in an Android bundle
	 */
	private void buildPdus(Bundle bundle)
	{
		Object[] pdus = (Object[]) bundle.get(EncrypText.PDUS);
        String format = bundle.getString(EncrypText.FORMAT);

		SmsMessage sms;

		for (Object pdu : pdus)
		{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            }
            else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }

            String address = sms.getOriginatingAddress();
            byte[] header = readHeader(sms.getUserData());
            byte[] body = readBody(sms.getUserData());
			addMsgFragment(address, header, body);
		}
	}

	/**
	 * Method to retrieve the contact name associated with a phone number.
	 * Queries android contacts for name associated with number. If no 
	 * results, returns a ConversationEntry with the number the message is
	 * from in the name slot
	 *
	 * 
	 * @param address - a string representing a phone number
	 * @param message - a string representing the body of a text message
	 * @return A ConversationEntry filled out with a name if possible
	 */
	private ConversationEntry buildThreadEntry(String address, String message)
	{
        Log.i(TAG, "Building thread entry");

        String name = null;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            name = ContactUtils.getContactName(getContentResolver(), address);
        }

        if(name == null) { // needed as a separate check because devices below API 23 will always pass above
            name = address;
        }

        Log.i(TAG, "Building date");
        String time = DateUtils.buildDate();
        Log.i(TAG, "Date of " + time);


        boolean useDrawable = false;
        Bitmap pic = null;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pic = ContactUtils.getBitmap(getContentResolver(), address);
        }

        if(pic == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            useDrawable = true;
        } else if (pic == null) {
            pic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp);
        }

		ConversationEntry item = new ConversationEntry(message, address, name, time, pic);

        if(useDrawable) {
            item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
        }

		return item;
	}

    /**
     * Method to test a partially filled logical message to verify completion by checking
     * to see if the first bit of each PDU is not 0. The first bit of each PDU is the message
     * type, which is never 0.
     *
     * @param message a 2D byte array representing a collection of PDUs
     * @return whether all parts of message have been received
     */
	private boolean isCompleteMessage(byte[][] message)
	{

		for(byte[] pdu : message)
		{
			if(pdu[0] == 0)
				return false;
		}

		return true;
	}

    private void tryQuit()
    {
        if(!HomeActivity.isCreated() && !ConversationActivity.isCreated() && processingStatus == 0)
        {
            Log.i(TAG, "Quit Check Passed");
            stopSelf();
        }
    }

    private void showKeyExchangeNotification(String address)
    {
        SharedPreferences prefs = getSharedPreferences(EncrypText.class.getSimpleName(), MODE_PRIVATE);
        Notification.Builder builder = new Notification.Builder(this);

        String name;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            name = ContactUtils.getContactName(getContentResolver(), address);
        } else {
            name = address;
        }

        if(prefs.contains(address)) {
            prefs.edit().remove(address).apply();

            Log.i(TAG, "Generating secret key");
            SecretKey secretKey;
            try {
                secretKey = cryptor.createAndStoreSecretKey(address);
            }
            catch (InvalidKeyException | InvalidKeyTypeException e) {
                Log.e(TAG, "Error generating secret key", e);
                Toast.makeText(this, "Could not generate secret key from exchange", Toast.LENGTH_SHORT).show();
                return;
            }

            if ((ConversationActivity.isActive()) && (ConversationActivity.currentNumber().equals(address)))
            {
                Log.i(TAG, "Passing secret key to ConversationActivity");
                Intent in = new Intent(this, ConversationActivity.class);
                in.putExtra(EncrypText.KEY, secretKey);
                in.setFlags(872415232); //Basically, clear top | single top | new task, as I recall.
                startActivity(in);
            }

            builder.setContentTitle("Key exchange complete");
            builder.setContentText("You are ready to begin sending messages to " + name);
            builder.setAutoCancel(true);
        } else {

            Intent yes = new Intent(this, SenderSvc.class);
            yes.putExtra(EncrypText.KEY, cryptor.getMyPublicKey());
            yes.putExtra(EncrypText.ADDRESS, address); //comment slash change for local phone testing
            yes.putExtra(EncrypText.FLAGS, EncrypText.FLAG_GENERATE_SECRET_KEY);
            PendingIntent p1 = PendingIntent.getService(this, 1, yes, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_done_black_24dp, "Yes", p1);

            Intent no = new Intent(this, ReceiverSvc.class);
            no.putExtra(EncrypText.ADDRESS, address);
            no.putExtra(EncrypText.FLAGS, EncrypText.FLAG_REMOVE_PUBLIC_KEY);
            PendingIntent p2 = PendingIntent.getService(this, 2, no, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_clear_black_24dp, "No", p2);

            if(name == null || "".equals(name))
                name = address;

            Intent delete = new Intent(this, ReceiverSvc.class);
            delete.putExtra(EncrypText.DATE, DateUtils.buildDate());
            delete.putExtra(EncrypText.ADDRESS, address);
            delete.putExtra(EncrypText.NAME, name);
            PendingIntent p3 = PendingIntent.getService(this, 3, delete, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setDeleteIntent(p3);
            builder.setContentTitle("Request from " + name);
            builder.setContentText(name + " is requesting to swap public keys with you. Accept and reply with your key?");

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                builder.setLargeIcon(ContactUtils.getBitmap(getContentResolver(), address));
            } else {
                builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_box_gray_48dp));
            }

            builder.setAutoCancel(false);
        }

        builder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);
        builder.setSmallIcon(R.mipmap.ic_stat_notification);
        builder.setLights(color, 1000, 3000);

        ((NotificationManager) getSystemService(ReceiverSvc.NOTIFICATION_SERVICE)
        ).notify(address.hashCode(), builder.build());
    }

	private void makeNotification(ConversationEntry item)
	{
		Notification.Builder builder = new Notification.Builder(this);
		Intent in = new Intent(this, ReceiverSvc.class);

		in.putExtra(EncrypText.ADDRESS, item.getNumber());
		in.putExtra(EncrypText.NAME, item.getName());

        Intent d = new Intent(this, ReceiverSvc.class);
        d.putExtra(EncrypText.ADDRESS, item.getNumber());

		PendingIntent p = PendingIntent.getService(this, 0, in, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent del = PendingIntent.getService(this, 1, d, PendingIntent.FLAG_UPDATE_CURRENT);

		builder.setSmallIcon(R.mipmap.ic_stat_notification);
		builder.setContentTitle("Message from " + item.getName());

        String text = "";
        for(ConversationEntry msg : finishedTexts.get(item.getNumber()))
            text += msg.getMessage() + "\n";

		builder.setContentText(text);
		builder.setContentIntent(p);
		builder.setAutoCancel(true);
        builder.setDeleteIntent(del);
		builder.setLights(color, 1000, 3000);
		builder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);


		((NotificationManager)getSystemService(ReceiverSvc.NOTIFICATION_SERVICE) //Note: valid?
        ).notify(item.getNumber().hashCode(), builder.build());
	}

	private void passOrNotify(ConversationEntry item)
	{
        Log.i(TAG, "Writing message");
		String address = item.getNumber();
        dbUtils.storeMessage(item);
		//manager.writeSMS(address, item, -1, this); //should add to end
		
		if ((ConversationActivity.isActive()) && (ConversationActivity.currentNumber().equals(address)))
		{
            Log.i(TAG, "Passing");
			Intent in = new Intent(this, ConversationActivity.class);
			in.putExtra(EncrypText.THREAD_ITEM, item);
			in.setFlags(872415232); //Basically, clear top | single top | new task, as I recall.
			startActivity(in);
		}
		else if (HomeActivity.isActive())
		{
            //manager.writePreview(item, this);
			Intent in = new Intent(this, HomeActivity.class);
			item.setAddress(address);
			in.putExtra(EncrypText.THREAD_ITEM, item);
			in.setFlags(872415232);
			startActivity(in);
		}
		else //notify
		{
            //manager.writePreview(item, this);

			if(!finishedTexts.containsKey(address))
				finishedTexts.put(address, new ArrayList<ConversationEntry>());

            Log.i(TAG, "PassOrNotify Processing status " + processingStatus);
			finishedTexts.get(address).add(item);
            processingStatus++;
            Log.i(TAG, "PassOrNotify Processing status " + processingStatus);
			makeNotification(item);
			ConversationActivity.setNewData();
		}
	}

	private void process(String address, String name)
	{
        Intent in = new Intent(this, ConversationActivity.class);

		if ((ConversationActivity.isCreated()) && (ConversationActivity.currentNumber().equals(address)))
        {
			in.putExtra(EncrypText.MULTIPLE_THREAD_ITEMS, this.finishedTexts.get(address));
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        }
		else if ((ConversationActivity.isCreated()) && (!ConversationActivity.currentNumber().equals(address)))
        {
            //file reload pass
			in.putExtra(EncrypText.ADDRESS, address);
            in.putExtra(EncrypText.NAME, name);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(in);
        }
        else //cold start from file
        {
            in.putExtra(EncrypText.ADDRESS, address);
            in.putExtra(EncrypText.NAME, name);
            in.addFlags(268435456);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(ConversationActivity.class);
            stackBuilder.addNextIntent(in);
            stackBuilder.startActivities();
            HomeActivity.setCreated();
            ConversationActivity.setCreated();
        }

         Log.i(TAG, "Process Processing status " + processingStatus);
         processingStatus -= finishedTexts.get(address).size();
         finishedTexts.remove(address);
         Log.i(TAG, "Process Processing status " + processingStatus);
	}

    private byte[] readBody(byte[] pdu)
    {
        byte[] body = new byte[MAX_DATA_BYTES - HEADER_LENGTH];

        System.arraycopy(pdu, 4, body, 0, body.length);

        return body;
    }

	private byte[] readHeader(byte[] pdu)
	{
		byte[] header = new byte[HEADER_LENGTH];

        System.arraycopy(pdu, 0, header, 0, 4);

		return header;
	}

	public IBinder onBind(Intent paramIntent)
	{
		return binder;
	}

    public static boolean isCreated()
    {
        return created;
    }

    @Override
    public void onDestroy()
    {
        created = false;
        super.onDestroy();
    }

    @Override
	public void onCreate()
	{
		super.onCreate();

        // The attributes you want retrieved
        int[] attrs = {R.attr.colorPrimary};
        TypedArray ta = obtainStyledAttributes(R.style.AppTheme, attrs);
        color = ta.getColor(0, Color.BLACK);
        ta.recycle();

        Log.i(TAG, "ReceiverSvc created");

        created = true;
        processingStatus = 0;
		app = ((EncrypText)getApplication());

        if(app == null)
        {
            Log.v(TAG, "Error retrieving application instance");
            throw new NullPointerException();
        }

        intents = new LinkedList<>();

        worker.start();
		
		dbUtils = app.getDbUtils();
        cryptor = app.getCryptor();
		this.pendingTexts = new TreeMap<>();
		this.finishedTexts = new TreeMap<>();
		messageExp = new TreeMap<>();
	}

    private void handleIntents()
    {
        while(intents.size() > 0)
        {
            Log.i(TAG, "Grabbing intent");
            Intent in = intents.removeFirst();
            Bundle b = in.getExtras();

            if (b != null)
            {
                Bundle pdus = b.getBundle(EncrypText.PDUS);
                String name = b.getString(EncrypText.NAME);
                String address = b.getString(EncrypText.ADDRESS);
                String date = b.getString(EncrypText.DATE);
                int flags = b.getInt(EncrypText.FLAGS, -1);

                if (pdus != null)
                {
                    Log.i(TAG, "Building pdus");
                    buildPdus(pdus);
                }
                else if(date != null && name != null && address != null)
                {
                    Log.i(TAG, "Generating key request entry");
                    dbUtils.generateKeyRequestEntry(address, name, Contact.KeyStatus.NEEDS_REVIEW, date);
                    HomeActivity.setNewKeyRequests();

                    if (HomeActivity.isActive())
                    {
                        Intent update = new Intent(this, HomeActivity.class);
                        update.putExtra(EncrypText.FLAGS, EncrypText.FLAG_UPDATE_KEY_REQUESTS_ICON);
                        update.setFlags(872415232);
                        startActivity(update);
                    }
                }
                else if(name != null && address != null)
                {
                    Log.i(TAG, "Handling notification activation");
                    process(address, name);
                }
                else if(flags == EncrypText.FLAG_REMOVE_PUBLIC_KEY && address != null)
                {
                    Log.i(TAG, "Removing held public key");
                    try {
                        //cancel notification - action doesn't automatically do so
                        NotificationManager manager = (NotificationManager) getSystemService(ReceiverSvc.NOTIFICATION_SERVICE);
                        manager.cancel(address.hashCode());

                        cryptor.removePublicKey(address);
                    } catch (InvalidKeyTypeException e) {
                        Log.e(TAG, "Error removing public key", e);
                        Toast.makeText(this, "Error removing public key", Toast.LENGTH_SHORT).show();
                    }
                }
                else if(address != null)
                {
                    Log.i(TAG, "Removing held texts");
                    //Log.i(TAG, "Processing status " + processingStatus);
                    //processingStatus -= finishedTexts.get(address).size();
                    //Log.i(TAG, "Processing status " + processingStatus);
                    finishedTexts.remove(address);
                    tryQuit();
                }
            }
            else
                tryQuit();
        }
    }

	public int onStartCommand(Intent intent, int paramInt1, int paramInt2)
	{
        Log.i(TAG, "Intent received");
        intents.add(intent);

        if(worker.getState().equals(Thread.State.WAITING))
        {
            synchronized (worker)
            {
                Log.i(TAG, "Bumping thread");
                worker.notify();
            }
        }

        return START_REDELIVER_INTENT;
	}

    public void removeHeldTexts(String number)
    {
        finishedTexts.remove(number);
    }

    public class ReceiverBinder extends Binder
    {
        public ReceiverSvc getService()
        {
            return ReceiverSvc.this;
        }
    }

	private class dumpMsg extends TimerTask
	{
		private Integer msg;
		private String num;

		dumpMsg(String n, int pos)
		{
			this.msg = pos;
			this.num = n;
		}

		public void run()
		{
			pendingTexts.get(num).remove(msg);
		}
	}
}