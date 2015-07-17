package bmoore.encryptext;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

public class ReceiverSvc extends Service
{
    private final IBinder binder = new ReceiverBinder();
	private final int MAX_DATA_BYTES = 133;
    private final int HEADER_LENGTH = 4;
    private Thread worker;
    private LinkedList<Intent> intents;
    private Files manager;
    private Cryptor cryptor;
    private EncrypText app;
    private int processingStatus;
    private static boolean created;
    private TreeMap<String, TreeMap<Integer, byte[][]>> pendingTexts;
    private TreeMap<String, ArrayList<ConversationEntry>> finishedTexts;
    private static TreeMap<String, TreeMap<Integer, Timer>> messageExp;



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
            if(header[0] == 1)
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

                if(cryptor.checkAndHold(key, address, getContactName(address)))
                {
                    /*Log.i(TAG, "Replying with key");
                    Intent in = new Intent(SenderSvc.class.getName());
                    in.putExtra("k", cryptor.getMyKeyBytes());
                    in.putExtra("a", address);
                    startService(in);*/

                    thread.remove(Integer.valueOf(seq));
                    Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
                    processingStatus--;
                    Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);

                    Log.i(TAG, "Generating secret key");
                    SecretKey secretKey;
                    try {
                        secretKey = cryptor.finalize(address);
                    }
                    catch (InvalidKeyException e) {
                        Log.e(TAG, "Error generating secret key", e);
                        Toast.makeText(this, "Could not generate secret key from exchange", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if ((Conversation.isActive()) && (Conversation.currentNumber().equals(address)))
                    {
                        Log.i(TAG, "Passing secret key to Conversation");
                        Intent in = new Intent(this, Conversation.class);
                        in.putExtra(EncrypText.KEY, secretKey);
                        in.setFlags(872415232); //Basically, clear top | single top | new task, as I recall.
                        startActivity(in);
                    }
                }
            }
            else if(header[0] == 2) //2 for regular aes encrypted message
            {
                Log.i(TAG, "Building complete message");
                SecretKey key = cryptor.loadSecretKey(address);
			    String message = buildMessage(thread.get(seq), key, seq);

			    thread.remove(Integer.valueOf(seq));
                Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);
                processingStatus--;
                Log.i(TAG, "AddMsgFragment Processing status " + processingStatus);

			    passOrNotify(buildThreadEntry(address, message));
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
		Object[] pdus = (Object[]) bundle.get("pdus");

		SmsMessage sms;

		for (Object pdu : pdus)
		{
			//--retrieve relevant information--//
			sms = SmsMessage.createFromPdu((byte[]) pdu);

            String address = sms.getOriginatingAddress();
            byte[] header = readHeader(sms.getUserData());
            byte[] body = readBody(sms.getUserData());
			addMsgFragment(address, header, body);
		}
	}

    private String getContactName(String address)
    {
        Uri temp = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI;

        if(temp == null)
        {
            Log.v(TAG, "Could not read ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI");
            return null;
        }

        Uri path = Uri.withAppendedPath(temp, address);

        if(path == null)
        {
            Log.v(TAG, "Construction of path to contact failed with given base and address");
            return null;
        }


        Log.i(TAG, "Obtaining name");
        String[] projection = {ContactsContract.Contacts.DISPLAY_NAME };
        Cursor cr = getContentResolver().query(path, projection, null, null, null);

        if (cr == null || !cr.moveToFirst())
            return "";

        String name = cr.getString(0); //can use zero, bc only queried for one column
        cr.close();
        return name;
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
        String name = getContactName(address);

        Log.i(TAG, "Building date");
        String time = buildDate();
        Log.i(TAG, "Date of " + time);
		
		return new ConversationEntry(message, address, name, time, null);
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
        if(!Main.isCreated() && !Conversation.isCreated() && processingStatus == 0)
        {
            Log.i(TAG, "Quit Check Passed");
            stopSelf();
        }
    }

	private void makeNotification(ConversationEntry item)
	{
		Notification.Builder builder = new Notification.Builder(this);
		Intent in = new Intent(ReceiverSvc.class.getName());

		in.putExtra("a", item.getNumber());
		in.putExtra("n", item.getName());

        Intent d = new Intent(ReceiverSvc.class.getName());
        d.putExtra("a", item.getNumber());

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
		builder.setSound(RingtoneManager.getDefaultUri(2));
		builder.setLights(-16711936, 1000, 3000);
		builder.setDefaults(2);


		((NotificationManager)getSystemService(ReceiverSvc.NOTIFICATION_SERVICE) //Note: valid?
        ).notify(item.getNumber().hashCode(), builder.build());
	}

	private void passOrNotify(ConversationEntry item)
	{
        Log.i(TAG, "Writing message");
		String address = item.getNumber();
		manager.writeSMS(address, item, -1, this); //should add to end
		
		if ((Conversation.isActive()) && (Conversation.currentNumber().equals(address)))
		{
            Log.i(TAG, "Passing");
			Intent in = new Intent(this, Conversation.class);
			in.putExtra("M", item);
			in.setFlags(872415232); //Basically, clear top | single top | new task, as I recall.
			startActivity(in);
		}
		else if (Main.isActive())
		{
            manager.writePreview(item, this);

			Intent in = new Intent(this, Main.class);
			item.setAddress(address);
			in.putExtra("M", item);
			in.setFlags(872415232);
			startActivity(in);
		}
		else //notify
		{
            manager.writePreview(item, this);

			if(!finishedTexts.containsKey(address))
				finishedTexts.put(address, new ArrayList<ConversationEntry>());

            Log.i(TAG, "PassOrNotify Processing status " + processingStatus);
			finishedTexts.get(address).add(item);
            processingStatus++;
            Log.i(TAG, "PassOrNotify Processing status " + processingStatus);
			makeNotification(item);
			Conversation.setNewData();
		}
	}

	private void process(String address, String name)
	{
        Intent in = new Intent(this, Conversation.class);

		if ((Conversation.isCreated()) && (Conversation.currentNumber().equals(address)))
        {
			in.putExtra("Ms", this.finishedTexts.get(address));
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        }
		else if ((Conversation.isCreated()) && (!Conversation.currentNumber().equals(address)))
        {
            //file reload pass
			in.putExtra("a", address);
            in.putExtra("n", name);

            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(in);
        }
        else //cold start from file
        {
            in.putExtra("a", address);
            in.putExtra("n", name);
            in.addFlags(268435456);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(Conversation.class);
            stackBuilder.addNextIntent(in);
            stackBuilder.startActivities();
            Main.setCreated();
            Conversation.setCreated();
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

        worker = new Thread("Receiver Worker")
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
        worker.start();
		
		manager = app.getFileManager();
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
                Bundle pdus = b.getBundle("pdus");
                String name = b.getString("n");
                String address = b.getString("a");
                int pos = b.getInt("p", -1);

                if (pdus != null)
                {
                    Log.i(TAG, "Building pdus");
                    buildPdus(pdus);
                }
                else if(name != null && address != null)
                {
                    Log.i(TAG, "Handling notification activation");
                    process(address, name);
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

    private String buildDate()
    {
        final int MAX_DATE_LENGTH = 19;

        Calendar cal = app.getCal();
        String time;

        int hour = cal.get(Calendar.HOUR);

        if(hour == 0)
            time = "12:";
        else
            time = hour + ":";


        int minute = cal.get(Calendar.MINUTE);
        if(minute < 10) //apply minute filtering
            time += "0" + minute;
        else
            time += minute;

        if(cal.get(Calendar.AM_PM) == 0)
            time += " AM";
        else
            time += " PM";

        time += "," + cal.get(Calendar.MONTH) + ","
                + cal.get(Calendar.DAY_OF_MONTH) + "," + cal.get(Calendar.YEAR);

        int padLength = MAX_DATE_LENGTH - time.length();

        for(int i = 0; i < padLength; i++) //pad. Bump that String.format noise
            time += "*";

        return time;
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