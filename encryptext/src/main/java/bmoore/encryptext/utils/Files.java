package bmoore.encryptext.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Scanner;
import java.util.TreeMap;

import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ConversationEntry;


/**
 * @author Benjamin Moore
 */
public class Files {

    private Context context;
    private TreeMap<String, Long> readPositions;
    String address;
    private static final String SECRET_KEYSTORE_LOCATION = "secretkeystore.ks";
    private static final String PUBLIC_KEYSTORE_LOCATION = "publickeystore.ks";

    private static final String KEY_EXCHANGE_REQUEST_FILE = "keyExchangeRequests.dat";

    public Files(Context con, String address) {
        readPositions = new TreeMap<>();
        context = con;
        this.address = address;
    }


    KeyStore createOrLoadKeystore(boolean isPublic, final char[] PASSWORD) {
        KeyStore keyDB = null;
        File keyDBLocation;
        try {
            if (isPublic) {
                keyDB = KeyStore.getInstance("PKCS12"); //should be supported on all devices?
                keyDBLocation = context.getFileStreamPath(PUBLIC_KEYSTORE_LOCATION);
            } else {
                keyDB = KeyStore.getInstance("BouncyCastle"); //should be supported on all devices
                keyDBLocation = context.getFileStreamPath(SECRET_KEYSTORE_LOCATION);
            }

            if (keyDBLocation.exists()) {
                keyDB.load(context.openFileInput(keyDBLocation.getName()), PASSWORD);
            } else {
                keyDB.load(null, PASSWORD);
                keyDB.store(context.openFileOutput(keyDBLocation.getName(), Context.MODE_PRIVATE),
                        PASSWORD);
            }
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return keyDB;
    }

    void saveKeyStore(KeyStore keyDB, final char[] PASSWORD) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        if (keyDB.getType().equals("PKCS12"))
            keyDB.store(context.openFileOutput(PUBLIC_KEYSTORE_LOCATION, Context.MODE_PRIVATE), PASSWORD);
        else
            keyDB.store(context.openFileOutput(SECRET_KEYSTORE_LOCATION, Context.MODE_PRIVATE), PASSWORD);
    }

    private Bitmap findBitmap(String phone, ContentResolver cr) {
        Cursor c = cr.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)), new String[]{ContactsContract.Contacts._ID}, null, null, null);

        long ID = -1L;
        if (c.moveToFirst())
            do
                ID = c.getLong(0);
            while (c.moveToNext());

        c.close();

        Bitmap pic = null;
        if (ID != -1)
            pic = BitmapFactory.decodeStream(ContactsContract.Contacts.openContactPhotoInputStream(cr, Uri.parse("content://com.android.contacts/contacts/" + ID)));
        return pic;
    }


    public ArrayList<ConversationEntry> readConv(Context c, String number, long shift, Bitmap me,
                                                 Bitmap other) {
        ArrayList<ConversationEntry> conversation = new ArrayList<>();

        try {
            RandomAccessFile f = new RandomAccessFile(c.getFileStreamPath(number + ".dat"), "r");

            f.seek(shift);

            byte[] buf = new byte[(int) (f.length() - shift)];
            f.read(buf);

            readPositions.put(number, f.length()); //should be how much we read

            f.close();

            String[] entries = new String(buf).split("\r\n");

            for (int i = 0; i < entries.length; i++) {
                // public ConversationEntry(message, number, name, date, pic)
                String[] parts = entries[i].split("\n");

                if (parts[1].equals("Me"))
                    conversation.add(new ConversationEntry(parts[0], null, parts[1], parts[2], me));
                else
                    conversation.add(new ConversationEntry(parts[0], null, parts[1], parts[2], other));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return conversation;
    }

    public ArrayList<ConversationEntry> readPreviews(Context current, ContentResolver cr) {
        ArrayList<ConversationEntry> previews = new ArrayList<ConversationEntry>();

        File[] files = current.getFilesDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith("preview.dat");
            }
        });

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                long l1 = lhs.lastModified();
                long l2 = rhs.lastModified();
                if (l1 > l2)
                    return 1;
                if (l1 < l2)
                    return -1;
                return 0;
            }
        });

        for (File f : files) {
            try {
                FileInputStream reader = current.openFileInput(f.getName());

                byte[] buffer = new byte[(int) f.length()];
                reader.read(buffer);

                String[] contents = new String(buffer).split("\n");

                Bitmap pic = findBitmap(contents[1], cr);

                //message number name
                ConversationEntry temp = new ConversationEntry(contents[0], contents[1], contents[2],
                        contents[3], pic);

                previews.add(temp);

                reader.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return previews;
    }

    public void writePreview(ConversationEntry item, Context c) {
        if (Environment.getExternalStorageState().equals("mounted_ro"))
            return;
        try {
            FileOutputStream writer = c.openFileOutput(item.getNumber() + "preview.dat",
                    Context.MODE_PRIVATE);

            writer.write((item.getMessage() + "\n").getBytes());
            writer.write((item.getNumber() + "\n").getBytes());
            writer.write((item.getName() + "\n").getBytes());
            writer.write(item.getDate().getBytes());
            writer.close();
        } catch (IOException localIOException) {
            localIOException.printStackTrace();
        }
    }

    public long writeSMS(String number, ConversationEntry item, long pos, Context c) {
        long location = -1;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY))
            return location;

        try {
            RandomAccessFile f = new
                    RandomAccessFile(c.getFileStreamPath(number + ".dat"), "rw");

            if (pos == -1)
                f.seek(f.length()); //just append
            else
                f.seek(pos);

            f.write((item.getMessage() + "\n").getBytes());
            f.write((item.getName() + "\n").getBytes());
            location = f.getFilePointer();
            f.write((item.getDate()).getBytes());
            f.write("\r\n".getBytes());

            readPositions.put(number, f.length()); //update last effective read position
            //we use length because we might have gone back to update a missed message but we
            //dont want to read from that spot: we've already read past it or it's the end anyway.
            //we also have this line here because it's cheaper to do this than reopen the file
            //in getLastReadPosition

            f.close();
        } catch (IOException localIOException) {
            localIOException.printStackTrace();
        }

        return location;
    }

    void writeConf(String number, String time, long pos, Context c) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY))
            return;

        try {
            RandomAccessFile f = new
                    RandomAccessFile(c.getFileStreamPath(number + ".dat"), "rw");

            f.seek(pos);
            f.write(time.getBytes());
            f.close();
        } catch (IOException localIOException) {
            localIOException.printStackTrace();
        }
    }

    public void resetPointer(String file) {
        readPositions.put(file, 0L);
    }

    public long getLastReadPosition(String file) {
        Long temp = readPositions.get(file);
        if (temp != null)
            return temp;
        else
            return 0;
    }

    public void createPublicKeyExchangeIndicator(String address) throws IOException {
        FileOutputStream f = context.openFileOutput(address + ".exchange", Context.MODE_PRIVATE);
        f.close();
    }

    public void generateKeyRequestEntry(Date date, String address, String name, Contact.KeyStatus status, Context c) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY))
            return;

        try {
            RandomAccessFile f = new
                    RandomAccessFile(c.getFileStreamPath(KEY_EXCHANGE_REQUEST_FILE), "rw");

            f.seek(f.length()); //just append
            f.write((address + "\n").getBytes());
            f.write((status.toString() + "\n").getBytes());
            f.write((date.toString() + "\n").getBytes());
            f.write((name + "\n").getBytes());
            f.write("\r\n".getBytes());

            f.close();
        } catch (IOException localIOException) {
            localIOException.printStackTrace();
        }
    }

    public void updateKeyRequestEntry(String address, Contact.KeyStatus status, Context c) throws FileNotFoundException, IOException {
        Scanner s = new Scanner(c.getFileStreamPath(KEY_EXCHANGE_REQUEST_FILE));
        s.useDelimiter(address + "\n");
        long filePoint = s.next().getBytes().length; //surely terrible performance!! Will probably switch to SQLite

        RandomAccessFile f = new
                RandomAccessFile(c.getFileStreamPath(KEY_EXCHANGE_REQUEST_FILE), "rw");

        f.seek(filePoint);
        f.write(status.toString().getBytes());

        f.close();
    }
}